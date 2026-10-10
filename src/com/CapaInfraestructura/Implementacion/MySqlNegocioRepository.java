package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.INegocioRepository;
import com.CapaDominio.Entidades.ConfiguracionCatalogo;
import com.CapaDominio.Entidades.Negocio;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class MySqlNegocioRepository implements INegocioRepository {

    @Override
    public Optional<Negocio> buscarPorSubdominio(String subdominio) {
        String sql = "SELECT id, nombre, subdominio, email_contacto, activo, fecha_alta, fecha_baja FROM negocio WHERE subdominio = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, subdominio);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapNegocio(result));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar negocio por subdominio: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Negocio> buscarPorId(int id) {
        String sql = "SELECT id, nombre, subdominio, email_contacto, activo, fecha_alta, fecha_baja FROM negocio WHERE id = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapNegocio(result));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar negocio por id: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Map<ConfiguracionCatalogo, String> listarConfiguracionesEfectivas(int idNegocio) {
        String sql = "SELECT cc.id, cc.clave, cc.tipo, cc.descripcion, cc.valor_default, cc.categoria, " +
                     "COALESCE(cv.valor, cc.valor_default) AS valor_efectivo " +
                     "FROM configuracion_catalogo cc " +
                     "LEFT JOIN configuracion_valor cv ON cc.id = cv.id_configuracion AND cv.id_negocio = ?";
        
        Map<ConfiguracionCatalogo, String> resultado = new LinkedHashMap<>();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, idNegocio);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ConfiguracionCatalogo config = new ConfiguracionCatalogo(
                        rs.getInt("id"),
                        rs.getString("clave"),
                        rs.getString("tipo"),
                        rs.getString("descripcion"),
                        rs.getString("valor_default"),
                        rs.getString("categoria")
                    );
                    String valorEfectivo = rs.getString("valor_efectivo");
                    resultado.put(config, valorEfectivo);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar configuraciones efectivas: " + e.getMessage(), e);
        }
        return resultado;
    }

    @Override
    public Optional<ConfiguracionCatalogo> buscarConfiguracionPorClave(String clave) {
        String sql = "SELECT id, clave, tipo, descripcion, valor_default, categoria FROM configuracion_catalogo WHERE clave = ?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, clave);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    ConfiguracionCatalogo config = new ConfiguracionCatalogo(
                        result.getInt("id"),
                        result.getString("clave"),
                        result.getString("tipo"),
                        result.getString("descripcion"),
                        result.getString("valor_default"),
                        result.getString("categoria")
                    );
                    return Optional.of(config);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar configuración por clave: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public void guardarOActualizarValorConfiguracion(int idNegocio, int idConfiguracion, String valor) {
        String sql = "INSERT INTO configuracion_valor (id_negocio, id_configuracion, valor, fecha_actualizacion) " +
                     "VALUES (?, ?, ?, NOW()) " +
                     "ON DUPLICATE KEY UPDATE valor = VALUES(valor), fecha_actualizacion = NOW()";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, idNegocio);
            statement.setInt(2, idConfiguracion);
            statement.setString(3, valor);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar o actualizar valor de configuración: " + e.getMessage(), e);
        }
    }

    private Negocio mapNegocio(ResultSet rs) throws SQLException {
        Timestamp fechaAltaTs = rs.getTimestamp("fecha_alta");
        Timestamp fechaBajaTs = rs.getTimestamp("fecha_baja");
        LocalDateTime fechaAlta = fechaAltaTs != null ? fechaAltaTs.toLocalDateTime() : null;
        LocalDateTime fechaBaja = fechaBajaTs != null ? fechaBajaTs.toLocalDateTime() : null;

        return new Negocio(
            rs.getInt("id"),
            rs.getString("nombre"),
            rs.getString("subdominio"),
            rs.getString("email_contacto"),
            rs.getBoolean("activo"),
            fechaAlta,
            fechaBaja
        );
    }
}
