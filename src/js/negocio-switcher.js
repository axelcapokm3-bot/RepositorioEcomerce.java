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

function subdominioDeSesion(vinculos) {
    const actual = sesion.obtener();
    if (!actual) {
        return '';
    }
    const vinculo = (vinculos || []).find(
        (v) => Number(v.idNegocio) === Number(actual.idNegocio)
    );
    return vinculo && vinculo.subdominio ? vinculo.subdominio : '';
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
            option.value = vinculo.subdominio || '';
            option.dataset.vinculo = '1';
            option.textContent = textoVinculo(vinculo);
            option.disabled = !vinculo.subdominio
                || Boolean(vinculo.estado && vinculo.estado !== 'ACTIVA');
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
                select.value = subdominioDeSesion(vinculos);
            });
            select.value = subdominioDeSesion(vinculos);
            return;
        }
        try {
            const datos = await api.auth.negocioActivo(destino);
            if (etiqueta) {
                etiqueta.textContent = etiquetaNegocio(datos, vinculos);
            }
            window.location.reload();
        } catch {
            select.value = subdominioDeSesion(vinculos);
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
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">Subdominio del negocio
                    <input id="vincular-negocio" type="text" placeholder="Ej.: dos"
                        class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none" />
                </label>
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">DNI del titular
                    <input id="vincular-dni" type="text" inputmode="numeric" placeholder="Ej.: 30123456"
                        class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none" />
                </label>
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">Nombre del titular
                    <input id="vincular-nombre" type="text" placeholder="Ej.: Juan"
                        class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none" />
                </label>
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">Apellido del titular
                    <input id="vincular-apellido" type="text" placeholder="Ej.: Pérez"
                        class="h-10 px-3 bg-carbon-800 border border-carbon-700 text-white rounded-xl text-xs focus:border-brand focus:outline-none" />
                </label>
                <label class="flex flex-col gap-1 text-[11px] font-semibold text-slate-300">Fecha de nacimiento del titular
                    <input id="vincular-fecha" type="date"
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
        const subdominio = fondo.querySelector('#vincular-negocio').value.trim().toLowerCase();
        const dniCliente = fondo.querySelector('#vincular-dni').value.trim();
        const nombreCliente = fondo.querySelector('#vincular-nombre').value.trim();
        const apellidoCliente = fondo.querySelector('#vincular-apellido').value.trim();
        const fechaNacimientoCliente = fondo.querySelector('#vincular-fecha').value;
        const clave = fondo.querySelector('#vincular-clave').value;
        if (!subdominio || !dniCliente || !nombreCliente || !apellidoCliente
                || !fechaNacimientoCliente || !clave) {
            errorEl.textContent = 'Completá todos los campos.';
            errorEl.classList.remove('hidden');
            return;
        }
        try {
            await api.auth.vincular(subdominio, {
                dniCliente,
                nombreCliente,
                apellidoCliente,
                fechaNacimientoCliente
            }, clave);
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
