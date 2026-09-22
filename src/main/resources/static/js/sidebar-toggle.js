document.addEventListener('DOMContentLoaded', () => {
  const shell = document.querySelector('.app-shell');
  const toggle = document.querySelector('[data-sidebar-toggle]');
  if (!shell || !toggle) return;

  const storageKey = 'moa.sidebar.collapsed';
  const setCollapsed = (collapsed) => {
    shell.classList.toggle('sidebar-collapsed', collapsed);
    toggle.textContent = collapsed ? '+' : '-';
    toggle.setAttribute('aria-expanded', String(!collapsed));
    toggle.setAttribute('aria-label', collapsed ? '메뉴 펼치기' : '메뉴 접기');
    toggle.setAttribute('title', collapsed ? '메뉴 펼치기' : '메뉴 접기');
    localStorage.setItem(storageKey, String(collapsed));
  };

  setCollapsed(localStorage.getItem(storageKey) === 'true');
  toggle.addEventListener('click', () => setCollapsed(!shell.classList.contains('sidebar-collapsed')));
});
