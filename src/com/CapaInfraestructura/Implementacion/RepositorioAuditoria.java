package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IAuditoriaRepository;
import com.CapaDominio.Entidades.AccionAuditoria;
import com.CapaDominio.Entidades.Auditoria;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositorio de auditoría en memoria (Implementación para tests y desarrollo sin DB).
 */
public class RepositorioAuditoria implements IAuditoriaRepository {
    
    private static final List<Auditoria> logs = new CopyOnWriteArrayList<>();
    private static final AtomicLong siguienteId = new AtomicLong(1);

    @Override
    public void guardar(Auditoria log) {
        if (log == null) {
            throw new IllegalArgumentException("El registro de auditoría no puede ser nulo.");
        }
        if (log.getId() == 0) {
            log.setId(siguienteId.getAndIncrement());
        }
        if (log.getFecha() == null) {
            log.setFecha(LocalDateTime.now());
        }
        logs.add(log);
    }

    @Override
    public List<Auditoria> listarTodas(long idNegocio) {
        exigirNegocio(idNegocio);
        List<Auditoria> resultado = new ArrayList<>();
        for (Auditoria log : logs) {
            if (coincideNegocio(log, idNegocio)) {
                resultado.add(log);
            }
        }
        return resultado;
    }

    @Override
    public List<Auditoria> buscarPorEntidad(long idNegocio, String entidad, int idEntidad) {
        exigirNegocio(idNegocio);
        List<Auditoria> resultado = new ArrayList<>();
        for (Auditoria log : logs) {
            boolean coincideEntidad = entidad == null || entidad.equalsIgnoreCase(log.getEntidad());
            if (coincideEntidad && log.getIdEntidad() == idEntidad
                    && coincideNegocio(log, idNegocio)) {
                resultado.add(log);
            }
        }
        return resultado;
    }

    @Override
    public List<Auditoria> buscarPorUsuario(long idNegocio, String usuario) {
        exigirNegocio(idNegocio);
        List<Auditoria> resultado = new ArrayList<>();
        for (Auditoria log : logs) {
            if (usuario != null && usuario.equalsIgnoreCase(log.getUsuario())
                    && coincideNegocio(log, idNegocio)) {
                resultado.add(log);
            }
        }
        return resultado;
    }

    @Override
    public List<Auditoria> buscarPorAccion(long idNegocio, AccionAuditoria accion) {
        exigirNegocio(idNegocio);
        List<Auditoria> resultado = new ArrayList<>();
        for (Auditoria log : logs) {
            if (log.getAccion() == accion && coincideNegocio(log, idNegocio)) {
                resultado.add(log);
            }
        }
        return resultado;
    }

    @Override
    public List<Auditoria> buscarPorRangoFecha(long idNegocio, LocalDateTime desde, LocalDateTime hasta) {
        exigirNegocio(idNegocio);
        List<Auditoria> resultado = new ArrayList<>();
        for (Auditoria log : logs) {
            LocalDateTime fecha = log.getFecha();
            if (fecha != null) {
                boolean des = desde == null || !fecha.isBefore(desde);
                boolean has = hasta == null || !fecha.isAfter(hasta);
                if (des && has && coincideNegocio(log, idNegocio)) {
                    resultado.add(log);
                }
            }
        }
        return resultado;
    }

    private static void exigirNegocio(long idNegocio) {
        if (idNegocio <= 0) {
            throw new IllegalArgumentException("El ID de negocio es obligatorio para consultar auditoría.");
        }
    }

    private static boolean coincideNegocio(Auditoria log, long idNegocio) {
        return log.getIdNegocio() == idNegocio;
    }
}
