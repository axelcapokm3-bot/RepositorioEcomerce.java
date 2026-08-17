import { fetchData } from '../../js/api.js';
import { showToast } from '../../js/utils/toast-notifier.js';

export async function renderAccountSummary(element) {
    element.innerHTML = '<h3>Consultar Saldo de Cuenta</h3>';
    const accountId = 1; // Hardcoded for now, will be dynamic with auth
    try {
        const account = await fetchData(`/cuentas/${accountId}`);
        element.innerHTML += `
            <div class="account-summary">
                <p><strong>Nombre de Cuenta:</strong> ${account.nombreCuenta}</p>
                <p><strong>Correo Electrónico:</strong> ${account.correoElectronico}</p>
                <p><strong>Saldo Actual:</strong> $${account.saldo.toFixed(2)}</p>
                <p><strong>Rol de Usuario:</strong> ${account.rolUsuario}</p>
            </div>
        `;
    } catch (error) {
        console.error('Error loading account summary:', error);
        element.innerHTML += '<p>No se pudo cargar el resumen de la cuenta.</p>';
    }
}
