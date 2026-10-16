package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IProveedorTokenAcceso;
import com.CapaAplicacion.Seguridad.Principal;
import com.CapaDominio.Entidades.RolUsuario;
import com.CapaInfraestructura.Configuracion.ConfigLoader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ProveedorTokenAcceso implements IProveedorTokenAcceso {
    private static final Logger LOGGER = Logger.getLogger(ProveedorTokenAcceso.class.getName());
    private static final long SEGUNDOS_VIDA_PREDETERMINADO = 12L * 60L * 60L;

    private final byte[] secreto;
    private final long segundosDeVida;

    public ProveedorTokenAcceso() {
        this(resolverSecreto(), resolverVida());
    }

    ProveedorTokenAcceso(byte[] secreto, long segundosDeVida) {
        if (secreto == null || secreto.length < 32) {
            throw new IllegalArgumentException("El secreto del token debe tener al menos 32 bytes.");
        }
        if (segundosDeVida <= 0) {
            throw new IllegalArgumentException("La vida del token debe ser positiva.");
        }
        this.secreto = secreto.clone();
        this.segundosDeVida = segundosDeVida;
    }

    @Override
    public String emitir(int idCuenta, int idCliente, long idNegocio, String rol) {
        if (idCuenta <= 0 || idCliente <= 0 || idNegocio <= 0) {
            throw new IllegalArgumentException("Los identificadores del token no son válidos.");
        }
        RolUsuario rolValidado = RolUsuario.desdeTexto(rol);
        long expiracion = ahoraEpoch() + segundosDeVida;
        String contenido = idCuenta + ":" + idCliente + ":" + idNegocio + ":" + rolValidado.name() + ":" + expiracion;
        String firma = firmar(contenido);
        return base64Url(contenido.getBytes(StandardCharsets.UTF_8)) + "." + firma;
    }

    @Override
    public Optional<Principal> validar(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        int punto = token.indexOf('.');
        if (punto <= 0 || punto != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        String parteContenido = token.substring(0, punto);
        String parteFirma = token.substring(punto + 1);
        byte[] contenidoBytes;
        try {
            contenidoBytes = Base64.getUrlDecoder().decode(parteContenido);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String contenido = new String(contenidoBytes, StandardCharsets.UTF_8);
        String firmaEsperada = firmar(contenido);
        if (!compararConstante(firmaEsperada, parteFirma)) {
            return Optional.empty();
        }
        String[] campos = contenido.split(":", -1);
        if (campos.length != 5) {
            return Optional.empty();
        }
        try {
            int idCuenta = Integer.parseInt(campos[0]);
            int idCliente = Integer.parseInt(campos[1]);
            long idNegocio = Long.parseLong(campos[2]);
            RolUsuario rol = RolUsuario.desdeTexto(campos[3]);
            long expiracion = Long.parseLong(campos[4]);
            if (idCuenta <= 0 || idCliente <= 0 || idNegocio <= 0 || expiracion <= ahoraEpoch()) {
                return Optional.empty();
            }
            return Optional.of(new Principal(idCuenta, idCliente, idNegocio, rol));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public long segundosDeVida() {
        return segundosDeVida;
    }

    private String firmar(String contenido) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            byte[] firma = hmac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
            return base64Url(firma);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de acceso.", e);
        }
    }

    private static boolean compararConstante(String esperado, String recibido) {
        byte[] a = esperado.getBytes(StandardCharsets.UTF_8);
        byte[] b = recibido.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String base64Url(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }

    private static long ahoraEpoch() {
        return java.time.Instant.now().getEpochSecond();
    }

    private static byte[] resolverSecreto() {
        String configurado = ConfigLoader.getProperty("auth.token-secret");
        if (configurado != null && !configurado.trim().isEmpty()
                && configurado.trim().length() >= 32) {
            return configurado.trim().getBytes(StandardCharsets.UTF_8);
        }
        if (esPerfilProduccion()) {
            throw new IllegalStateException(
                    "AUTH_TOKEN_SECRET es obligatorio en producción y debe tener al menos 32 caracteres. "
                            + "El servidor no inicia sin un secreto configurado.");
        }
        LOGGER.warning("AUTH_TOKEN_SECRET no configurado. Perfil de desarrollo: "
                + "se genera un secreto efímero SOLO para uso local. Los tokens se invalidan al reiniciar. "
                + "No usar este modo en producción.");
        byte[] efimero = new byte[32];
        new java.security.SecureRandom().nextBytes(efimero);
        return efimero;
    }

    static boolean esPerfilProduccion() {
        String perfil = ConfigLoader.getProperty("app.perfil");
        return perfil != null && "prod".equalsIgnoreCase(perfil.trim());
    }

    private static long resolverVida() {
        String configurado = ConfigLoader.getProperty("auth.expiracion-horas");
        if (configurado != null && !configurado.trim().isEmpty()) {
            try {
                long horas = Long.parseLong(configurado.trim());
                if (horas >= 1 && horas <= 72) {
                    return horas * 60L * 60L;
                }
            } catch (NumberFormatException e) {
                LOGGER.warning("auth.expiracion-horas inválido; se usa el valor predeterminado de 12 horas.");
            }
        }
        return SEGUNDOS_VIDA_PREDETERMINADO;
    }
}
