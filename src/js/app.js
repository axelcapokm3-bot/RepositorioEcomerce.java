import { api, sesion } from './api.js';
import { addToCart, toggleCart } from './components/cart-sidebar.js';
import { renderSalesList } from './components/sales-list.js';
import { showToast } from './utils/toast-notifier.js';
import { showSpinner, hideSpinner } from './utils/spinner.js';

let carritoTemporal = [];
let procesandoVenta = false;
let catalogoCache = [];
let filtroCategoriaActual = 'TODOS';
let busquedaFiltroActual = '';

const CLAVE_SOLICITUD_CHECKOUT = 'ecommerce.checkout.pending-request';

function obtenerClaveIdempotenciaCheckout(payload) {
    const payloadSerializado = JSON.stringify(payload);
    const solicitudGuardada = sessionStorage.getItem(CLAVE_SOLICITUD_CHECKOUT);

    if (solicitudGuardada) {
        try {
            const solicitud = JSON.parse(solicitudGuardada);
            if (solicitud.payload === payloadSerializado && solicitud.clave) {
                return solicitud.clave;
            }
        } catch {
            sessionStorage.removeItem(CLAVE_SOLICITUD_CHECKOUT);
        }
    }

    const clave = (typeof crypto !== 'undefined' && crypto.randomUUID) 
        ? crypto.randomUUID() 
        : 'idemp-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);

    sessionStorage.setItem(CLAVE_SOLICITUD_CHECKOUT, JSON.stringify({
        payload: payloadSerializado,
        clave
    }));
    return clave;
}

document.addEventListener('DOMContentLoaded', () => {
    mostrarResultadoCheckout();
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async (event) => {
            event.preventDefault();
            await api.auth.salir();
            window.location.href = 'login.html';
        });
    }
    const navLinks = document.querySelectorAll('.nav-link, .mobile-nav-link');
    const appView = document.getElementById('app-view');
    const globalSearchInput = document.getElementById('global-search');
    const rolSesion = String(sesion.obtener()?.rol || '').toUpperCase();
    const esAdmin = rolSesion === 'ADMIN';
    const vistasAdministrativas = ['dashboard', 'terminal', 'clientes', 'auditoria'];

    if (!esAdmin) {
        navLinks.forEach((link) => {
            if (vistasAdministrativas.includes(link.getAttribute('data-view'))) {
                link.style.display = 'none';
            }
        });
        const accesoRapidoPos = document.querySelector('header a[href="#terminal"]');
        if (accesoRapidoPos) {
            accesoRapidoPos.style.display = 'none';
        }
    }

    async function router() {
        const hash = window.location.hash.substring(1) || (esAdmin ? 'dashboard' : 'catalogo');

        if (!esAdmin && vistasAdministrativas.includes(hash)) {
            window.location.hash = '#catalogo';
            return;
        }
        
        // Update active navigation state
        navLinks.forEach(link => {
            const linkView = link.getAttribute('data-view');
            const isMatch = linkView === hash;
            
            if (link.classList.contains('nav-link')) {
                if (isMatch) {
                    link.classList.add('bg-brand', 'text-white', 'shadow-glow-orange');
                    link.classList.remove('text-slate-400', 'hover:bg-carbon-800');
                    const icon = link.querySelector('.material-symbols-outlined');
                    if (icon) icon.classList.remove('group-hover:text-brand');
                } else {
                    link.classList.remove('bg-brand', 'text-white', 'shadow-glow-orange');
                    link.classList.add('text-slate-400', 'hover:bg-carbon-800');
                }
            } else if (link.classList.contains('mobile-nav-link')) {
                if (isMatch) {
                    link.classList.add('text-brand', 'font-bold');
                    link.classList.remove('text-slate-400');
                } else {
                    link.classList.remove('text-brand', 'font-bold');
                    link.classList.add('text-slate-400');
                }
            }
        });

        // Skeleton / Spinner inicial
        appView.innerHTML = `
            <div class="flex flex-col items-center justify-center h-80 text-slate-400 gap-3">
                <div class="w-10 h-10 border-3 border-brand border-t-transparent rounded-full animate-spin"></div>
                <div class="text-xs font-medium tracking-wide text-slate-400">Sincronizando con SurCommerce Server (Java SE)...</div>
            </div>
        `;

        try {
            if (hash === 'dashboard') await renderDashboard(appView);
            else if (hash === 'clientes') await renderClientes(appView);
            else if (hash === 'catalogo') await renderCatalogo(appView);
            else if (hash === 'terminal') await renderTerminal(appView);
            else if (hash === 'auditoria') await renderAuditoria(appView);
            else await (esAdmin ? renderDashboard(appView) : renderCatalogo(appView));
        } catch (e) {
            console.error('Error de enrutamiento:', e);
            const esPermiso = e.status === 403;
            const esSesion = e.status === 401;
            const esHttp = typeof e.status === 'number';
            const titulo = esPermiso
                ? 'Permisos Insuficientes'
                : esSesion
                    ? 'Sesión Expirada'
                    : esHttp
                        ? 'No se pudo cargar la información'
                        : 'No se pudo conectar con el servidor';
            const detalle = esPermiso
                ? 'Tu cuenta no tiene permisos para acceder a este módulo. La autorización final la define el servidor.'
                : esSesion
                    ? 'Tu sesión expiró o no es válida. Volvé a iniciar sesión.'
                    : esHttp
                        ? `El servidor respondió con HTTP ${e.status}. No se pudo completar la carga.`
                        : 'No se pudo conectar con el backend Java en el puerto local 8080. Verificá tu conexión y que el servidor esté activo.';
            const icono = esPermiso
                ? 'lock'
                : esSesion
                    ? 'passkey'
                    : esHttp
                        ? 'error'
                        : 'signal_wifi_connected_no_internet_4';
            const ayudaConexion = esHttp ? '' : `
                            <div class="mt-4 pt-3 border-t border-carbon-700/20 text-xs text-slate-400">
                                <span>Verificá que el servidor esté activo:</span>
                                <pre class="mt-2 p-3 bg-carbon-900 rounded-xl text-[11px] font-mono text-orange-400 overflow-x-auto border border-carbon-700">mvn exec:java -Dexec.mainClass="com.CapaExterna.ServerMain"</pre>
                            </div>`;
            appView.innerHTML = `
                <div class="p-6 md:p-8 bg-carbon-900/50 border border-carbon-700/30 rounded-2xl text-white shadow-xl max-w-2xl mx-auto my-12">
                    <div class="flex items-start gap-4">
                        <span class="material-symbols-outlined text-[32px] text-orange-400 shrink-0">${icono}</span>
                        <div class="flex flex-col gap-2">
                            <h3 class="font-bold text-base text-white tracking-tight">${titulo}</h3>
                            <p class="text-xs text-slate-400/90 leading-relaxed">${detalle} Detalle técnico: <code class="bg-carbon-900 px-2 py-0.5 rounded text-orange-400 font-mono text-sm">${escaparHtml(e.message || 'Error desconocido')}</code></p>
                            ${ayudaConexion}
                            <button onclick="window.location.reload()" class="mt-4 px-4 py-2 bg-brand hover:bg-brand-hover text-white rounded-xl text-xs font-semibold w-fit transition-all flex items-center gap-2 cursor-pointer shadow-md">
                                <span class="material-symbols-outlined text-[16px]">refresh</span>
                                Reintentar
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    if (globalSearchInput) {
        globalSearchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase().trim();
            const hash = window.location.hash.substring(1) || 'dashboard';

            if (hash === 'catalogo') {
                const tarjetas = document.querySelectorAll('.catalogo-product-card');
                tarjetas.forEach(card => {
                    const nombre = (card.getAttribute('data-nombre') || '').toLowerCase();
                    const cat = (card.getAttribute('data-categoria') || '').toLowerCase();
                    const coincide = nombre.includes(query) || cat.includes(query);
                    card.style.display = coincide ? '' : 'none';
                });
            } else if (hash === 'terminal') {
                const tarjetas = document.querySelectorAll('.pos-product-item');
                tarjetas.forEach(card => {
                    const nombre = (card.getAttribute('data-nombre') || '').toLowerCase();
                    const coincide = nombre.includes(query);
                    card.style.display = coincide ? '' : 'none';
                });
            } else if (hash === 'clientes') {
                const filas = document.querySelectorAll('.cliente-row');
                filas.forEach(row => {
                    const texto = (row.textContent || '').toLowerCase();
                    row.style.display = texto.includes(query) ? '' : 'none';
                });
            }
        });
    }

    window.addEventListener('hashchange', router);
    router();
});

function mostrarResultadoCheckout() {
    const url = new URL(window.location.href);
    const estado = url.searchParams.get('payment_status') || url.searchParams.get('resultado');
    const sessionId = url.searchParams.get('session_id');

    const mensajes = {
        success: {
            texto: 'Mercado Pago / Stripe confirmó el pago con éxito. El estado final se concilia mediante Webhook.',
            tipo: 'success'
        },
        failure: {
            texto: 'La pasarela informó que el pago no fue aprobado. Puedes revisar los fondos o probar otro medio.',
            tipo: 'error'
        },
        pending: {
            texto: 'Pago en proceso de acreditación. Se confirmará automáticamente al registrarse en el backend.',
            tipo: 'info'
        }
    };

    if (estado && mensajes[estado]) {
        const resultado = mensajes[estado];
        showToast(resultado.texto, resultado.tipo);
        url.searchParams.delete('payment_status');
        url.searchParams.delete('resultado');
        if (sessionId) url.searchParams.delete('session_id');
        window.history.replaceState(null, '', `${url.pathname}${url.search}${url.hash}`);
    }
}

async function fetchPaginated(apiFn, page = 1) {
    const response = await apiFn(page);
    if (Array.isArray(response)) {
        return { datos: response, totalPaginas: 1, paginaActual: 1 };
    }
    return response;
}

// ---------------------------------------------------------------------
// 1. DASHBOARD GENERAL CON D3.JS VISUALIZATIONS
// ---------------------------------------------------------------------
async function renderDashboard(container) {
    const [resProductos, resClientes, resAuditoria] = await Promise.all([
        fetchPaginated(api.productos.listar),
        fetchPaginated(api.clientes.listar),
        fetchPaginated(api.auditoria.listar)
    ]);
    const productos = resProductos.datos;
    const clientes = resClientes.datos;
    const auditoria = resAuditoria.datos;

    const totalStock = productos.reduce((sum, p) => sum + (p.stockFisico || 0), 0);
    const productosCriticos = productos.filter(p => (p.stockDisponible ?? p.stockFisico) <= 10 && (p.stockDisponible ?? p.stockFisico) > 0);
    const productosAgotados = productos.filter(p => (p.stockDisponible ?? p.stockFisico) <= 0);

    // Render KPI Section first
    container.innerHTML = `
        <div id="kpi-section" class="mb-8 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 animate-fadeIn">
            <!-- KPI cards will be populated below -->
        </div>
    `;

    // Render KPIs
    renderKPIs(container, productos, clientes, auditoria);

    // Render main dashboard content
    container.innerHTML += `
        <div class="flex flex-col gap-8 animate-fadeIn">
            <!-- Header con métricas de salud -->
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-carbon-700/80">
                <div>
                    <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-carbon-800/50 border border-carbon-700/30 text-[11px] text-slate-300 font-medium mb-2">
                        <span class="w-2 h-2 rounded-full bg-emerald-400 pulse-glow"></span>
                        SurCommerce Node AR-Central • Conexión Activa
                    </div>
                    <h1 class="text-3xl font-bold text-white tracking-tight">Panel de Control Operativo</h1>
                    <p class="text-xs text-slate-400 mt-1 max-w-xl">Supervisión en tiempo real de inventario, terminal de cobros AFIP y trazabilidad con base de datos transaccional.</p>
                </div>
                <div class="flex items-center gap-3">
                    <a href="#terminal" class="px-4 py-2.5 bg-brand hover:bg-brand-hover text-white rounded-xl text-xs font-bold transition-all shadow-glow-orange flex items-center gap-2 cursor-pointer">
                        <span class="material-symbols-outlined text-[18px]">point_of_sale</span>
                        <span>Abrir Terminal POS</span>
                    </a>
                </div>
            </div>

            <!-- Grilla de Estadísticas Principales y KPIs -->
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <!-- KPI cards injected by renderKPIs -->
            </div>

            <!-- Accesos y Pasarelas -->
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <!-- Pasarelas Integradas -->
                <div class="bg-carbon-900/80 p-6 rounded-2xl border border-carbon-700 shadow-card flex flex-col justify-between">
                    <div>
                        <div class="flex items-center justify-between mb-4">
                            <h3 class="text-sm font-bold text-white tracking-tight">Pasarelas de Pago Oficiales</h3>
                            <span class="text-[10px] text-emerald-400 font-bold bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">LIVE</span>
                        </div>
                        <p class="text-xs text-slate-400 mb-5 leading-relaxed">Transacciones seguras tokenizadas mediante pasarelas externas con retorno directo y webhook idempotente.</p>
                        
                        <div class="space-y-3">
                            <div class="flex items-center justify-between p-3.5 rounded-xl bg-carbon-800/80 border border-carbon-700">
                                <div class="flex items-center gap-3">
                                    <div class="w-9 h-9 rounded-lg bg-sky-500/20 text-sky-400 flex items-center justify-center font-bold text-xs">MP</div>
                                    <div class="flex flex-col">
                                        <span class="text-xs font-bold text-white">Mercado Pago</span>
                                        <span class="text-[10px] text-slate-400">Orders API • Modo Manual Checkout</span>
                                    </div>
                                </div>
                                <span class="material-symbols-outlined text-emerald-400 text-[18px]">check_circle</span>
                            </div>

                            <div class="flex items-center justify-between p-3.5 rounded-xl bg-carbon-800/80 border border-carbon-700">
                                <div class="flex items-center gap-3">
                                    <div class="w-9 h-9 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-xs">ST</div>
                                    <div class="flex flex-col">
                                        <span class="text-xs font-bold text-white">Stripe</span>
                                        <span class="text-[10px] text-slate-400">Checkout Session • Webhooks Activos</span>
                                    </div>
                                </div>
                                <span class="material-symbols-outlined text-emerald-400 text-[18px]">check_circle</span>
                            </div>
                        </div>
                    </div>

                    <div class="mt-6 pt-4 border-t border-carbon-700/80 flex items-center justify-between text-[11px] text-slate-400">
                        <span>Clave Idempotencia:</span>
                        <span class="font-mono text-brand font-semibold">uuid()</span>
                    </div>
                </div>

                <!-- Última actividad de Auditoría -->
                <div class="lg:col-span-2 bg-carbon-900/80 p-6 rounded-2xl border border-carbon-700 shadow-card flex flex-col justify-between">
                    <div class="flex items-center justify-between mb-4">
                        <div class="flex items-center gap-2">
                            <span class="material-symbols-outlined text-brand text-[20px]">history</span>
                            <h3 class="text-sm font-bold text-white tracking-tight">Actividad Transaccional Reciente</h3>
                        </div>
                        <a href="#auditoria" class="text-xs text-orange-400 hover:text-orange-300 font-medium">Ver todo &rarr;</a>
                    </div>
                    
                    <div class="space-y-2.5 overflow-hidden" id="auditoria-list">
                        ${auditoria.length === 0 ? `
                            <div class="py-12 text-center text-slate-500 text-xs">No hay eventos transaccionales registrados aún en el EventBus.</div>
                        ` : auditoria.slice(0, 5).map(log => `
                            <div class="p-3 bg-carbon-800/80 rounded-xl border border-carbon-700 flex items-center justify-between text-xs gap-3">
                                <div class="flex items-center gap-2.5 min-w-0">
                                    <span class="px-2 py-0.5 rounded text-[10px] font-mono font-bold shrink-0 ${
                                        log.accion === 'COMPRA' ? 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30' :
                                        log.accion === 'CREACION' ? 'bg-sky-500/15 text-sky-400 border border-sky-500/30' :
                                        log.accion === 'ELIMINACION' ? 'bg-rose-500/15 text-rose-400 border border-rose-500/30' :
                                        'bg-orange-500/15 text-orange-400 border border-orange-500/30'
                                    }">${escaparHtml(log.accion)}</span>
                                    <span class="text-slate-200 truncate font-medium">${escaparHtml(log.descripcion)}</span>
                                </div>
                                <span class="text-[11px] text-slate-500 font-mono shrink-0">${escaparHtml(log.fecha || 'Reciente')}</span>
                            </div>
                        `).join('')}
                    </div>

                    <div class="mt-6 pt-4 border-t border-carbon-700/80 flex items-center justify-between text-xs text-slate-400">
                        <span>Repositorio: <code class="text-slate-300 font-mono">MySqlRepositorioAuditoria</code></span>
                        <a href="#catalogo" class="text-brand hover:underline font-semibold">Gestionar Stock &rarr;</a>
                    </div>
                </div>
            </div>

            <div id="d3-visualizations" class="grid grid-cols-1 xl:grid-cols-2 gap-6"></div>
        </div>
    `;

    // Now render D3.js visualizations
    await renderD3Visualizations(productos, auditoria);
}

// ---------------------------------------------------------------------
// 2. KPI CARDS WITH TREND INDICATORS
// ---------------------------------------------------------------------
function renderKPIs(container, productos, clientes, auditoria) {
    const totalStock = productos.reduce((sum, p) => sum + (p.stockFisico || 0), 0);
    // Datos simulados de tendencias (en producción vendrían del backend)
    const tendencias = {
        productos: { valor: productos.length, cambio: +2.5 },
        clientes: { valor: clientes.length, cambio: +1.2 },
        auditoria: { valor: auditoria.length, cambio: +8.7 },
        stock: { valor: totalStock, cambio: -3.4 }
    };

    const kpiData = [
        { label: 'Catálogo Activo', value: productos.length, trend: tendencias.productos.cambio, icon: 'inventory_2', color: 'sky' },
        { label: 'Clientes Activos', value: clientes.length, trend: tendencias.clientes.cambio, icon: 'group', color: 'sky' },
        { label: 'Eventos Auditados', value: auditoria.length, trend: tendencias.auditoria.cambio, icon: 'verified_user', color: 'emerald' },
        { label: 'Salud de Stock', value: totalStock, trend: tendencias.stock.cambio, icon: 'warning', color: 'amber' }
    ];

    const kpiSection = document.getElementById('kpi-section');
    if (kpiSection) {
        kpiSection.innerHTML = kpiData.map(kpi => `
            <div class="bg-carbon-900/80 p-5 rounded-2xl border border-carbon-700 shadow-card flex flex-col justify-between group:hover:shadow-xl transition-shadow">
                <div class="flex items-center justify-between">
                    <span class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">${kpi.label}</span>
                    <div class="w-8 h-8 rounded-lg bg-${kpi.color-500}/10 text-${kpi.color-400} flex items-center justify-center">
                        <span class="material-symbols-outlined text-[18px]">${kpi.icon}</span>
                    </div>
                </div>
                <div class="mt-3">
                    <div class="text-2xl font-extrabold text-white font-mono-numbers">${kpi.value}</div>
                    <div class="flex items-center gap-1.5 text-[10px] ${kpi.trend >= 0 ? 'text-emerald-400' : 'text-rose-400'}">
                        <span class="font-mono font-medium">${kpi.trend >= 0 ? '+' : ''}${kpi.trend}%</span>
                        <span class="material-symbols-outlined text-[10px]">${kpi.trend >= 0 ? 'expand_more' : 'trending_down'}</span>
                    </div>
                </div>
            </div>
        `).join('');
    }
}

// ---------------------------------------------------------------------
// 3. D3.JS VISUALIZATIONS
// ---------------------------------------------------------------------
async function renderD3Visualizations(productos, auditoria) {
    const vizContainer = document.getElementById('d3-visualizations');
    if (!vizContainer) return;

    // El dashboard se vuelve a renderizar en cada navegación: no acumular SVG.
    vizContainer.innerHTML = '';

    try {
        // Nota: no existe endpoint de listado de ventas/stock global;
        // los gráficos usan auditoría y el catálogo ya cargado.

        // Chart 1: Actividad Transaccional Reciente - Line Chart
        renderTransactionLineChart(vizContainer, auditoria);

        // Chart 2: Salud de Stock - Donut Chart
        renderStockDonutChart(vizContainer, productos);

        // Chart 3: Pasarelas de Pago - Bar Chart
        renderPaymentBarChart(vizContainer);

    } catch (error) {
        console.error('Error cargando datos para D3.js:', error);
        vizContainer.innerHTML = `
            <div class="p-8 bg-carbon-900/50 rounded-2xl border border-carbon-700/30 text-slate-400 text-center">
                <span class="material-symbols-outlined text-[48px] text-slate-600">error</span>
                <p class="mt-2">No se pudieron cargar las visualizaciones D3.js</p>
            </div>
        `;
    }
}

function renderTransactionLineChart(container, auditoria) {
    const canvas = document.createElement('div');
    canvas.className = 'relative bg-carbon-900/80 rounded-2xl border border-carbon-700 p-6 shadow-card xl:col-span-2';
    canvas.innerHTML = `
        <h3 class="text-font text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Actividad Transaccional Reciente</h3>
        <div id="transaction-line-chart" class="relative h-64"></div>
    `;
    container.appendChild(canvas);

    // Datos simulados basados en auditoria (en producción obtendría del backend)
    const datosTransaccionales = auditoria.slice(0, 30).map((log, i) => ({
        fecha: new Date(Date.now() - i * 3600000),
        monto: Math.floor(Math.random() * 50000) + 1000,
        accion: log.accion
    })).reverse();

    // Si no hay datos, usar datos simulados
    const datos = datosTransaccionales.length > 0 ? datosTransaccionales : Array.from({ length: 30 }, (_, i) => ({
        fecha: new Date(Date.now() - (30 - i) * 3600000),
        monto: Math.floor(Math.random() * 50000) + 1000,
        accion: i % 5 === 0 ? 'COMPRA' : i % 5 === 1 ? 'CREACION' : i % 5 === 2 ? 'ELIMINACION' : 'TRANSFERENCIA'
    }));

    const chartEl = canvas.querySelector('#transaction-line-chart');
    const ancho = Math.max(300, chartEl.clientWidth || 400);
    const alto = Math.max(200, chartEl.clientHeight || 256);
    const margin = { top: 20, right: 20, bottom: 30, left: 60 };
    const width = ancho - margin.left - margin.right;
    const height = alto - margin.top - margin.bottom;

    const svg = d3.select(chartEl)
        .append('svg')
        .attr('viewBox', `0 0 ${ancho} ${alto}`)
        .attr('width', '100%')
        .attr('height', '100%')
        .attr('role', 'img')
        .attr('aria-label', 'Actividad transaccional reciente')
        .append('g')
        .attr('transform', `translate(${margin.left},${margin.top})`);

    // Escalas
    const x = d3.scaleTime()
        .domain(d3.extent(datos, d => d.fecha))
        .range([0, width]);

    const y = d3.scaleLinear()
        .domain([0, d3.max(datos, d => d.monto) * 1.1])
        .range([height, 0]);

    // Eje X
    svg.append('g')
        .attr('class', 'x-axis')
        .attr('transform', `translate(0,${height})`)
        .call(d3.axisBottom(x).ticks(6).tickFormat(d3.timeFormat('%H:%M')))
        .selectAll('.tick line')
        .attr('stroke', '#3F3F46');

    // Eje Y
    svg.append('g')
        .attr('class', 'y-axis')
        .call(d3.axisLeft(y).tickFormat(d => `$${d.toLocaleString()}`))
        .selectAll('.tick line')
        .attr('stroke', '#3F3F46');

    // Área de relleno con gradiente
    const defs = svg.append('defs');

    // Gradient vertical
    const gradient = defs.append('linearGradient')
        .attr('x1', '0%')
        .attr('y1', '0%')
        .attr('x2', '0%')
        .attr('y2', '100%')
        .attr('id', 'transaction-gradient');

    gradient.append('stop')
        .attr('offset', '0%')
        .attr('stop-color', 'rgba(255, 107, 0, 0.3)');

    gradient.append('stop')
        .attr('offset', '100%')
        .attr('stop-color', 'rgba(255, 107, 0, 0)');

    // Línea
    const line = d3.line()
        .x(d => x(d.fecha))
        .y(d => y(d.monto))
        .curve(d3.curveMonotoneX);

    // Path de la línea
    svg.append('path')
        .datum(datos)
        .attr('fill', 'url(#transaction-gradient)')
        .attr('stroke', '#FF6B00')
        .attr('stroke-width', 2)
        .attr('d', line);

    // Points
    svg.selectAll('.data-point')
        .data(datos)
        .enter().append('circle')
        .attr('class', 'data-point')
        .attr('cx', d => x(d.fecha))
        .attr('cy', d => y(d.monto))
        .attr('r', 3)
        .attr('fill', '#FF6B00')
        .attr('stroke', '#0A0A0A')
        .attr('stroke-width', 1);

    // Tooltip dentro de la tarjeta para que se elimine junto con el gráfico.
    const tooltip = document.createElement('div');
    tooltip.className = 'absolute opacity-0 pointer-events-none z-10 text-[10px] font-medium text-white bg-carbon-800 border border-carbon-600 rounded-lg px-2 py-1 shadow-lg transition-opacity';
    tooltip.innerHTML = '$<span></span>';
    canvas.appendChild(tooltip);

    svg.selectAll('.data-point')
        .on('mouseover', (event, d) => {
            const rect = canvas.getBoundingClientRect();
            tooltip.querySelector('span').textContent = d.monto.toLocaleString();
            tooltip.style.left = `${Math.max(4, Math.min(event.clientX - rect.left + 12, rect.width - 90))}px`;
            tooltip.style.top = `${Math.max(4, event.clientY - rect.top - 28)}px`;
            tooltip.style.opacity = '1';
        })
        .on('mousemove', (event) => {
            const rect = canvas.getBoundingClientRect();
            tooltip.style.left = `${Math.max(4, Math.min(event.clientX - rect.left + 12, rect.width - 90))}px`;
            tooltip.style.top = `${Math.max(4, event.clientY - rect.top - 28)}px`;
        })
        .on('mouseout', () => {
            tooltip.style.opacity = '0';
        });
}

function renderStockDonutChart(container, productos) {
    const canvas = document.createElement('div');
    canvas.className = 'bg-carbon-900/80 rounded-2xl border border-carbon-700 p-6 shadow-card';
    canvas.innerHTML = `
        <h3 class="text-font text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Salud de Stock</h3>
        <div id="stock-donut-chart" class="relative h-64"></div>
    `;
    container.appendChild(canvas);

    // Calcular estadísticas de stock
    const totalProductos = productos.length;
    const stockCritico = productos.filter(p => {
        const stock = p.stockDisponible ?? p.stockFisico;
        return stock > 0 && stock <= 10;
    }).length;
    const stockOptimo = productos.filter(p => {
        const stock = p.stockDisponible ?? p.stockFisico;
        return stock > 10;
    }).length;
    const stockAgotado = productos.filter(p => {
        const stock = p.stockDisponible ?? p.stockFisico;
        return stock <= 0;
    }).length;

    const total = stockCritico + stockOptimo + stockAgotado;
    const constCritico = total > 0 ? (stockCritico / total) * 100 : 0;
    const constOptimo = total > 0 ? (stockOptimo / total) * 100 : 0;
    const constAgotado = total > 0 ? (stockAgotado / total) * 100 : 0;

    const chartEl = canvas.querySelector('#stock-donut-chart');
    const ancho = Math.max(300, chartEl.clientWidth || 400);
    const alto = Math.max(200, chartEl.clientHeight || 256);
    const radio = Math.min(ancho * 0.4, alto * 0.42);
    const centroX = ancho * 0.34;
    const centroY = alto / 2;

    const svg = d3.select(chartEl)
        .append('svg')
        .attr('viewBox', `0 0 ${ancho} ${alto}`)
        .attr('width', '100%')
        .attr('height', '100%')
        .attr('role', 'img')
        .attr('aria-label', 'Distribución de salud de stock')
        .append('g')
        .attr('transform', `translate(${centroX},${centroY})`);

    // Datos para el donut
    const datos = [
        { label: 'Crítico', valor: Math.max(0.01, constCritico), color: '#FF6B00' },
        { label: 'Óptimo', valor: Math.max(0.01, constOptimo), color: '#FFFFFF' },
        { label: 'Agotado', valor: Math.max(0.01, constAgotado), color: '#FFB86B' }
    ];

    // Arco donut
    const arc = d3.arc()
        .innerRadius(radio * 0.6)
        .outerRadius(radio);

    // Pie layout
    const pie = d3.pie()
        .value(d => d.valor)
        .sort(null);

    // Dibujar sectores
    svg.selectAll('path')
        .data(pie(datos))
        .enter()
        .append('path')
        .attr('fill', d => d.data.color)
        .attr('d', arc)
        .each(function(d) { this._current = d; });

    // Efecto de animación
    svg.selectAll('path')
        .transition()
        .duration(1000)
        .attrTween('d', function(d) {
            const interpolate = d3.interpolate(this._current, d);
            return function(t) {
                return arc(interpolate(t));
            };
        });

    // Valores centrales y leyenda
    svg.append('text')
        .attr('text-anchor', 'middle')
        .attr('y', -4)
        .attr('fill', '#FFFFFF')
        .attr('font-size', '20px')
        .attr('font-weight', 'bold')
        .text(total);

    svg.append('text')
        .attr('text-anchor', 'middle')
        .attr('y', 14)
        .attr('fill', '#94A3B8')
        .attr('font-size', '10px')
        .text('productos');

    const legend = svg.append('g')
        .attr('transform', `translate(${ancho * 0.62 - centroX}, ${-36})`)
        .selectAll('g')
        .data(datos)
        .enter()
        .append('g')
        .attr('transform', (d, i) => `translate(0, ${i * 24})`);

    legend.append('circle')
        .attr('fill', d => d.color)
        .attr('r', 6);

    legend.append('text')
        .attr('x', 12)
        .attr('y', 4)
        .attr('font-size', '10px')
        .attr('fill', '#E0E0E0')
        .text(d => `${d.label}: ${Math.round(d.valor)}%`);
}

function renderPaymentBarChart(container) {
    const canvas = document.createElement('div');
    canvas.className = 'bg-carbon-900/80 rounded-2xl border border-carbon-700 p-6 shadow-card';
    canvas.innerHTML = `
        <h3 class="text-font text-sm font-bold text-slate-400 uppercase tracking-wider mb-4">Volumen de Transacciones por Pasarela</h3>
        <div id="payment-bar-chart" class="relative h-48"></div>
    `;
    container.appendChild(canvas);

    // Datos simulados - en producción obtendría del backend
    const datosPago = [
        { nombre: 'Mercado Pago', volumen: 124500 },
        { nombre: 'Stripe', volumen: 98200 }
    ];

    const chartEl = canvas.querySelector('#payment-bar-chart');
    const ancho = Math.max(300, chartEl.clientWidth || 500);
    const alto = Math.max(200, chartEl.clientHeight || 192);
    const margin = { top: 20, right: 20, bottom: 40, left: 70 };
    const width = ancho - margin.left - margin.right;
    const height = alto - margin.top - margin.bottom;

    const svg = d3.select(chartEl)
        .append('svg')
        .attr('viewBox', `0 0 ${ancho} ${alto}`)
        .attr('width', '100%')
        .attr('height', '100%')
        .attr('role', 'img')
        .attr('aria-label', 'Volumen de transacciones por pasarela')
        .append('g')
        .attr('transform', `translate(${margin.left},${margin.top})`);

    const x = d3.scaleBand()
        .domain(datosPago.map(d => d.nombre))
        .range([0, width])
        .padding(0.2);

    const y = d3.scaleLinear()
        .domain([0, d3.max(datosPago, d => d.volumen) * 1.1])
        .range([height, 0]);

    // Ejes
    svg.append('g')
        .attr('class', 'x-axis')
        .attr('transform', `translate(0,${height})`)
        .call(d3.axisBottom(x))
        .selectAll('.tick text')
        .attr('fill', '#E0E0E0')
        .attr('font-size', '10px');

    svg.append('g')
        .attr('class', 'y-axis')
        .call(d3.axisLeft(y).tickFormat(d => `$${d.toLocaleString()}`))
        .selectAll('.tick line')
        .attr('stroke', '#3F3F46');

    // Barras
    svg.selectAll('.bar')
        .data(datosPago)
        .enter()
        .append('rect')
        .attr('class', 'bar')
        .attr('x', d => x(d.nombre))
        .attr('y', d => y(d.volumen))
        .attr('width', x.bandwidth())
        .attr('height', d => height - y(d.volumen))
        .attr('fill', '#FF6B00')
        .attr('rx', 4)
        .attr('ry', 4);

    // Etiquetas de valor
    svg.selectAll('.label')
        .data(datosPago)
        .enter()
        .append('text')
        .attr('class', 'label')
        .attr('x', d => x(d.nombre) + x.bandwidth() / 2)
        .attr('y', d => y(d.volumen) - 5)
        .attr('text-anchor', 'middle')
        .attr('fill', '#FF6B00')
        .attr('font-size', '10px')
        .attr('font-weight', 'bold')
        .text(d => `$${d.volumen.toLocaleString()}`);

    // Etiquetas de eje X
    svg.append('text')
        .attr('x', width / 2)
        .attr('y', height + margin.bottom - 10)
        .attr('text-anchor', 'middle')
        .attr('fill', '#6B7280')
        .attr('font-size', '10px')
        .text('Pasarelas de Pago');

    // Etiqueta Y
    svg.append('text')
        .attr('transform', 'rotate(-90)')
        .attr('x', -height / 2)
        .attr('y', 10)
        .attr('fill', '#6B7280')
        .attr('font-size', '10px')
        .text('Volumen de Transacciones (ARS)');

}

// ---------------------------------------------------------------------
// Vistas cableadas: catálogo, terminal POS, clientes, auditoría/ventas
// ---------------------------------------------------------------------
function escaparHtml(texto) {
    return String(texto ?? '').replace(/[&<>"']/g, (c) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

async function renderCatalogo(container, page = 1) {
    const response = await fetchPaginated(api.productos.listar, page);
    const productos = response.datos;
    
    container.innerHTML = `
        <div class="flex items-center justify-between gap-4 pb-6 border-b border-carbon-700/80">
            <div>
                <h1 class="text-2xl font-bold text-white tracking-tight">Catálogo</h1>
                <p class="text-xs text-slate-400 mt-1">${productos.length} productos (Pág. ${response.paginaActual}/${response.totalPaginas})</p>
            </div>
            <div class="flex gap-2">
                <button id="btn-prev" class="px-3 py-2 bg-carbon-800 rounded-xl text-xs font-bold ${response.paginaActual <= 1 ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}">Anterior</button>
                <button id="btn-next" class="px-3 py-2 bg-carbon-800 rounded-xl text-xs font-bold ${response.paginaActual >= response.totalPaginas ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}">Siguiente</button>
                <button id="catalogo-ver-carrito" class="px-4 py-2.5 bg-brand hover:bg-brand-hover text-white rounded-xl text-xs font-bold transition-all shadow-glow-orange flex items-center gap-2 cursor-pointer">
                    <span class="material-symbols-outlined text-[18px]">shopping_cart</span>
                    <span>Ver Carrito</span>
                </button>
            </div>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-6">
            ${productos.map((p) => `
                <div class="catalogo-product-card bg-carbon-900/60 border border-carbon-700 rounded-2xl p-5 flex flex-col gap-2" data-nombre="${escaparHtml(p.nombre)}" data-categoria="${escaparHtml(p.categoria)}">
                    <h3 class="text-sm font-bold text-white">${escaparHtml(p.nombre)}</h3>
                    <p class="text-[11px] text-slate-400 uppercase tracking-wider">${escaparHtml(p.categoriaOriginal || p.categoria)}</p>
                    <p class="text-lg font-bold text-white font-mono-numbers">$ ${Number(p.precio).toFixed(2)}</p>
                    <p class="text-[11px] ${p.stockDisponible > 0 ? 'text-emerald-400' : 'text-red-400'} font-semibold">Stock disponible: ${p.stockDisponible}</p>
                    <button class="catalogo-agregar mt-2 px-3 py-2 bg-carbon-800 hover:bg-carbon-750 text-white rounded-xl text-xs font-bold border border-carbon-700 transition-colors" data-id="${p.id}" ${p.stockDisponible <= 0 ? 'disabled' : ''}>Agregar al Carrito</button>
                </div>`).join('') || '<p class="text-xs text-slate-400">Sin productos en este negocio.</p>'}
        </div>`;
    
    container.querySelector('#btn-prev')?.addEventListener('click', () => { if(response.paginaActual > 1) renderCatalogo(container, response.paginaActual - 1); });
    container.querySelector('#btn-next')?.addEventListener('click', () => { if(response.paginaActual < response.totalPaginas) renderCatalogo(container, response.paginaActual + 1); });

    const porId = new Map(productos.map((p) => [Number(p.id), p]));
    container.querySelector('#catalogo-ver-carrito')?.addEventListener('click', toggleCart);
    container.querySelectorAll('.catalogo-agregar').forEach((boton) => {
        boton.addEventListener('click', () => {
            const producto = porId.get(Number(boton.dataset.id));
            if (producto) {
                addToCart(producto);
            }
        });
    });
}

async function renderTerminal(container) {
    const productos = await api.productos.listar();
    const orden = new Map();

    container.innerHTML = `
        <div class="pb-6 border-b border-carbon-700/80">
            <h1 class="text-2xl font-bold text-white tracking-tight">Terminal POS</h1>
            <p class="text-xs text-slate-400 mt-1">Venta presencial con checkout online (Mercado Pago / Stripe).</p>
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 mt-6">
            <div class="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4" id="pos-productos">
                ${productos.filter((p) => p.stockDisponible > 0).map((p) => `
                    <div class="pos-product-item bg-carbon-900/60 border border-carbon-700 rounded-2xl p-4 flex items-center justify-between gap-3" data-nombre="${escaparHtml(p.nombre)}">
                        <div>
                            <h3 class="text-sm font-bold text-white">${escaparHtml(p.nombre)}</h3>
                            <p class="text-xs text-slate-400 font-mono-numbers">$ ${Number(p.precio).toFixed(2)} · Stock ${p.stockDisponible}</p>
                        </div>
                        <button class="pos-agregar px-3 py-2 bg-brand hover:bg-brand-hover text-white rounded-xl text-xs font-bold transition-colors" data-id="${p.id}">＋</button>
                    </div>`).join('') || '<p class="text-xs text-slate-400">Sin stock disponible.</p>'}
            </div>
            <div class="bg-carbon-900/60 border border-carbon-700 rounded-2xl p-5 flex flex-col gap-3 h-fit">
                <h3 class="text-sm font-bold text-white">Orden actual</h3>
                <div id="pos-lineas" class="flex flex-col gap-2 text-xs text-slate-300"><p class="text-slate-500">Sin ítems.</p></div>
                <p class="text-sm font-bold text-white font-mono-numbers">Total: $ <span id="pos-total">0.00</span></p>
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">Tipo de pago
                    <select id="pos-tipopago" class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none">
                        <option value="MERCADO_PAGO">Mercado Pago</option>
                        <option value="STRIPE">Stripe</option>
                    </select>
                </label>
                <button id="pos-cobrar" class="h-11 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-bold transition-all shadow-glow-orange">Cobrar</button>
                <div id="pos-resultado"></div>
            </div>
        </div>`;

    const porId = new Map(productos.map((p) => [Number(p.id), p]));
    const lineasEl = container.querySelector('#pos-lineas');
    const totalEl = container.querySelector('#pos-total');

    function pintarOrden() {
        if (orden.size === 0) {
            lineasEl.innerHTML = '<p class="text-slate-500">Sin ítems.</p>';
            totalEl.textContent = '0.00';
            return;
        }
        let total = 0;
        lineasEl.innerHTML = [...orden.entries()].map(([id, cantidad]) => {
            const p = porId.get(id);
            const subtotal = Number(p.precio) * cantidad;
            total += subtotal;
            return `<div class="flex items-center justify-between gap-2">
                <span>${escaparHtml(p.nombre)} x ${cantidad}</span>
                <span class="font-mono-numbers">$ ${subtotal.toFixed(2)}</span>
            </div>`;
        }).join('');
        totalEl.textContent = total.toFixed(2);
    }

    container.querySelectorAll('.pos-agregar').forEach((boton) => {
        boton.addEventListener('click', () => {
            const id = Number(boton.dataset.id);
            orden.set(id, (orden.get(id) || 0) + 1);
            pintarOrden();
        });
    });

    container.querySelector('#pos-cobrar')?.addEventListener('click', async () => {
        const resultadoEl = container.querySelector('#pos-resultado');
        resultadoEl.innerHTML = '';
        const actual = sesion.obtener();
        if (!actual || !actual.idCliente) {
            showToast('Inicie sesión para cobrar.', 'error');
            window.location.href = 'login.html';
            return;
        }
        if (orden.size === 0) {
            showToast('La orden está vacía.', 'error');
            return;
        }
        const payload = {
            idCliente: actual.idCliente,
            tipoPago: container.querySelector('#pos-tipopago').value,
            descuento: null,
            items: [...orden.entries()].map(([idProducto, cantidad]) => ({ idProducto, cantidad }))
        };
        try {
            const resultado = await api.ventas.crear(payload, obtenerClaveIdempotenciaCheckout(payload));
            orden.clear();
            pintarOrden();
            resultadoEl.innerHTML = `
                <div class="p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-[11px] text-emerald-300 flex flex-col gap-1">
                    <span class="font-bold">Checkout generado: ${escaparHtml(resultado.paymentId || '')}</span>
                    <span>${escaparHtml(resultado.mensaje || '')}</span>
                    ${resultado.urlPago ? `<a class="text-orange-400 hover:underline font-semibold" href="${escaparHtml(resultado.urlPago)}" target="_blank" rel="noopener">Abrir link de pago</a>` : ''}
                </div>`;
        } catch (error) {
            showToast(error?.message || 'No se pudo crear la venta.', 'error');
        }
    });
}

async function renderClientes(container, page = 1) {
    const response = await fetchPaginated(api.clientes.listar, page);
    const clientes = response.datos;
    container.innerHTML = `
        <div class="pb-6 border-b border-carbon-700/80 flex justify-between items-center">
            <div>
                <h1 class="text-2xl font-bold text-white tracking-tight">Clientes</h1>
                <p class="text-xs text-slate-400 mt-1">${clientes.length} clientes (Pág. ${response.paginaActual}/${response.totalPaginas})</p>
            </div>
            <div class="flex gap-2">
                <button id="btn-prev" class="px-3 py-2 bg-carbon-800 rounded-xl text-xs font-bold ${response.paginaActual <= 1 ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}">Anterior</button>
                <button id="btn-next" class="px-3 py-2 bg-carbon-800 rounded-xl text-xs font-bold ${response.paginaActual >= response.totalPaginas ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}">Siguiente</button>
            </div>
        </div>
        <div class="mt-6 overflow-x-auto bg-carbon-900/60 border border-carbon-700 rounded-2xl">
            <table class="w-full text-xs text-left">
                <thead>
                    <tr class="text-slate-400 uppercase tracking-wider text-[10px] border-b border-carbon-700/60">
                        <th class="px-4 py-3">ID</th>
                        <th class="px-4 py-3">DNI</th>
                        <th class="px-4 py-3">Nombre</th>
                        <th class="px-4 py-3">Localidad</th>
                        <th class="px-4 py-3">Estado</th>
                    </tr>
                </thead>
                <tbody>
                    ${clientes.map((c) => `
                        <tr class="cliente-row border-b border-carbon-700/30 text-slate-200">
                            <td class="px-4 py-2.5 font-mono-numbers">${c.id}</td>
                            <td class="px-4 py-2.5 font-mono-numbers">${escaparHtml(c.dni)}</td>
                            <td class="px-4 py-2.5 font-semibold text-white">${escaparHtml(c.nombreCompleto)}</td>
                            <td class="px-4 py-2.5">${escaparHtml(c.localidad || '—')}</td>
                            <td class="px-4 py-2.5 ${c.activo ? 'text-emerald-400' : 'text-red-400'} font-semibold">${c.activo ? 'Activo' : 'Baja'}</td>
                        </tr>`).join('') || '<tr><td class="px-4 py-4 text-slate-500" colspan="5">Sin clientes en este negocio.</td></tr>'}
                </tbody>
            </table>
        </div>`;
    container.querySelector('#btn-prev')?.addEventListener('click', () => { if(response.paginaActual > 1) renderClientes(container, response.paginaActual - 1); });
    container.querySelector('#btn-next')?.addEventListener('click', () => { if(response.paginaActual < response.totalPaginas) renderClientes(container, response.paginaActual + 1); });
}

async function renderAuditoria(container, page = 1) {
    const response = await fetchPaginated(api.auditoria.listar, page);
    const registros = response.datos;
    
    container.innerHTML = `
        <div class="pb-6 border-b border-carbon-700/80 flex justify-between items-center">
            <div>
                <h1 class="text-2xl font-bold text-white tracking-tight">Auditoría y Ventas</h1>
                <p class="text-xs text-slate-400 mt-1">Pág. ${response.paginaActual}/${response.totalPaginas}</p>
            </div>
            <div class="flex gap-2">
                <button id="btn-prev" class="px-3 py-2 bg-carbon-800 rounded-xl text-xs font-bold ${response.paginaActual <= 1 ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}">Anterior</button>
                <button id="btn-next" class="px-3 py-2 bg-carbon-800 rounded-xl text-xs font-bold ${response.paginaActual >= response.totalPaginas ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}">Siguiente</button>
            </div>
        </div>
        <div class="mt-6 bg-carbon-900/60 border border-carbon-700 rounded-2xl p-5">
            <div id="auditoria-ventas"></div>
        </div>
        <div class="mt-4 bg-carbon-900/60 border border-carbon-700 rounded-2xl p-5">
            <h3 class="text-sm font-bold text-white mb-3">Registros recientes</h3>
            <div class="flex flex-col gap-2 max-h-96 overflow-y-auto">
                ${registros.map((r) => `
                    <div class="text-[11px] text-slate-300 border-b border-carbon-700/30 pb-2">
                        <span class="font-bold text-white">${escaparHtml(r.accion)}</span>
                        <span class="text-slate-500">· ${escaparHtml(r.entidad)} #${r.idEntidad}</span>
                        <span class="text-slate-500">· ${escaparHtml(r.resultado || '')}</span>
                        <p class="text-slate-400 mt-0.5">${escaparHtml(r.descripcion || '')}</p>
                    </div>`).join('') || '<p class="text-[11px] text-slate-500">Sin registros.</p>'}
            </div>
        </div>`;
    
    container.querySelector('#btn-prev')?.addEventListener('click', () => { if(response.paginaActual > 1) renderAuditoria(container, response.paginaActual - 1); });
    container.querySelector('#btn-next')?.addEventListener('click', () => { if(response.paginaActual < response.totalPaginas) renderAuditoria(container, response.paginaActual + 1); });
    
    const ventasEl = container.querySelector('#auditoria-ventas');
    if (ventasEl) {
        renderSalesList(ventasEl);
    }
}