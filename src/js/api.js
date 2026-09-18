import { showToast } from './utils/toast-notifier.js';

const API_BASE_URL = 'http://localhost:8080/api';

async function request(endpoint, options = {}) {
    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...(options.headers || {})
            },
            body: options.body ? JSON.stringify(options.body) : undefined
        });

        const text = await response.text();
        let data = null;
        if (text) {
            try { data = JSON.parse(text); } catch { data = text; }
        }

        if (!response.ok) {
            const errorMsg = data?.error || text || `Error HTTP ${response.status}`;
            throw new Error(errorMsg);
        }

        return data;
    } catch (error) {
        console.error(`API Error [${endpoint}]:`, error);
        showToast(error.message, 'error');
        throw error;
    }
}

export const api = {
    productos: {
        listar: () => request('/productos'),
        obtener: (id) => request(`/productos/${id}`),
        crear: (payload) => request('/productos', { method: 'POST', body: payload }),
        actualizar: (id, payload) => request(`/productos/${id}`, { method: 'PUT', body: payload }),
        eliminar: (id) => request(`/productos/${id}`, { method: 'DELETE' })
    },
    clientes: {
        listar: () => request('/clientes'),
        obtener: (id) => request(`/clientes/${id}`),
        crear: (payload) => request('/clientes', { method: 'POST', body: payload }),
        actualizar: (id, payload) => request(`/clientes/${id}`, { method: 'PUT', body: payload }),
        eliminar: (id) => request(`/clientes/${id}`, { method: 'DELETE' })
    },
    ventas: {
        crear: (payload) => request('/ventas/crear', { method: 'POST', body: payload }),
        previsualizar: (payload) => request('/ventas/previsualizar', { method: 'POST', body: payload }),
        obtener: (id) => request(`/ventas/${id}`),
        pagar: (id) => request(`/ventas/${id}/pago`, { method: 'POST' }),
        cancelar: (id) => request(`/ventas/${id}/cancelar`, { method: 'POST' })
    },
    stock: {
        evaluar: (idProducto) => request('/stock/evaluar', { method: 'POST', body: { idProducto } }),
        obtener: (id) => request(`/stock/${id}`)
    },
    recomendaciones: {
        obtener: (payload) => request('/recomendaciones', { method: 'POST', body: payload })
    },
    auditoria: {
        listar: () => request('/auditoria'),
        crear: (payload) => request('/auditoria', { method: 'POST', body: payload }),
        porProducto: (id) => request(`/auditoria/producto/${id}`),
        porAccion: (accion) => request(`/auditoria/accion/${accion}`)
    }
};
