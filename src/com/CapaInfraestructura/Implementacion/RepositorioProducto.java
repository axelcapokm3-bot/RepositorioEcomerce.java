package com.CapaInfraestructura.Implementacion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.CapaAplicacion.Interfaces.IProductoRepository;
import com.CapaDominio.Entidades.Producto;

public class RepositorioProducto implements IProductoRepository {

private static final List<Producto> productos = new CopyOnWriteArrayList<>();
private static final AtomicInteger siguiente   = new AtomicInteger(MaximoId());

private static int MaximoId(){
    if (productos == null || productos.isEmpty()) {
        return 1;
    }
    int Maxid = 0 ; 

    for(Producto producto : productos){
            if(producto.getId() > Maxid){
                Maxid = producto.getId();
            }


    }

return Maxid + 1   ;
}





         @Override
         public void guardar(Producto producto) {
                if(producto == null) {
                    throw new IllegalArgumentException("El producto no puede ser nulo.");
                }

                if(producto.getId() == 0 ){
                        producto.setId(siguiente.getAndIncrement());
                }
productos.add(producto);

         }


        @Override
        public Optional<Producto> buscarPorId(int id) {
            if (id <= 0) {
                throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
            }

                for (Producto P : productos) {
                    if (P.getId() == id ) {
                        return Optional.of(P); 
                    }
                }
                return Optional.empty();
        }

@Override
         
         public List<Producto> listarTodos() {
    
    return new ArrayList<>(productos);
}
@Override
          
          public void actualizar(int id , Producto producto){
            if (producto == null ) {
                throw new IllegalArgumentException("El producto no puede ser nulo.");
            }

            for(int i =  0 ; i < productos.size() ; i ++ )  {
                


                if(productos.get(i).getId() == id) {
                    productos.set(i, producto);
                    return;
                }
            }


          }
@Override
          

          public void eliminar(int id){

            if (id <= 0) {
                throw new IllegalArgumentException("El ID debe ser mayor a cero. ID recibido: " + id);
            }

            for(int i = 0 ; i < productos.size() ; i ++ )  {
                
                if(productos.get(i).getId() == id) {
                    productos.remove(i);
                    return;
                }
            }       
          }
 
        }


