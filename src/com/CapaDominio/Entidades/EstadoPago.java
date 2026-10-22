package com.CapaDominio.Entidades;

public enum EstadoPago {
    PENDIENTE {
        @Override
        public boolean puedeTransicionarA(EstadoPago nuevoEstado) {
            return nuevoEstado == APROBADO
                    || nuevoEstado == RECHAZADO
                    || nuevoEstado == CANCELADO;
        }
    },
    APROBADO {
        @Override
        public boolean puedeTransicionarA(EstadoPago nuevoEstado) {
            return false;
        }
    },
    RECHAZADO {
        @Override
        public boolean puedeTransicionarA(EstadoPago nuevoEstado) {
            return false;
        }
    },
    CANCELADO {
        @Override
        public boolean puedeTransicionarA(EstadoPago nuevoEstado) {
            return false;
        }
    };

    public abstract boolean puedeTransicionarA(EstadoPago nuevoEstado);
}
