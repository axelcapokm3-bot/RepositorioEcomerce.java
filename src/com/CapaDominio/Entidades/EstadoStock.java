package com.CapaDominio.Entidades;

 public enum EstadoStock {
    DISPONIBLE(0),
    CRITICO(50),  
    AGOTADO(100),      
    EN_REPOSICION(0)  ;
    
    private final int cantidadReponer ; 

EstadoStock(int cantidadAReponer) {
        this.cantidadReponer = cantidadAReponer;
    }

    public int getCantidadAReponer() {
        return cantidadReponer;
    }

    public static EstadoStock evaluarEstado(int stockFisico )  
    {
      if (stockFisico == 0) {
            return AGOTADO;
        }
        if (stockFisico >= 1 && stockFisico <= 10) {
            return CRITICO;
        }
        return DISPONIBLE;  
    }


} 
    
