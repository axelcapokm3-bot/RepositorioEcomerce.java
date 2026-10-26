import { showToast } from './utils/toast-notifier.js';

const API_BASE_URL = window.location.port === '5500'
    && ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname)
    ? 'http://localhost:8080/api'
    : '/api';

const CLAVE_SESION = 'ecommerce.sesion';
const CLAVE_NEGOCIO = 'ecommerce.negocio';
const HEADER_NEGOCIO = 'X-Negocio-Subdominio';

export const sesion = {
    obtener() {
        try {
            const crudo = sessionStorage.getItem(CLAVE_SESION);
            return crudo ? JSON.parse(crudo) : null;
        } catch {
            return null;
        }
    },
    guardar(datos) {
        sessionStorage.setItem(CLAVE_SESION, JSON.stringify(datos));
    },
    limpiar() {
        sessionStorage.removeItem(CLAVE_SESION);
    },
    token() {
        const actual = sesion.obtener();
        return actual && actual.token ? actual.token : null;
    }
};

function esPaginaLogin() {
    return window.location.pathname.endsWith('login.html');
}

export const negocio = {
    detectarPorHost() {
        try {
            const host = (window.location.hostname || '').trim().toLowerCase();
            const partes = host.split('.');
            if (partes.length < 2) {
                return null;
            }
            const primero = partes[0];
            if (!primero || primero === 'www') {
                return null;
            }
            return primero;
        } catch {
            return null;
        }
    },
    obtener() {
        try {
            return localStorage.getItem(CLAVE_NEGOCIO) || negocio.detectarPorHost();
        } catch {
            return negocio.detectarPorHost();
        }
    },
    fijar(subdominio) {
        const valor = (subdominio || '').trim().toLowerCase();
        if (!valor) {
            negocio.limpiar();
            return;
        }
        try {
            localStorage.setItem(CLAVE_NEGOCIO, valor);
        } catch {
            // almacenamiento no disponible: se sigue sin override
        }
    },
    limpiar() {
        try {
            localStorage.removeItem(CLAVE_NEGOCIO);
        } catch {
            // ignorar
        }
    }
};

function errorHttp(mensaje, status) {
    const error = new Error(mensaje);
    error.status = status;
    return error;
}

async function request(endpoint, options = {}, mostrarError = true) {
    try {
        const token = sesion.token();
        const subdominio = negocio.obtener();
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
                ...(subdominio ? { [HEADER_NEGOCIO]: subdominio } : {}),
                ...(options.headers || {})
            },
            body: options.body ? JSON.stringify(options.body) : undefined
        });

        const text = await response.text();
        let data = null;
        if (text) {
            try { data = JSON.parse(text); } catch { data = text; }
        }

        if (response.status === 401) {
            sesion.limpiar();
            if (!esPaginaLogin()) {
                window.location.href = 'login.html';
            }
            throw errorHttp('Su sesión expiró o no es válida. Ingrese nuevamente.', response.status);
        }
        if (response.status === 403) {
            throw errorHttp('No tiene permisos para realizar esta operación.', response.status);
        }
        if (!response.ok) {
            const errorMsg = data?.error || text || `Error HTTP ${response.status}`;
            throw errorHttp(errorMsg, response.status);
        }

        return data;
    } catch (error) {
        console.error(`API Error [${endpoint}]:`, error);
        if (mostrarError) {
            showToast(error.message, 'error');
        }
        throw error;
    }
}

export const api = {
    auth: {
        ingreso: (correoElectronico, contraseniaPlana) => request('/auth/ingreso', {
            method: 'POST',
            body: { correoElectronico, contraseniaPlana }
        }, false),
        registro: (payload) => request('/auth/registro', { method: 'POST', body: payload }),
        sesionActual: () => request('/auth/sesion'),
        negocios: () => request('/auth/negocios'),
        negocioActivo: async (subdominio) => {
            const datos = await request('/auth/negocio-activo', {
                method: 'POST',
                headers: { [HEADER_NEGOCIO]: subdominio }
            });
            sesion.guardar(datos);
            negocio.fijar(subdominio);
            return datos;
        },
        vincular: async (subdominio, datosCliente, contraseniaPlana) => {
            const datos = await request('/auth/vincular', {
                method: 'POST',
                headers: { [HEADER_NEGOCIO]: subdominio },
                body: { ...datosCliente, contraseniaPlana }
            });
            sesion.guardar(datos);
            negocio.fijar(subdominio);
            return datos;
        },
        salir: async () => {
            if (sesion.token()) {
                try {
                    await request('/auth/salida', { method: 'POST' }, false);
                } catch {
                    // La sesión local se cierra aunque el servidor no responda.
                }
            }
            sesion.limpiar();
        }
    },
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
    carrito: {
        obtener: () => request('/carrito', {}, false),
        agregar: (payload) => request('/carrito/items', { method: 'POST', body: payload }, false),
        eliminar: (idProducto) => request(`/carrito/items/${idProducto}`, { method: 'DELETE' }, false),
        vaciar: () => request('/carrito', { method: 'DELETE' }, false)
    },
    ventas: {
        crear: (payload, claveIdempotencia) => request('/ventas/crear', {
            method: 'POST',
            headers: { 'Idempotency-Key': claveIdempotencia },
            body: payload
        }, false),
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
