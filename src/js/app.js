import { api } from './api.js';
import { showToast } from './utils/toast-notifier.js';

let carritoTemporal = [];

document.addEventListener('DOMContentLoaded', () => {
    const navLinks = document.querySelectorAll('.nav-link');
    const appView = document.getElementById('app-view');

    async function router() {
        const hash = window.location.hash.substring(1) || 'dashboard';
        
        navLinks.forEach(link => {
            if (link.getAttribute('data-view') === hash) {
                link.classList.add('bg-brand', 'text-white', 'shadow-glow-orange');
                link.classList.remove('text-slate-400');
            } else {
                link.classList.remove('bg-brand', 'text-white', 'shadow-glow-orange');
                link.classList.add('text-slate-400');
            }
        });

        appView.innerHTML = `<div class="flex items-center justify-center h-64 text-slate-400">
            <span class="material-symbols-outlined animate-spin text-[32px] text-brand mr-2">refresh</span> Conectando con Backend Java...
        </div>`;

        try {
            if (hash === 'dashboard') await renderDashboard(appView);
            else if (hash === 'clientes') await renderClientes(appView);
            else if (hash === 'catalogo') await renderCatalogo(appView);
            else if (hash === 'terminal') await renderTerminal(appView);
            else if (hash === 'auditoria') await renderAuditoria(appView);
            else await renderDashboard(appView);
        } catch (e) {
            appView.innerHTML = `<div class="p-6 bg-rose-950/40 border border-rose-500/40 rounded-xl text-rose-300">
                <h3 class="font-bold text-sm mb-1">Error de Conexión HTTP con el Backend Java</h3>
                <p class="text-xs">Detalle: ${e.message}</p>
                <p class="text-xs mt-2 text-slate-400">Verifica que el servidor esté corriendo ejecutando: <code class="bg-carbon-900 px-1.5 py-0.5 rounded text-brand">mvn exec:java -Dexec.mainClass="com.CapaExterna.ServerMain"</code></p>
            </div>`;
        }
    }

    window.addEventListener('hashchange', router);
    router();
});

async function renderDashboard(container) {
    const productos = await api.productos.listar();
    const clientes = await api.clientes.listar();
    const auditoria = await api.auditoria.listar();

    container.innerHTML = `
        <div class="flex flex-col gap-6">
            <div class="flex items-center justify-between">
                <div>
                    <h1 class="text-2xl font-bold text-white tracking-tight">Panel General</h1>
                    <p class="text-xs text-slate-400 mt-1">Conectado en tiempo real con SurCommerce Server (Java SE)</p>
                </div>
                <span class="px-3 py-1 bg-emerald-500/15 text-emerald-400 border border-emerald-500/35 rounded-lg text-xs font-semibold flex items-center gap-1.5">
                    <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span> Backend Activo (:8080)
                </span>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div class="bg-carbon-800 p-5 rounded-xl border border-carbon-700 shadow-card">
                    <span class="text-xs font-semibold text-slate-400 uppercase">Total Productos (Catálogo)</span>
                    <div class="text-3xl font-extrabold text-white font-mono mt-2">${productos.length}</div>
                </div>
                <div class="bg-carbon-800 p-5 rounded-xl border border-carbon-700 shadow-card">
                    <span class="text-xs font-semibold text-slate-400 uppercase">Clientes Registrados</span>
                    <div class="text-3xl font-extrabold text-white font-mono mt-2">${clientes.length}</div>
                </div>
                <div class="bg-carbon-800 p-5 rounded-xl border border-carbon-700 shadow-card">
                    <span class="text-xs font-semibold text-slate-400 uppercase">Registros de Auditoría</span>
                    <div class="text-3xl font-extrabold text-white font-mono mt-2">${auditoria.length}</div>
                </div>
            </div>
        </div>
    `;
}

async function renderClientes(container) {
    const clientes = await api.clientes.listar();
    container.innerHTML = `
        <div class="flex flex-col gap-6">
            <div class="flex justify-between items-center">
                <div>
                    <h1 class="text-2xl font-bold text-white tracking-tight">Gestión de Clientes</h1>
                    <p class="text-xs text-slate-400 mt-1">Sincronizado con <code class="text-brand">RepositorioCliente</code></p>
                </div>
                <button id="btn-nuevo-cliente" class="h-9 px-4 rounded-lg bg-brand hover:bg-brand-hover text-white font-semibold text-xs transition-all">+ Registrar Cliente</button>
            </div>
            
            <!-- Formulario de Alta Oculto/Desplegable -->
            <div id="form-cliente-container" class="hidden bg-carbon-800 p-5 rounded-xl border border-carbon-700">
                <h3 class="text-sm font-bold text-white mb-4">Nuevo Cliente Comercial</h3>
                <form id="clientForm" class="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                    <input type="text" id="dni" placeholder="DNI (7-8 dígitos)" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="text" id="cuit" placeholder="CUIT (11 dígitos, opcional)" class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="text" id="nombre" placeholder="Nombre" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="text" id="apellido" placeholder="Apellido" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="text" id="direccion" placeholder="Dirección" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="date" id="fechaNacimiento" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="text" id="localidad" placeholder="Localidad" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <div class="flex items-center gap-2">
                        <button type="submit" class="px-4 py-2 bg-brand text-white font-semibold rounded-lg">Guardar Cliente</button>
                        <button type="button" id="btn-cancelar-cliente" class="px-4 py-2 bg-carbon-700 text-slate-300 rounded-lg">Cancelar</button>
                    </div>
                </form>
            </div>

            <div class="bg-carbon-800 rounded-xl border border-carbon-700 overflow-hidden">
                <table class="w-full text-left text-xs">
                    <thead class="bg-carbon-900 text-slate-400 uppercase">
                        <tr>
                            <th class="py-3 px-6">Cliente</th>
                            <th class="py-3 px-4">DNI</th>
                            <th class="py-3 px-4">Localidad</th>
                            <th class="py-3 px-4">Nacimiento</th>
                            <th class="py-3 px-4 text-center">Acción</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-carbon-700">
                        ${clientes.length === 0 ? `<tr><td colspan="5" class="py-6 text-center text-slate-500">No hay clientes registrados.</td></tr>` : 
                        clientes.map(c => `
                            <tr class="hover:bg-carbon-750">
                                <td class="py-3 px-6 font-semibold text-white">${c.nombreCompleto}</td>
                                <td class="py-3 px-4 font-mono">${c.dni}</td>
                                <td class="py-3 px-4">${c.localidad}</td>
                                <td class="py-3 px-4 font-mono">${c.fechaNacimiento}</td>
                                <td class="py-3 px-4 text-center">
                                    <button onclick="window.eliminarCliente(${c.id})" class="text-rose-400 hover:text-rose-300 font-medium">Eliminar</button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        </div>
    `;

    document.getElementById('btn-nuevo-cliente')?.addEventListener('click', () => {
        document.getElementById('form-cliente-container').classList.toggle('hidden');
    });
    document.getElementById('btn-cancelar-cliente')?.addEventListener('click', () => {
        document.getElementById('form-cliente-container').classList.add('hidden');
    });

    document.getElementById('clientForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        try {
            await api.clientes.crear({
                dni: document.getElementById('dni').value,
                cuit: document.getElementById('cuit').value || '',
                nombre: document.getElementById('nombre').value,
                apellido: document.getElementById('apellido').value,
                direccion: document.getElementById('direccion').value,
                fechaNacimiento: document.getElementById('fechaNacimiento').value,
                localidad: document.getElementById('localidad').value
            });
            showToast('Cliente registrado con éxito en el backend', 'success');
            renderClientes(container);
        } catch (err) {
            showToast(err.message, 'error');
        }
    });
}

async function renderCatalogo(container) {
    const productos = await api.productos.listar();
    container.innerHTML = `
        <div class="flex flex-col gap-6">
            <div class="flex justify-between items-center">
                <h1 class="text-2xl font-bold text-white tracking-tight">Catálogo de Productos & Stock</h1>
                <button id="btn-nuevo-prod" class="h-9 px-4 rounded-lg bg-brand hover:bg-brand-hover text-white font-semibold text-xs transition-all">+ Nuevo Producto</button>
            </div>

            <!-- Formulario Nuevo Producto -->
            <div id="form-prod-container" class="hidden bg-carbon-800 p-5 rounded-xl border border-carbon-700">
                <h3 class="text-sm font-bold text-white mb-4">Registrar Producto en Inventario</h3>
                <form id="prodForm" class="grid grid-cols-1 md:grid-cols-4 gap-4 text-xs">
                    <input type="text" id="prod-nombre" placeholder="Nombre de Producto" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="number" step="0.01" id="prod-precio" placeholder="Precio (ARS)" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <input type="number" id="prod-stock" placeholder="Stock Físico" required class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                    <select id="prod-categoria" class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg">
                        <option value="ELECTRONICA">ELECTRONICA</option>
                        <option value="ROPA">ROPA</option>
                        <option value="HOGAR">HOGAR</option>
                        <option value="DEPORTES">DEPORTES</option>
                        <option value="LIBROS">LIBROS</option>
                    </select>
                    <div class="md:col-span-4 flex gap-2">
                        <button type="submit" class="px-4 py-2 bg-brand text-white font-semibold rounded-lg">Guardar Producto</button>
                        <button type="button" id="btn-cancelar-prod" class="px-4 py-2 bg-carbon-700 text-slate-300 rounded-lg">Cancelar</button>
                    </div>
                </form>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                ${productos.map(p => `
                    <div class="bg-carbon-800 p-4 rounded-xl border border-carbon-700 flex flex-col justify-between">
                        <div>
                            <span class="text-[10px] text-brand uppercase font-bold">${p.categoria}</span>
                            <h3 class="text-sm font-bold text-white mt-1">${p.nombre}</h3>
                            <div class="text-lg font-mono font-bold text-white mt-2">$ ${p.precio.toLocaleString('es-AR')} ARS</div>
                        </div>
                        <div class="mt-4 pt-3 border-t border-carbon-700 flex justify-between items-center text-xs">
                            <span class="text-slate-400">Stock: <strong class="text-white">${p.stockDisponible ?? p.stockFisico}</strong></span>
                            <div class="flex gap-2">
                                <button onclick="window.editarProducto(${p.id}, '${p.nombre.replace(/'/g, "")}', ${p.precio}, ${p.stockFisico}, '${p.categoria}')" class="text-brand hover:underline">Editar</button>
                                <button onclick="window.eliminarProducto(${p.id})" class="text-rose-400 hover:text-rose-300">Eliminar</button>
                            </div>
                        </div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;

    document.getElementById('btn-nuevo-prod')?.addEventListener('click', () => {
        document.getElementById('form-prod-container').classList.toggle('hidden');
    });
    document.getElementById('btn-cancelar-prod')?.addEventListener('click', () => {
        document.getElementById('form-prod-container').classList.add('hidden');
    });

    document.getElementById('prodForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('prod-id')?.value;
        const payload = {
            nombre: document.getElementById('prod-nombre').value,
            precio: parseFloat(document.getElementById('prod-precio').value),
            stock: parseInt(document.getElementById('prod-stock').value),
            categoria: document.getElementById('prod-categoria').value
        };

        try {
            if (id) {
                await api.productos.actualizar(id, payload);
                showToast('Producto actualizado con éxito (PUT)', 'success');
            } else {
                await api.productos.crear(payload);
                showToast('Producto creado con éxito (POST)', 'success');
            }
            renderCatalogo(container);
        } catch (err) {
            showToast(err.message, 'error');
        }
    });
}

async function renderTerminal(container) {
    const productos = await api.productos.listar();
    const clientes = await api.clientes.listar();
    
    container.innerHTML = `
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
            <div class="lg:col-span-8 flex flex-col gap-4">
                <div class="bg-carbon-800 p-4 rounded-xl border border-carbon-700 flex flex-col gap-3">
                    <div class="flex items-center justify-between">
                        <span class="text-xs font-semibold text-slate-300">Cliente Comprador:</span>
                        <select id="pos-cliente" class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg text-xs w-64">
                            ${clientes.map(c => `<option value="${c.id}">${c.nombreCompleto} (DNI: ${c.dni})</option>`).join('')}
                        </select>
                    </div>
                    <div class="flex items-center justify-between border-t border-carbon-700 pt-3">
                        <span class="text-xs font-semibold text-slate-300">Método de Pago:</span>
                        <select id="pos-tipopago" class="h-9 px-3 bg-carbon-900 border border-carbon-700 text-white rounded-lg text-xs w-64">
                            <option value="EFECTIVO">EFECTIVO</option>
                            <option value="TARJETA_CREDITO">TARJETA_CREDITO</option>
                        </select>
                    </div>
                    <div class="flex items-center justify-between border-t border-carbon-700 pt-3">
                        <span class="text-xs font-semibold text-slate-300">Descuento Aplicado:</span>
                        <div class="flex gap-2 w-64">
                            <select id="pos-desc-tipo" class="h-9 px-2 bg-carbon-900 border border-carbon-700 text-white rounded-lg text-xs flex-1">
                                <option value="ninguno">Sin Descuento</option>
                                <option value="PORCENTAJE">Porcentaje (%)</option>
                                <option value="MONTO">Monto Fijo ($)</option>
                                <option value="ESPECIAL">Especial</option>
                            </select>
                        </div>
                    </div>
                </div>
                <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
                    ${productos.map(p => {
                        const stockDisp = p.stockDisponible ?? (p.stockFisico - p.stockReservado);
                        const agotado = stockDisp <= 0;
                        return `
                        <div class="bg-carbon-800 p-3 rounded-xl border border-carbon-700 flex flex-col justify-between ${agotado ? 'opacity-50' : ''}">
                            <div>
                                <span class="text-[10px] text-brand uppercase font-bold">${p.categoria}</span>
                                <h4 class="text-xs font-bold text-white mt-0.5">${p.nombre}</h4>
                                <div class="text-sm font-mono font-bold text-brand mt-1">$ ${p.precio}</div>
                                <span class="text-[10px] ${agotado ? 'text-rose-400 font-bold' : 'text-slate-400'}">Stock Disp: ${stockDisp} u. (${p.estadoStock})</span>
                            </div>
                            <button ${agotado ? 'disabled' : ''} onclick="window.agregarAlCarrito(${p.id}, '${p.nombre.replace(/'/g, "")}', ${p.precio}, ${stockDisp})" class="mt-3 py-1.5 ${agotado ? 'bg-carbon-700 text-slate-500 cursor-not-allowed' : 'bg-brand/20 hover:bg-brand text-brand hover:text-white'} rounded text-xs font-semibold transition-all">
                                ${agotado ? 'Agotado' : 'Agregar'}
                            </button>
                        </div>
                    `;}).join('')}
                </div>
            </div>
            <div class="lg:col-span-4 bg-carbon-800 p-5 rounded-xl border border-carbon-700 flex flex-col justify-between">
                <div>
                    <h3 class="text-sm font-bold text-white mb-3">Ticket de Venta POS & Stock</h3>
                    <div id="cart-items" class="text-xs text-slate-400 mb-4 space-y-2 max-h-60 overflow-y-auto">
                        <p>No hay ítems en el carrito.</p>
                    </div>
                </div>
                <div class="space-y-3 pt-4 border-t border-carbon-700">
                    <div class="flex justify-between text-sm font-bold text-white">
                        <span>Total (con desc.):</span>
                        <span id="pos-total" class="font-mono text-brand">$ 0,00</span>
                    </div>
                    <div class="flex justify-between text-xs text-slate-400">
                        <span>Descuento calculado por backend:</span>
                        <span id="pos-descuento-backend" class="font-mono">$ 0,00</span>
                    </div>
                    <button onclick="window.procesarVenta()" class="w-full py-3 bg-brand hover:bg-brand-hover text-white font-bold rounded-lg text-xs shadow-glow-orange cursor-pointer">Finalizar Venta & Descontar Stock</button>
                </div>
            </div>
        </div>
    `;
}

async function renderAuditoria(container) {
    const logs = await api.auditoria.listar();
    container.innerHTML = `
        <div class="flex flex-col gap-6">
            <h1 class="text-2xl font-bold text-white tracking-tight">Registro de Auditoría & Trazabilidad</h1>
            <div class="bg-carbon-800 rounded-xl border border-carbon-700 p-4">
                <ul class="space-y-3">
                    ${logs.length === 0 ? `<li class="text-xs text-slate-500 text-center py-4">No hay registros de auditoría.</li>` :
                    logs.map(l => `
                        <li class="p-3 bg-carbon-900 rounded-lg border border-carbon-700 flex justify-between items-center text-xs">
                            <div>
                                <span class="font-mono font-bold text-brand">[${l.accion}]</span>
                                <span class="text-white ml-2">${l.descripcion}</span>
                            </div>
                            <span class="text-slate-500 font-mono">${l.fecha}</span>
                        </li>
                    `).join('')}
                </ul>
            </div>
        </div>
    `;
}

window.editarProducto = (id, nombre, precio, stock, categoria) => {
    const container = document.getElementById('form-prod-container');
    container.classList.remove('hidden');
    container.querySelector('h3').textContent = `Editar Producto #${id} (PUT)`;
    
    // Inyectar campo oculto ID y rellenar valores
    let idInput = document.getElementById('prod-id');
    if (!idInput) {
        idInput = document.createElement('input');
        idInput.type = 'hidden';
        idInput.id = 'prod-id';
        document.getElementById('prodForm').appendChild(idInput);
    }
    idInput.value = id;
    document.getElementById('prod-nombre').value = nombre;
    document.getElementById('prod-precio').value = precio;
    document.getElementById('prod-stock').value = stock;
    document.getElementById('prod-categoria').value = categoria;
};

window.eliminarProducto = async (id) => {
    if (!confirm('¿Estás seguro de eliminar este producto?')) return;
    try {
        await api.productos.eliminar(id);
        showToast('Producto eliminado correctamente', 'success');
        renderCatalogo(document.getElementById('app-view'));
    } catch (err) {
        showToast(err.message, 'error');
    }
};
window.eliminarCliente = async (id) => {
    if (!confirm('¿Estás seguro de eliminar este cliente?')) return;
    try {
        await api.clientes.eliminar(id);
        showToast('Cliente eliminado correctamente', 'success');
        renderClientes(document.getElementById('app-view'));
    } catch (err) {
        showToast(err.message, 'error');
    }
};
window.agregarAlCarrito = (id, nombre, precio, stockMax) => {
    const existente = carritoTemporal.find(i => i.idProducto === id);
    const cantidadActual = existente ? existente.cantidad : 0;
    
    if (cantidadActual + 1 > stockMax) {
        showToast(`Stock insuficiente. Disponible: ${stockMax}`, 'error');
        return;
    }

    if (existente) {
        existente.cantidad++;
    } else {
        carritoTemporal.push({ idProducto: id, cantidad: 1, nombre, precio, stockMax });
    }
    actualizarVistaCarrito();
    showToast(`Agregado: ${nombre}`, 'success');
};

window.cambiarCantidad = (id, delta) => {
    const item = carritoTemporal.find(i => i.idProducto === id);
    if (!item) return;

    const nuevaCantidad = item.cantidad + delta;
    if (nuevaCantidad <= 0) {
        carritoTemporal = carritoTemporal.filter(i => i.idProducto !== id);
    } else if (nuevaCantidad > item.stockMax) {
        showToast(`No puedes superar el stock disponible (${item.stockMax})`, 'error');
        return;
    } else {
        item.cantidad = nuevaCantidad;
    }
    actualizarVistaCarrito();
};

function actualizarVistaCarrito() {
    const container = document.getElementById('cart-items');
    const totalEl = document.getElementById('pos-total');
    const descuentoEl = document.getElementById('pos-descuento-backend');
    if (!container || !totalEl) return;

    if (carritoTemporal.length === 0) {
        container.innerHTML = `<p>No hay ítems en el carrito.</p>`;
        totalEl.textContent = '$ 0,00';
        if (descuentoEl) descuentoEl.textContent = '$ 0,00';
        return;
    }

    const descTipo = document.getElementById('pos-desc-tipo')?.value || 'ninguno';

    container.innerHTML = carritoTemporal.map(i => {
        const sub = i.precio * i.cantidad;
        return `<div class="flex justify-between items-center bg-carbon-900 p-2 rounded">
            <div class="flex flex-col">
                <span class="text-white font-semibold">${i.nombre}</span>
                <span class="text-[10px] text-slate-400">$ ${i.precio} c/u</span>
            </div>
            <div class="flex items-center gap-2">
                <button onclick="window.cambiarCantidad(${i.idProducto}, -1)" class="px-1.5 bg-carbon-700 text-white rounded">-</button>
                <span class="font-mono text-white">${i.cantidad}</span>
                <button onclick="window.cambiarCantidad(${i.idProducto}, 1)" class="px-1.5 bg-carbon-700 text-white rounded">+</button>
                <span class="font-mono text-white ml-2">$ ${sub.toLocaleString('es-AR')}</span>
            </div>
        </div>`;
    }).join('');

    totalEl.textContent = '$ ...';
    if (descuentoEl) descuentoEl.textContent = '$ ...';
    actualizarTotalDesdeBackend(descTipo);
}

document.addEventListener('change', (e) => {
    if (e.target.id === 'pos-desc-tipo' || e.target.id === 'pos-cliente') {
        actualizarVistaCarrito();
    }
});

let solicitudPreview = 0;
async function actualizarTotalDesdeBackend(descTipo) {
    const totalEl = document.getElementById('pos-total');
    const descuentoEl = document.getElementById('pos-descuento-backend');
    if (!totalEl || carritoTemporal.length === 0) return;

    const solicitudActual = ++solicitudPreview;
    try {
        const preview = await api.ventas.previsualizar({
            idCliente: parseInt(document.getElementById('pos-cliente')?.value || '1'),
            tipoPago: document.getElementById('pos-tipopago')?.value || 'EFECTIVO',
            descuento: descTipo === 'ninguno' ? null : { tipo: descTipo },
            items: carritoTemporal.map(i => ({ idProducto: i.idProducto, cantidad: i.cantidad }))
        });
        if (solicitudActual === solicitudPreview && totalEl.isConnected) {
            totalEl.textContent = `$ ${Number(preview.total).toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
            if (descuentoEl) {
                descuentoEl.textContent = `$ ${Number(preview.descuento).toLocaleString('es-AR', { minimumFractionDigits: 2 })}`;
            }
        }
    } catch (error) {
        if (solicitudActual === solicitudPreview && totalEl.isConnected) totalEl.textContent = '$ --';
        if (descuentoEl) descuentoEl.textContent = '$ --';
    }
}

window.procesarVenta = async () => {
    if (carritoTemporal.length === 0) {
        showToast('El carrito está vacío', 'error');
        return;
    }
    const clienteSelect = document.getElementById('pos-cliente');
    const tipoPagoSelect = document.getElementById('pos-tipopago');
    const descTipoSelect = document.getElementById('pos-desc-tipo');

    const idCliente = clienteSelect ? parseInt(clienteSelect.value) : 1;
    const tipoPago = tipoPagoSelect ? tipoPagoSelect.value : 'EFECTIVO';
    
    let descuentoObj = null;
    if (descTipoSelect && descTipoSelect.value !== 'ninguno') {
        descuentoObj = {
            tipo: descTipoSelect.value
        };
    }

    try {
        await api.ventas.crear({
            idCliente: idCliente,
            tipoPago: tipoPago,
            descuento: descuentoObj,
            items: carritoTemporal.map(i => ({ idProducto: i.idProducto, cantidad: i.cantidad }))
        });
        showToast('¡Venta registrada! Stock descontado correctamente en el backend.', 'success');
        carritoTemporal = [];
        actualizarVistaCarrito();
        // Recargar vista para reflejar stock actualizado
        const appView = document.getElementById('app-view');
        if (appView) renderTerminal(appView);
    } catch (e) {
        showToast(e.message, 'error');
    }
};
