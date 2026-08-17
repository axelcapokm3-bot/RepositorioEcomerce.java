package com.CapaAplicacion.Utilidades;

public class OfuscadorDeContraseñas {
    
    public static String ofuscadorEstricto(String contraseña, String extra) { 
        // 1. Evitar NullPointerException en los parámetros
        if (contraseña == null) {
            contraseña = "";
        }
        
        char[] contraseñaOriginal = contraseña.toCharArray();
        char[] extraChars = (extra != null) ? extra.toCharArray() : new char[0]; 

        int largoOriginal = contraseñaOriginal.length;
        int mitad = largoOriginal / 2;

        // Reservamos el espacio exacto en memoria
        char[] resultado = new char[largoOriginal + extraChars.length];

        int destino = 0; 
        int espejo = largoOriginal - 1; 

        for (int i = 0; i < largoOriginal; i++) { 
            // Al llegar a la mitad, insertamos el "Extra"
            if (i == mitad) {
                for (int k = 0; k < extraChars.length; k++) {
                    resultado[destino] = extraChars[k]; 
                    destino++; 
                }
            }

            // Insertamos el caracter de la contraseña de forma invertida
            resultado[destino] = contraseñaOriginal[espejo];
            destino++; 
            espejo--;
        }
        
        return new String(resultado);
    }
}