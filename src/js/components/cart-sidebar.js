import { fetchData } from '../../js/api.js';
import { showToast } from '../../js/utils/toast-notifier.js';

const cart = [];
let isCartOpen = false;

export function addToCart(product) {
    const existingItem = cart.find(item => item.id === product.id);
    if (existingItem) {
        existingItem.quantity++;
    } else {
        cart.push({ ...product, quantity: 1 });
    }
    showToast(`${product.nombre} añadido al carrito`, 'success');
    renderCart();
}

export function removeFromCart(productId) {
    const index = cart.findIndex(item => item.id === productId);
    if (index > -1) {
        cart.splice(index, 1);
    }
    renderCart();
}

export function renderCart() {
    const cartSidebar = document.getElementById('cart-sidebar');
    if (!cartSidebar) return;

    const cartItemsContainer = cartSidebar.querySelector('.cart-items');
    const cartTotalSpan = cartSidebar.querySelector('.cart-total span');

    cartItemsContainer.innerHTML = cart.map(item => `
        <div class="cart-item">
            <span>${item.nombre} x ${item.quantity}</span>
            <span>$${(item.precio * item.quantity).toFixed(2)}
                <button class="remove-from-cart-btn" data-product-id="${item.id}">X</button>
            </span>
        </div>
    `).join('');

    const total = cart.reduce((sum, item) => sum + (item.precio * item.quantity), 0);
    cartTotalSpan.textContent = total.toFixed(2);

    cartSidebar.querySelectorAll('.remove-from-cart-btn').forEach(button => {
        button.addEventListener('click', (e) => {
            const productId = parseInt(e.target.dataset.productId);
            removeFromCart(productId);
        });
    });
}

export function toggleCart() {
    const cartSidebar = document.getElementById('cart-sidebar');
    isCartOpen = !isCartOpen;
    if (cartSidebar) {
        cartSidebar.classList.toggle('open', isCartOpen);
    }
}

export async function checkout() {
    if (cart.length === 0) {
        showToast('El carrito está vacío.', 'error');
        return;
    }

    const tipoPago = document.querySelector('input[name="tipoPago"]:checked')?.value;
    if (!tipoPago) {
        showToast('Por favor, selecciona un tipo de pago.', 'error');
        return;
    }

    const itemsForSale = cart.map(item => ({
        idProducto: item.id,
        cantidad: item.quantity
    }));

    const createVentaRequest = {
        idCliente: 1, // Hardcoded for now, will be dynamic with auth
        tipoPago: tipoPago,
        descuento: {
            tipo: "PORCENTAJE", // Example, can be dynamic
            valor: 0.0
        },
        items: itemsForSale
    };

    try {
        await fetchData('/ventas/crear', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(createVentaRequest),
        });
        showToast('Compra finalizada con éxito!', 'success');
        cart.length = 0; // Clear cart
        renderCart();
        toggleCart(); // Close cart after checkout
    } catch (error) {
        console.error('Error al finalizar la compra:', error);
        showToast('Error al finalizar la compra.', 'error');
    }
}

// Initial setup for the cart sidebar
document.addEventListener('DOMContentLoaded', () => {
    const mainApp = document.getElementById('app');
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
                <label><input type="radio" name="tipoPago" value="EFECTIVO"> Efectivo</label>
                <label><input type="radio" name="tipoPago" value="TARJETA"> Tarjeta</label>
                <label><input type="radio" name="tipoPago" value="TRANSFERENCIA"> Transferencia</label>
            </div>
            <button class="checkout-btn">Finalizar Compra</button>
        </div>
    `;
    mainApp.insertAdjacentHTML('beforeend', cartSidebarHTML);

    document.querySelector('.close-cart-btn').addEventListener('click', toggleCart);
    document.querySelector('.checkout-btn').addEventListener('click', checkout);

    const openCartButton = document.createElement('button');
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

    renderCart(); // Render cart on load
});
