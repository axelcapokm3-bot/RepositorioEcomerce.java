import { fetchData } from '../../js/api.js';
import { showToast } from '../../js/utils/toast-notifier.js';

export async function renderSalesList(element) {
    element.innerHTML = '<h3>Lista de Ventas</h3><div id="sales-table"></div>';
    const salesTableDiv = document.getElementById('sales-table');

    try {
        const sales = await fetchData('/ventas'); // Assuming an endpoint /ventas exists for listing all sales

        if (sales.length === 0) {
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
                                <button class="action-btn pay-btn" data-sale-id="${sale.id}" ${sale.estadoCompra === 'PAGADO' || sale.estadoCompra === 'CANCELADO' ? 'disabled' : ''}>Registrar Pago</button>
                                <button class="action-btn cancel-btn" data-sale-id="${sale.id}" ${sale.estadoCompra === 'PAGADO' || sale.estadoCompra === 'CANCELADO' ? 'disabled' : ''}>Cancelar Venta</button>
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
                handleSaleAction(saleId, 'pago', element);
            });
        });

        salesTableDiv.querySelectorAll('.cancel-btn').forEach(button => {
            button.addEventListener('click', (e) => {
                const saleId = parseInt(e.target.dataset.saleId);
                handleSaleAction(saleId, 'cancelar', element);
            });
        });

    } catch (error) {
        console.error('Error loading sales list:', error);
        salesTableDiv.innerHTML = '<p>No se pudieron cargar las ventas.</p>';
    }
}

async function handleSaleAction(saleId, actionType, parentElement) {
    try {
        await fetchData(`/ventas/${saleId}/${actionType}`, {
            method: 'POST',
        });
        showToast(`Venta ${saleId} ${actionType === 'pago' ? 'registrada como pagada' : 'cancelada'} con éxito!`, 'success');
        renderSalesList(parentElement); // Re-render the list to update status
    } catch (error) {
        console.error(`Error al ${actionType} la venta ${saleId}:`, error);
        showToast(`Error al ${actionType} la venta ${saleId}.`, 'error');
    }
}
