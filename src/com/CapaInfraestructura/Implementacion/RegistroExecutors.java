package com.CapaInfraestructura.Implementacion;

import com.CapaAplicacion.Interfaces.IServiceExecutor;
import com.CapaAplicacion.Interfaces.TipoServicio;

import java.util.EnumMap;
import java.util.Map;

public final class RegistroExecutors {

    private static final Map<TipoServicio, IServiceExecutor> EXECUTORS =
            new EnumMap<>(TipoServicio.class);

    private RegistroExecutors() {
    }

    public static synchronized IServiceExecutor obtener(TipoServicio tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de servicio no puede ser nulo.");
        }
        IServiceExecutor executor = EXECUTORS.get(tipo);
        if (executor == null) {
            executor = new ServiceExecutor(tipo);
            EXECUTORS.put(tipo, executor);
        }
        return executor;
    }

    public static synchronized void cerrarTodos() {
        for (IServiceExecutor executor : EXECUTORS.values()) {
            executor.shutdown();
        }
        EXECUTORS.clear();
    }
}
