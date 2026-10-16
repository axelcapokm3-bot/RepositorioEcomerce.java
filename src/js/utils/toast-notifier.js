function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    // Toast styles: Pure black (#0A0A0A), vibrant orange border, pure white icon
    const theme = {
        error: {
            icon: 'warning',
            iconColor: 'text-white',
            borderColor: 'border-orange-500/80 shadow-[0_0_24px_rgba(255,107,0,0.25)]',
            accentBar: 'bg-brand',
            title: 'Alerta del Sistema',
            titleColor: 'text-orange-400'
        },
        success: {
            icon: 'check_circle',
            iconColor: 'text-white',
            borderColor: 'border-emerald-500/60 shadow-[0_0_20px_rgba(16,185,129,0.2)]',
            accentBar: 'bg-emerald-400',
            title: 'Operación Exitosa',
            titleColor: 'text-emerald-400'
        },
        info: {
            icon: 'info',
            iconColor: 'text-white',
            borderColor: 'border-sky-500/60 shadow-[0_0_20px_rgba(14,165,233,0.2)]',
            accentBar: 'bg-sky-400',
            title: 'Notificación',
            titleColor: 'text-sky-400'
        }
    };

    const currentTheme = theme[type] || theme.error;

    const toast = document.createElement('div');
    toast.className = `pointer-events-auto flex items-start gap-3.5 p-4 rounded-xl bg-carbon-950/95 border backdrop-blur-xl transition-all duration-300 transform translate-x-8 opacity-0 max-w-sm sm:max-w-md w-full relative overflow-hidden ${currentTheme.borderColor}`;
    
    toast.innerHTML = `
        <div class="absolute left-0 top-0 bottom-0 w-1 ${currentTheme.accentBar}"></div>
        <div class="w-8 h-8 rounded-lg bg-carbon-850 flex items-center justify-center shrink-0 border border-carbon-700/60">
            <span class="material-symbols-outlined text-[19px] ${currentTheme.iconColor}">${currentTheme.icon}</span>
        </div>
        <div class="flex-1 min-w-0 pt-0.5">
            <div class="text-[11px] font-bold uppercase tracking-wider ${currentTheme.titleColor} mb-0.5">${currentTheme.title}</div>
            <div class="text-xs text-slate-100 font-medium leading-relaxed break-words">${message}</div>
        </div>
        <button class="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-carbon-800/60 transition-colors shrink-0" aria-label="Cerrar notificación">
            <span class="material-symbols-outlined text-[16px]">close</span>
        </button>
    `;

    const closeBtn = toast.querySelector('button');
    const dismiss = () => {
        toast.classList.remove('translate-x-0', 'opacity-100');
        toast.classList.add('translate-x-12', 'opacity-0');
        setTimeout(() => toast.remove(), 250);
    };

    if (closeBtn) closeBtn.addEventListener('click', dismiss);

    container.appendChild(toast);

    // Trigger enter animation
    requestAnimationFrame(() => {
        toast.classList.remove('translate-x-8', 'opacity-0');
        toast.classList.add('translate-x-0', 'opacity-100');
    });

    setTimeout(dismiss, 5500);
}

export { showToast };
