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
    // 아이콘 모드에서는 접이식 그룹을 강제로 펼친다. 닫힌 <details>는 자식을 UA 스타일로
    // 숨기므로 CSS로 되살릴 수 없고, 그대로 두면 개인 메뉴 아이콘이 통째로 사라진다.
    document.querySelectorAll('.nav-collapsible').forEach((group) => {
      if (collapsed) {
        group.dataset.wasOpen = String(group.open);
        group.open = true;
      } else if (group.dataset.wasOpen !== undefined) {
        group.open = group.dataset.wasOpen === 'true';
        delete group.dataset.wasOpen;
      }
    });
  };

  setCollapsed(localStorage.getItem(storageKey) === 'true');
  toggle.addEventListener('click', () => setCollapsed(!shell.classList.contains('sidebar-collapsed')));
});
