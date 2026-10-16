import { api, sesion } from './api.js';
import { showToast } from './utils/toast-notifier.js';

const SELECTOR_ID = 'negocio-switcher';
const ETIQUETA_ID = 'negocio-actual';
const VALOR_VINCULAR = '__vincular__';

function etiquetaNegocio(sesionActual, vinculos) {
    if (!sesionActual) {
        return 'Sin sesión';
    }
    const actual = (vinculos || []).find((v) => Number(v.idNegocio) === Number(sesionActual.idNegocio));
    const nombre = actual && (actual.nombreNegocio || actual.subdominio)
        ? (actual.nombreNegocio || actual.subdominio)
        : `Negocio ${sesionActual.idNegocio}`;
    return `${nombre} · ${sesionActual.rol}`;
}

function textoVinculo(vinculo) {
    const nombre = vinculo.nombreNegocio || vinculo.subdominio || `Negocio ${vinculo.idNegocio}`;
    const inactivo = vinculo.estado && vinculo.estado !== 'ACTIVA';
    return `${nombre} · ${vinculo.rol}${inactivo ? ` (${vinculo.estado})` : ''}`;
}

async function inicializar() {
    const select = document.getElementById(SELECTOR_ID);
    const etiqueta = document.getElementById(ETIQUETA_ID);
    if (!select) {
        return;
    }
    const actual = sesion.obtener();
    if (!actual || !actual.token) {
        window.location.href = 'login.html';
        return;
    }
    let vinculos = [];
    try {
        vinculos = await api.auth.negocios();
    } catch {
        // sin vínculos o sin conexión: se mantiene el selector básico
    }
    if (etiqueta) {
        etiqueta.textContent = etiquetaNegocio(actual, vinculos);
    }
    try {
        select.querySelectorAll('option[data-vinculo]').forEach((o) => o.remove());
        for (const vinculo of vinculos || []) {
            const option = document.createElement('option');
            option.value = String(vinculo.idNegocio);
            option.dataset.vinculo = '1';
            option.textContent = textoVinculo(vinculo);
            option.disabled = Boolean(vinculo.estado && vinculo.estado !== 'ACTIVA');
            if (Number(vinculo.idNegocio) === Number(actual.idNegocio)) {
                option.selected = true;
            }
            select.appendChild(option);
        }
        const vincular = document.createElement('option');
        vincular.value = VALOR_VINCULAR;
        vincular.dataset.vinculo = '1';
        vincular.textContent = '＋ Vincular otro negocio…';
        select.appendChild(vincular);
    } catch {
        // selector básico
    }

    select.addEventListener('change', async () => {
        const destino = select.value;
        if (!destino) {
            return;
        }
        if (destino === VALOR_VINCULAR) {
            mostrarModalVincular(() => {
                const vigente = sesion.obtener();
                select.value = vigente ? String(vigente.idNegocio) : '';
            });
            const vigente = sesion.obtener();
            select.value = vigente ? String(vigente.idNegocio) : '';
            return;
        }
        try {
            const datos = await api.auth.negocioActivo(Number(destino));
            if (etiqueta) {
                etiqueta.textContent = etiquetaNegocio(datos, vinculos);
            }
            window.location.reload();
        } catch {
            const vigente = sesion.obtener();
            select.value = vigente ? String(vigente.idNegocio) : '';
        }
    });
}

function mostrarModalVincular(alCerrar) {
    if (document.getElementById('vincular-modal')) {
        return;
    }
    const fondo = document.createElement('div');
    fondo.id = 'vincular-modal';
    fondo.className = 'fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4';
    fondo.innerHTML = `
        <div class="w-full max-w-sm bg-carbon-900 border border-carbon-700 rounded-2xl p-6 shadow-2xl">
            <h3 class="text-sm font-bold text-white">Vincular otro negocio</h3>
            <p class="text-[11px] text-slate-400 mt-1">Se crea un acceso CLIENTE a ese negocio con tu cuenta actual.</p>
            <div class="flex flex-col gap-3 mt-4">
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">ID del negocio
                    <input id="vincular-negocio" type="number" min="1" placeholder="Ej.: 2"
                        class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none" />
                </label>
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">ID de cliente (tu perfil en ese negocio)
                    <input id="vincular-cliente" type="number" min="1" placeholder="Ej.: 9"
                        class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none" />
                </label>
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">Confirmá tu contraseña
                    <input id="vincular-clave" type="password" placeholder="••••••••"
                        class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none" />
                </label>
                <p id="vincular-error" class="hidden text-[11px] text-red-400 font-semibold" role="alert"></p>
                <div class="flex gap-2 mt-1">
                    <button id="vincular-cancelar" type="button"
                        class="flex-1 h-10 rounded-xl bg-carbon-800 hover:bg-carbon-750 text-slate-300 text-xs font-bold border border-carbon-700 transition-colors">Cancelar</button>
                    <button id="vincular-confirmar" type="button"
                        class="flex-1 h-10 rounded-xl bg-brand hover:bg-brand-hover text-white text-xs font-bold transition-colors">Vincular</button>
                </div>
            </div>
        </div>`;
    document.body.appendChild(fondo);

    const cerrar = () => {
        fondo.remove();
        if (alCerrar) {
            alCerrar();
        }
    };
    fondo.querySelector('#vincular-cancelar')?.addEventListener('click', cerrar);
    fondo.addEventListener('click', (e) => {
        if (e.target === fondo) {
            cerrar();
        }
    });
    fondo.querySelector('#vincular-confirmar')?.addEventListener('click', async () => {
        const errorEl = fondo.querySelector('#vincular-error');
        errorEl.classList.add('hidden');
        const idNegocio = Number(fondo.querySelector('#vincular-negocio').value);
        const idCliente = Number(fondo.querySelector('#vincular-cliente').value);
        const clave = fondo.querySelector('#vincular-clave').value;
        if (!idNegocio || !idCliente || !clave) {
            errorEl.textContent = 'Completá todos los campos.';
            errorEl.classList.remove('hidden');
            return;
        }
        try {
            await api.auth.vincular(idNegocio, idCliente, clave);
            showToast('Negocio vinculado correctamente.', 'success');
            cerrar();
            window.location.reload();
        } catch (error) {
            errorEl.textContent = error?.message || 'No se pudo vincular el negocio.';
            errorEl.classList.remove('hidden');
        }
    });
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', inicializar);
} else {
    inicializar();
}
