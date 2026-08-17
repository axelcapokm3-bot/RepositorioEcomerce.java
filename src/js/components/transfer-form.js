import { fetchData } from '../../js/api.js';
import { showToast } from '../../js/utils/toast-notifier.js';

export function renderTransferForm(element) {
    element.innerHTML += `
        <h3>Realizar Transferencia</h3>
        <div class="transfer-form">
            <label for="idCuentaOrigen">ID Cuenta Origen:</label>
            <input type="number" id="idCuentaOrigen" value="1" required>
            <label for="idCuentaDestino">ID Cuenta Destino:</label>
            <input type="number" id="idCuentaDestino" required>
            <label for="montoTransferencia">Monto:</label>
            <input type="number" id="montoTransferencia" step="0.01" required>
            <button id="transfer-button">Transferir</button>
        </div>
    `;

    document.getElementById('transfer-button').addEventListener('click', async () => {
        const idCuentaOrigen = parseInt(document.getElementById('idCuentaOrigen').value);
        const idCuentaDestino = parseInt(document.getElementById('idCuentaDestino').value);
        const monto = parseFloat(document.getElementById('montoTransferencia').value);

        if (isNaN(idCuentaOrigen) || isNaN(idCuentaDestino) || isNaN(monto) || monto <= 0) {
            showToast('Por favor, ingresa datos válidos para la transferencia.', 'error');
            return;
        }

        const transferRequest = {
            idCuentaOrigen: idCuentaOrigen,
            idCuentaDestino: idCuentaDestino,
            monto: monto
        };

        try {
            await fetchData('/cuentas/transferir', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(transferRequest),
            });
            showToast('Transferencia realizada con éxito!', 'success');
            // Optionally re-render account summary
            // renderAccountSummary(document.querySelector('.account-section')); 
        } catch (error) {
            console.error('Error al realizar la transferencia:', error);
            showToast('Error al realizar la transferencia.', 'error');
        }
    });
}
