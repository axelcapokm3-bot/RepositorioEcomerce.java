package com.CapaDominio.Entidades;

public enum EstadoVenta {
    PENDIENTE {
        @Override
        public boolean puedeTransicionarA(EstadoVenta nuevoEstado) {
          
            return nuevoEstado == PAGADO;
        }
    },
    PAGADO {
        @Override
        public boolean puedeTransicionarA(EstadoVenta nuevoEstado) {
         
            return nuevoEstado == ENVIADO || nuevoEstado == CANCELADO;
        }
    },
    ENVIADO {
        @Override
        public boolean puedeTransicionarA(EstadoVenta nuevoEstado) {
            return false; 
        }
    },
    CANCELADO {
        @Override
        public boolean puedeTransicionarA(EstadoVenta nuevoEstado) {
            return false; 
        }
    };

    public abstract boolean puedeTransicionarA(EstadoVenta nuevoEstado);
}