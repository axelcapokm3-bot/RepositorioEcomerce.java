function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `flex items-center gap-3 px-4 py-3 rounded-xl shadow-2xl border text-xs font-medium transition-all transform translate-y-0 opacity-100 ${
        type === 'success' ? 'bg-carbon-800 text-emerald-400 border-emerald-500/30' : 'bg-carbon-800 text-rose-400 border-rose-500/30'
    }`;
    toast.innerHTML = `
        <span class="material-symbols-outlined text-[18px]">${type === 'success' ? 'verified' : 'error'}</span>
        <span>${message}</span>
    `;

    container.appendChild(toast);
    setTimeout(() => {
        toast.classList.add('opacity-0', 'translate-y-2');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

export { showToast };

