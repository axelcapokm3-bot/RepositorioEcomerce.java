function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const styles = {
        success: {
            color: 'text-emerald-400',
            border: 'border-emerald-500/30',
            icon: 'verified'
        },
        error: {
            color: 'text-rose-400',
            border: 'border-rose-500/30',
            icon: 'error'
        },
        info: {
            color: 'text-sky-400',
            border: 'border-sky-500/30',
            icon: 'info'
        }
    };
    const style = styles[type] || styles.success;
    const toast = document.createElement('div');
    toast.className = `flex items-center gap-3 px-4 py-3 rounded-xl shadow-2xl border text-xs font-medium transition-all transform translate-y-0 opacity-100 bg-carbon-800 ${style.color} ${style.border}`;
    toast.innerHTML = `
        <span class="material-symbols-outlined text-[18px]">${style.icon}</span>
        <span>${message}</span>
    `;

    container.appendChild(toast);
    setTimeout(() => {
        toast.classList.add('opacity-0', 'translate-y-2');
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

export { showToast };
