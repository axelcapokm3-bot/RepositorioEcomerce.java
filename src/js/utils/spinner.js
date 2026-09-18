function showSpinner() {
    let spinner = document.getElementById('loading-spinner');
    if (!spinner) {
        spinner = document.createElement('div');
        spinner.id = 'loading-spinner';
        spinner.className = 'fixed inset-0 bg-black/60 z-50 flex items-center justify-center backdrop-blur-xs';
        spinner.innerHTML = `<div class="w-10 h-10 border-4 border-brand border-t-transparent rounded-full animate-spin"></div>`;
        document.body.appendChild(spinner);
    }
    spinner.style.display = 'flex';
}

function hideSpinner() {
    const spinner = document.getElementById('loading-spinner');
    if (spinner) spinner.style.display = 'none';
}

export { showSpinner, hideSpinner };
