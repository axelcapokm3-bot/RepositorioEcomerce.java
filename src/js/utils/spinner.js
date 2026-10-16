function showSpinner() {
    let spinner = document.getElementById('loading-spinner');
    if (!spinner) {
        spinner = document.createElement('div');
        spinner.id = 'loading-spinner';
        spinner.className = 'fixed inset-0 bg-carbon-950/70 z-50 flex items-center justify-center backdrop-blur-xs transition-opacity duration-200';
        spinner.innerHTML = `
            <div class="p-5 rounded-2xl bg-carbon-900 border border-carbon-700 shadow-2xl flex flex-col items-center gap-3">
                <div class="w-10 h-10 border-3 border-brand border-t-transparent rounded-full animate-spin"></div>
                <span class="text-xs font-semibold text-slate-300 tracking-wide">Procesando solicitud...</span>
            </div>
        `;
        document.body.appendChild(spinner);
    }
    spinner.style.display = 'flex';
}

function hideSpinner() {
    const spinner = document.getElementById('loading-spinner');
    if (spinner) spinner.style.display = 'none';
}

export { showSpinner, hideSpinner };
