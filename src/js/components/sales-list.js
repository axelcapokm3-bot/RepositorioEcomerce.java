import { api } from '../../js/api.js';
import { showToast } from '../../js/utils/toast-notifier.js';

export async function renderSalesList(element) {
    element.innerHTML = `
        <h3>Gestión de Ventas</h3>
        <div class="sales-lookup">
            <input id="sales-lookup-id" type="number" min="1" placeholder="ID de venta" />
            <button id="sales-lookup-btn" class="action-btn">Buscar</button>
        </div>
        <div id="sales-table"><p>La API no expone listado de ventas; busque por ID para operar.</p></div>`;
    const salesTableDiv = element.querySelector('#sales-table');

    element.querySelector('#sales-lookup-btn')?.addEventListener('click', async () => {
        const id = parseInt(element.querySelector('#sales-lookup-id').value, 10);
        if (!id || id <= 0) {
            showToast('Indique un ID de venta válido.', 'error');
            return;
        }
        try {
            const venta = await api.ventas.obtener(id);
            renderTabla(salesTableDiv, [venta], element);
        } catch (error) {
            console.error('Error buscando la venta:', error);
            salesTableDiv.innerHTML = '<p>No se encontró la venta solicitada.</p>';
        }
    });
}

function renderTabla(salesTableDiv, sales, parentElement) {
    if (!sales || sales.length === 0) {
        salesTableDiv.innerHTML = '<p>No hay ventas registradas.</p>';
        return;
    }

        salesTableDiv.innerHTML = `
            <table>
                <thead>
                    <tr>
                        <th>ID Venta</th>
                        <th>Cliente ID</th>
                        <th>Fecha Compra</th>
                        <th>Estado</th>
                        <th>Tipo Pago</th>
                        <th>Total</th>
                        <th>Acciones</th>
                    </tr>
                </thead>
                <tbody>
                    ${sales.map(sale => `
                        <tr>
                            <td>${sale.id}</td>
                            <td>${sale.idCliente}</td>
                            <td>${new Date(sale.fechaCompra).toLocaleString()}</td>
                            <td><span class="status-badge status-${sale.estadoCompra.toLowerCase()}">${sale.estadoCompra}</span></td>
                            <td>${sale.tipoPago}</td>
                            <td>$${sale.detalles.reduce((sum, item) => sum + (item.precioUnitario * item.cantidad), 0).toFixed(2)}</td>
                            <td>
                                <button class="action-btn pay-btn" data-sale-id="${sale.id}" ${sale.estadoCompra === 'PENDIENTE' ? '' : 'disabled'}>Registrar Pago</button>
                                <button class="action-btn cancel-btn" data-sale-id="${sale.id}" ${sale.estadoCompra === 'PENDIENTE' ? '' : 'disabled'}>Cancelar Venta</button>
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;

        // Add event listeners for action buttons
        salesTableDiv.querySelectorAll('.pay-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                const saleId = parseInt(e.target.dataset.saleId);
                handleSaleAction(saleId, 'pago', parentElement);
            });
        });

        salesTableDiv.querySelectorAll('.cancel-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                const saleId = parseInt(e.target.dataset.saleId);
                handleSaleAction(saleId, 'cancelar', parentElement);
            });
        });
}

async function handleSaleAction(saleId, actionType, parentElement) {
    try {
        if (actionType === 'pago') {
            await api.ventas.pagar(saleId);
        } else {
            await api.ventas.cancelar(saleId);
        }
        showToast(`Venta ${saleId} ${actionType === 'pago' ? 'registrada como pagada' : 'cancelada'} con éxito!`, 'success');
        renderSalesList(parentElement); // Re-render the list to update status
    } catch (error) {
        console.error(`Error al ${actionType} la venta ${saleId}:`, error);
        showToast(`Error al ${actionType} la venta ${saleId}.`, 'error');
    }
}
