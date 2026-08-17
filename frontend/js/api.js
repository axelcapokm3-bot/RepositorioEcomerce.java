const BASE_URL = "http://localhost:8080/api";

async function requestJson(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options);
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(
      payload?.message || payload?.error || text || "Error en la petición",
    );
  }

  return payload;
}

async function requestVoid(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Error en la petición");
  }
  return true;
}

export const api = {
  productos: {
    getAll: async () => {
      try {
        return await requestJson("/productos");
      } catch (error) {
        console.error("Error al listar productos:", error);
        return [];
      }
    },

    getById: async (id) => {
      try {
        return await requestJson(`/productos/${id}`);
      } catch (error) {
        console.error("Error al obtener producto:", error);
        return null;
      }
    },

    create: async (productoData) => {
      try {
        await requestVoid("/productos", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(productoData),
        });
        return true;
      } catch (error) {
        console.error("Error al crear producto:", error);
        alert(error.message);
        return false;
      }
    },

    update: async (id, productoData) => {
      try {
        await requestVoid(`/productos/${id}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(productoData),
        });
        return true;
      } catch (error) {
        console.error("Error al actualizar producto:", error);
        alert(error.message);
        return false;
      }
    },

    delete: async (id) => {
      try {
        await requestVoid(`/productos/${id}`, { method: "DELETE" });
        return true;
      } catch (error) {
        console.error("Error al eliminar producto:", error);
        alert(error.message);
        return false;
      }
    },
  },

  clientes: {
    getAll: async () => {
      try {
        return await requestJson("/clientes");
      } catch (error) {
        console.error("Error al listar clientes:", error);
        return [];
      }
    },

    getById: async (id) => {
      try {
        return await requestJson(`/clientes/${id}`);
      } catch (error) {
        console.error("Error al obtener cliente:", error);
        return null;
      }
    },

    create: async (clienteData) => {
      try {
        await requestVoid("/clientes", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(clienteData),
        });
        return true;
      } catch (error) {
        console.error("Error al crear cliente:", error);
        alert(error.message);
        return false;
      }
    },

    update: async (id, clienteData) => {
      try {
        await requestVoid(`/clientes/${id}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(clienteData),
        });
        return true;
      } catch (error) {
        console.error("Error al actualizar cliente:", error);
        alert(error.message);
        return false;
      }
    },

    delete: async (id) => {
      try {
        await requestVoid(`/clientes/${id}`, { method: "DELETE" });
        return true;
      } catch (error) {
        console.error("Error al eliminar cliente:", error);
        alert(error.message);
        return false;
      }
    },
  },

  cuentas: {
    getAll: async () => {
      try {
        return await requestJson("/cuentas");
      } catch (error) {
        console.error("Error al listar cuentas:", error);
        return [];
      }
    },

    getById: async (id) => {
      try {
        return await requestJson(`/cuentas/${id}`);
      } catch (error) {
        console.error("Error al obtener cuenta:", error);
        return null;
      }
    },

    create: async (cuentaData) => {
      try {
        await requestVoid("/cuentas", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(cuentaData),
        });
        return true;
      } catch (error) {
        console.error("Error al crear cuenta:", error);
        alert(error.message);
        return false;
      }
    },

    update: async (id, cuentaData) => {
      try {
        await requestVoid(`/cuentas/${id}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(cuentaData),
        });
        return true;
      } catch (error) {
        console.error("Error al actualizar cuenta:", error);
        alert(error.message);
        return false;
      }
    },

    delete: async (id) => {
      try {
        await requestVoid(`/cuentas/${id}`, { method: "DELETE" });
        return true;
      } catch (error) {
        console.error("Error al eliminar cuenta:", error);
        alert(error.message);
        return false;
      }
    },

    transferir: async (payload) => {
      try {
        await requestVoid("/cuentas/transferir", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        return true;
      } catch (error) {
        console.error("Error al transferir:", error);
        alert(error.message);
        return false;
      }
    },

    buscarPorCorreo: async (correo) => {
      try {
        return await requestJson(
          `/cuentas/buscarPorCorreo?correo=${encodeURIComponent(correo)}`,
        );
      } catch (error) {
        console.error("Error al buscar cuenta por correo:", error);
        return null;
      }
    },
  },

  ventas: {
    create: async (payload) => {
      try {
        await requestVoid("/ventas/crear", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        return true;
      } catch (error) {
        console.error("Error al crear venta:", error);
        alert(error.message);
        return false;
      }
    },

    getById: async (id) => {
      try {
        return await requestJson(`/ventas/${id}`);
      } catch (error) {
        console.error("Error al buscar venta:", error);
        return null;
      }
    },

    pagar: async (id) => {
      try {
        await requestVoid(`/ventas/${id}/pago`, { method: "POST" });
        return true;
      } catch (error) {
        console.error("Error al pagar venta:", error);
        alert(error.message);
        return false;
      }
    },

    cancelar: async (id) => {
      try {
        await requestVoid(`/ventas/${id}/cancelar`, { method: "POST" });
        return true;
      } catch (error) {
        console.error("Error al cancelar venta:", error);
        alert(error.message);
        return false;
      }
    },
  },

  auditoria: {
    getAll: async () => {
      try {
        return await requestJson("/auditoria");
      } catch (error) {
        console.error("Error al listar auditoría:", error);
        return [];
      }
    },

    create: async (payload) => {
      try {
        await requestVoid("/auditoria", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
        return true;
      } catch (error) {
        console.error("Error al registrar auditoría:", error);
        alert(error.message);
        return false;
      }
    },

    getByProducto: async (id) => {
      try {
        return await requestJson(`/auditoria/producto/${id}`);
      } catch (error) {
        console.error("Error al filtrar auditoría por producto:", error);
        return [];
      }
    },

    getByAccion: async (accion) => {
      try {
        return await requestJson(
          `/auditoria/accion/${encodeURIComponent(accion)}`,
        );
      } catch (error) {
        console.error("Error al filtrar auditoría por acción:", error);
        return [];
      }
    },
  },

  stock: {
    evaluar: async (idProducto) => {
      try {
        return await requestJson("/stock/evaluar", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ idProducto }),
        });
      } catch (error) {
        console.error("Error al evaluar stock:", error);
        return null;
      }
    },

    getById: async (idProducto) => {
      try {
        return await requestJson(`/stock/${idProducto}`);
      } catch (error) {
        console.error("Error al consultar stock:", error);
        return null;
      }
    },
  },

  recomendaciones: {
    getForProduct: async (payload) => {
      try {
        return await requestJson("/recomendaciones", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });
      } catch (error) {
        console.error("Error al obtener recomendaciones:", error);
        return [];
      }
    },
  },
};
