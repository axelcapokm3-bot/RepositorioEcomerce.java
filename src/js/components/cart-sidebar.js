import { api, sesion } from '../../js/api.js';
import { showToast } from '../../js/utils/toast-notifier.js';

const cart = [];
let isCartOpen = false;

const CLAVE_PENDIENTE = 'ecommerce.checkout.pendiente';
const HOSTS_PAGO_PERMITIDOS = [
    'checkout.stripe.com',
    'mercadopago.com',
    'mercadopago.com.ar',
    'mercadopago.com.br',
    'mercadopago.com.mx',
    'mercadopago.com.co',
    'mercadopago.com.pe',
    'mercadopago.com.uy',
    'mercadopago.cl'
];

function aplicarCarrito(datos) {
    const items = datos && Array.isArray(datos.items) ? datos.items : [];
    cart.length = 0;
    items.forEach(item => {
        cart.push({
            id: Number(item.idProducto),
            nombre: String(item.nombre ?? ''),
            precio: Number(item.precio ?? 0),
            quantity: Number(item.cantidad ?? 0)
        });
    });
    renderCart();
}

async function cargarCarrito() {
    if (!sesion.obtener()) {
        cart.length = 0;
        renderCart();
        return;
    }
    try {
        aplicarCarrito(await api.carrito.obtener());
    } catch (error) {
        console.error('No se pudo cargar el carrito:', error);
        cart.length = 0;
        renderCart();
    }
}

async function vaciarCarritoEnServidor() {
    try {
        await api.carrito.vaciar();
    } catch (error) {
        console.error('No se pudo vaciar el carrito en el servidor:', error);
    }
}

export async function addToCart(product) {
    try {
        const datos = await api.carrito.agregar({
            idProducto: product.id,
            cantidad: 1
        });
        aplicarCarrito(datos);
        showToast(`${product.nombre} añadido al carrito`, 'success');
    } catch (error) {
        showToast(mensajeDeError(error, 'No se pudo agregar el producto al carrito.'), 'error');
    }
}

export async function removeFromCart(productId) {
    try {
        const datos = await api.carrito.eliminar(productId);
        aplicarCarrito(datos);
    } catch (error) {
        showToast(mensajeDeError(error, 'No se pudo quitar el producto del carrito.'), 'error');
    }
}

export function renderCart() {
    const cartSidebar = document.getElementById('cart-sidebar');
    if (!cartSidebar) return;

    const cartItemsContainer = cartSidebar.querySelector('.cart-items');
    const cartTotalSpan = cartSidebar.querySelector('.cart-total span');
    if (!cartItemsContainer) return;

    cartItemsContainer.innerHTML = '';

    cart.forEach(item => {
        const fila = document.createElement('div');
        fila.className = 'cart-item';

        const detalle = document.createElement('span');
        detalle.textContent = `${item.nombre} x ${item.quantity}`;

        const subtotal = document.createElement('span');
        subtotal.textContent = `$${(item.precio * item.quantity).toFixed(2)} `;

        const boton = document.createElement('button');
        boton.className = 'remove-from-cart-btn';
        boton.dataset.productId = String(item.id);
        boton.textContent = 'X';
        boton.addEventListener('click', () => removeFromCart(item.id));

        subtotal.appendChild(boton);
        fila.appendChild(detalle);
        fila.appendChild(subtotal);
        cartItemsContainer.appendChild(fila);
    });

    const total = cart.reduce((sum, item) => sum + (item.precio * item.quantity), 0);
    if (cartTotalSpan) {
        cartTotalSpan.textContent = total.toFixed(2);
    }
}

export function toggleCart() {
    const cartSidebar = document.getElementById('cart-sidebar');
    isCartOpen = !isCartOpen;
    if (cartSidebar) {
        cartSidebar.classList.toggle('open', isCartOpen);
    }
}

function leerPendiente() {
    try {
        const crudo = sessionStorage.getItem(CLAVE_PENDIENTE);
        return crudo ? JSON.parse(crudo) : null;
    } catch {
        return null;
    }
}

function guardarPendiente(datos) {
    try {
        sessionStorage.setItem(CLAVE_PENDIENTE, JSON.stringify(datos));
    } catch {
    }
}

function limpiarPendiente() {
    try {
        sessionStorage.removeItem(CLAVE_PENDIENTE);
    } catch {
    }
}

function crearClaveIdempotencia() {
    return (typeof crypto !== 'undefined' && crypto.randomUUID)
        ? crypto.randomUUID()
        : 'idemp-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);
}

export function urlPagoPermitida(url) {
    if (!url || typeof url !== 'string') return null;
    try {
        const destino = new URL(url, window.location.origin);
        if (destino.protocol !== 'https:') return null;
        const host = destino.hostname.toLowerCase();
        const permitido = HOSTS_PAGO_PERMITIDOS.some(
            base => host === base || host.endsWith('.' + base));
        return permitido ? destino.toString() : null;
    } catch {
        return null;
    }
}

function mensajeDeError(error, mensajePorDefecto) {
    if (error && typeof error.message === 'string' && error.message.trim()) {
        return error.message;
    }
    return mensajePorDefecto;
}

async function aplicarResultadoCheckout(resultado) {
    const estado = resultado && resultado.estado ? String(resultado.estado).toUpperCase() : '';
    const idVenta = resultado && resultado.transaccionId ? Number(resultado.transaccionId) : null;

    if (estado === 'APROBADO') {
        limpiarPendiente();
        await vaciarCarritoEnServidor();
        cart.length = 0;
        renderCart();
        toggleCart();
        showToast('Pago aprobado. ¡Gracias por tu compra!', 'success');
        return;
    }

    if (estado === 'PENDIENTE') {
        const pendiente = leerPendiente() || {};
        guardarPendiente({ ...pendiente, idVenta: idVenta });
        const urlPago = urlPagoPermitida(resultado.urlPago);
        if (urlPago) {
            showToast('Pedido creado. Te redirigimos a la pasarela para completar el pago.', 'info');
            window.location.assign(urlPago);
            return;
        }
        showToast('El pedido quedó pendiente de pago. Podés retomarlo desde el carrito.', 'error');
        return;
    }

    const mensaje = resultado && resultado.mensaje
        ? resultado.mensaje
        : 'El pago no se completó. Tu carrito quedó intacto; podés reintentar.';
    showToast(mensaje, 'error');
}

async function retomarPagoPendiente() {
    const pendiente = leerPendiente();
    if (!pendiente || !pendiente.idVenta) return;
    try {
        const resultado = await api.ventas.pagar(pendiente.idVenta);
        await aplicarResultadoCheckout(resultado);
    } catch (error) {
        console.error('Error al retomar el pago pendiente:', error);
        showToast(mensajeDeError(error, 'No se pudo retomar el pago. Podés volver a intentarlo.'), 'error');
    }
}

export async function checkout() {
    if (cart.length === 0) {
        const pendiente = leerPendiente();
        if (pendiente && pendiente.idVenta) {
            await retomarPagoPendiente();
            return;
        }
        showToast('El carrito está vacío.', 'error');
        return;
    }

    const tipoPago = document.querySelector('input[name="tipoPago"]:checked')?.value;
    if (!tipoPago) {
        showToast('Por favor, selecciona un tipo de pago.', 'error');
        return;
    }

    const clienteActual = sesion.obtener();
    if (!clienteActual || !clienteActual.idCliente) {
        showToast('Inicie sesión para finalizar la compra.', 'error');
        window.location.href = 'login.html';
        return;
    }

    const createVentaRequest = {
        idCliente: clienteActual.idCliente,
        tipoPago: tipoPago,
        descuento: null,
        items: cart.map(item => ({
            idProducto: item.id,
            cantidad: item.quantity
        }))
    };

    const pendienteAnterior = leerPendiente();
    const claveIdempotencia = pendienteAnterior && pendienteAnterior.claveIdempotencia
        ? pendienteAnterior.claveIdempotencia
        : crearClaveIdempotencia();
    const idVentaPendiente = pendienteAnterior && pendienteAnterior.idVenta
        ? pendienteAnterior.idVenta
        : null;

    guardarPendiente({
        claveIdempotencia: claveIdempotencia,
        idCliente: clienteActual.idCliente,
        idVenta: idVentaPendiente,
        creadoEn: Date.now()
    });

    try {
        const resultado = idVentaPendiente
            ? await api.ventas.pagar(idVentaPendiente)
            : await api.ventas.crear(createVentaRequest, claveIdempotencia);
        await aplicarResultadoCheckout(resultado);
    } catch (error) {
        console.error('Error al finalizar la compra:', error);
        showToast(mensajeDeError(error,
            'No se pudo iniciar el pago. Tu carrito quedó intacto; podés reintentar.'), 'error');
    }
}

// Initial setup for the cart sidebar
document.addEventListener('DOMContentLoaded', () => {
    const mainApp = document.getElementById('app') || document.body;
    if (mainApp && !document.getElementById('cart-sidebar')) {
        const cartSidebarHTML = `
            <div id="cart-sidebar" class="cart-sidebar">
                <div class="cart-header">
                    <h2>Carrito de Compras</h2>
                    <button class="close-cart-btn">&times;</button>
                </div>
                <div class="cart-items"></div>
                <div class="cart-total">Total: $<span>0.00</span></div>
                <div class="payment-options">
                    <h3>Tipo de Pago</h3>
                    <label><input type="radio" name="tipoPago" value="MERCADO_PAGO"> Mercado Pago</label>
                    <label><input type="radio" name="tipoPago" value="STRIPE"> Stripe</label>
                </div>
                <button class="checkout-btn">Finalizar Compra</button>
            </div>
        `;
        mainApp.insertAdjacentHTML('beforeend', cartSidebarHTML);

        document.querySelector('.close-cart-btn')?.addEventListener('click', toggleCart);
        document.querySelector('.checkout-btn')?.addEventListener('click', checkout);
    }

    if (document.getElementById('app') && !document.getElementById('open-cart-floating-btn')) {
        const openCartButton = document.createElement('button');
        openCartButton.id = 'open-cart-floating-btn';
        openCartButton.textContent = '🛒 Ver Carrito';
        openCartButton.style.position = 'fixed';
        openCartButton.style.bottom = '20px';
        openCartButton.style.right = '20px';
        openCartButton.style.padding = '15px';
        openCartButton.style.backgroundColor = 'var(--primary-color)';
        openCartButton.style.color = 'white';
        openCartButton.style.border = 'none';
        openCartButton.style.borderRadius = '50px';
        openCartButton.style.cursor = 'pointer';
        openCartButton.style.boxShadow = '0 2px 5px rgba(0,0,0,0.2)';
        openCartButton.style.zIndex = '998';
        openCartButton.addEventListener('click', toggleCart);
        document.body.appendChild(openCartButton);
    }

    const pendiente = leerPendiente();
    if (pendiente && pendiente.idVenta && !document.getElementById('retomar-pago-btn')) {
        const retomarButton = document.createElement('button');
        retomarButton.id = 'retomar-pago-btn';
        retomarButton.textContent = '💳 Retomar pago pendiente';
        retomarButton.style.position = 'fixed';
        retomarButton.style.bottom = '90px';
        retomarButton.style.right = '20px';
        retomarButton.style.padding = '12px 18px';
        retomarButton.style.backgroundColor = 'var(--primary-color)';
        retomarButton.style.color = 'white';
        retomarButton.style.border = 'none';
        retomarButton.style.borderRadius = '50px';
        retomarButton.style.cursor = 'pointer';
        retomarButton.style.boxShadow = '0 2px 5px rgba(0,0,0,0.2)';
        retomarButton.style.zIndex = '998';
        retomarButton.addEventListener('click', retomarPagoPendiente);
        document.body.appendChild(retomarButton);
    }

    cargarCarrito();
});
