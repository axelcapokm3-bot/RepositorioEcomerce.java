package com.CapaAplicacion.Service;
import com.CapaAplicacion.Utilidades.OfuscadorDeContraseñas;
import com.CapaDominio.Entidades.Cuenta;
import com.CapaAplicacion.Interfaces.*;
import org.mindrot.jbcrypt.BCrypt;
public class RegistroDeCuenta {

    private final ICuentaRepositorio repoCuenta;
    private final String PEPPER = "EcommerseStrictPepper2026";

    public RegistroDeCuenta(ICuentaRepositorio repoCuenta){ 
        this.repoCuenta = repoCuenta; 
    }

    public void RegistrarContraseña(String nombre, String email, String passwordPlana, String rol, int idCliente) {
        if(passwordPlana == null || passwordPlana.length() < 6) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 6 caracteres."); 
        }



        String ContraseñaMod = OfuscadorDeContraseñas.ofuscadorEstricto(passwordPlana, PEPPER);
        String hashFinal = BCrypt.hashpw(ContraseñaMod, BCrypt.gensalt());

        Cuenta nuevaCuenta = new Cuenta(0, nombre, email, hashFinal, rol, idCliente);

        repoCuenta.guardar(nuevaCuenta);
    
}
}