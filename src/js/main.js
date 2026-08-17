import { fetchData } from './api.js';
import { addToCart, toggleCart, renderCart } from './components/cart-sidebar.js';

// Basic client-side routing
document.addEventListener('DOMContentLoaded', () => {
    const app = document.getElementById('app');

    const routes = {
        '#products': renderProductCatalog,
        '#account': renderAccountSection,
        '#sales-management': renderSalesManagement,
        '': renderProductCatalog // Default route
    };

    function router() {
        const hash = window.location.hash;
        const route = routes[hash] || routes[''];
        if (route) {
            route(app);
        } else {
            app.innerHTML = '<h2>404 - Página no encontrada</h2>';
        }
    }

    window.addEventListener('hashchange', router);
    router(); // Initial route load
});

async function renderProductCatalog(element) {
    element.innerHTML = '<h2>Catálogo de Productos</h2><div id="product-list"></div>';
    try {
        const products = await fetchData('/productos');
        const productListDiv = document.getElementById('product-list');
        productListDiv.innerHTML = products.map(product => `
            <div class="product-card">
                <h3>${product.nombre}</h3>
                <p>Precio: $${product.precio}</p>
                <p>Stock Disponible: ${product.stockDisponible}</p>
                <p>Categoría: ${product.categoria}</p>
                <button class="add-to-cart-btn" data-product='${JSON.stringify(product)}'>Agregar al Carrito</button>
            </div>
        `).join('');

        productListDiv.querySelectorAll('.add-to-cart-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                const product = JSON.parse(e.target.dataset.product);
                addToCart(product);
            });
        });

    } catch (error) {
        console.error('Error loading products:', error);
        element.innerHTML = '<p>No se pudieron cargar los productos.</p>';
    }
}

import { renderAccountSummary } from './components/account-summary.js';
import { renderTransferForm } from './components/transfer-form.js';

function renderAccountSection(element) {
    element.innerHTML = '<h2>Mi Cuenta</h2><div id="account-details"></div>';
    const accountDetailsDiv = document.getElementById('account-details');
    renderAccountSummary(accountDetailsDiv);
    renderTransferForm(accountDetailsDiv);
}

import { renderSalesList } from './components/sales-list.js';

function renderSalesManagement(element) {
    element.innerHTML = '<h2>Gestión de Ventas</h2><div id="sales-panel"></div>';
    const salesPanelDiv = document.getElementById('sales-panel');
    renderSalesList(salesPanelDiv);
}
