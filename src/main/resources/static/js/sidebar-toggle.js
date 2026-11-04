document.addEventListener('DOMContentLoaded', () => {
  const shell = document.querySelector('.app-shell');
  const toggle = document.querySelector('[data-sidebar-toggle]');
  if (!shell || !toggle) return;

  // 860px 이하(폰·세로 태블릿)에서는 사이드바가 서랍이 되므로 아이콘 모드를 적용하지 않는다(저장값은 그대로 둔다).
  const mobile = window.matchMedia('(max-width: 860px)');
  const storageKey = 'moa.sidebar.collapsed';
  const setCollapsed = (collapsed) => {
    const applied = collapsed && !mobile.matches;
    shell.classList.toggle('sidebar-collapsed', applied);
    toggle.textContent = collapsed ? '+' : '-';
    toggle.setAttribute('aria-expanded', String(!collapsed));
    toggle.setAttribute('aria-label', collapsed ? '메뉴 펼치기' : '메뉴 접기');
    toggle.setAttribute('title', collapsed ? '메뉴 펼치기' : '메뉴 접기');
    localStorage.setItem(storageKey, String(collapsed));
    // 아이콘 모드에서는 접이식 그룹을 강제로 펼친다. 닫힌 <details>는 자식을 UA 스타일로
    // 숨기므로 CSS로 되살릴 수 없고, 그대로 두면 개인 메뉴 아이콘이 통째로 사라진다.
    document.querySelectorAll('.nav-collapsible').forEach((group) => {
      if (applied) {
        if (group.dataset.wasOpen === undefined) group.dataset.wasOpen = String(group.open);
        group.open = true;
      } else if (group.dataset.wasOpen !== undefined) {
        group.open = group.dataset.wasOpen === 'true';
        delete group.dataset.wasOpen;
      }
    });
  };

  setCollapsed(localStorage.getItem(storageKey) === 'true');
  toggle.addEventListener('click', () => setCollapsed(localStorage.getItem(storageKey) !== 'true'));

  // 폰 서랍: 탭 바 '메뉴'로 열고, 닫기 버튼·가림막·Esc로 닫는다. 닫으면 연 버튼으로 초점을 돌려준다.
  const openers = document.querySelectorAll('[data-drawer-open]');
  let returnFocus = null;
  const setDrawer = (open) => {
    document.body.classList.toggle('drawer-open', open);
    openers.forEach((button) => button.setAttribute('aria-expanded', String(open)));
    if (open) {
      returnFocus = document.activeElement;
      const close = document.querySelector('.drawer-close');
      if (close) setTimeout(() => close.focus(), 40);
    } else if (returnFocus) {
      returnFocus.focus();
      returnFocus = null;
    }
  };
  openers.forEach((button) => button.addEventListener('click', () => setDrawer(true)));
  document.querySelectorAll('[data-drawer-close]').forEach((el) => el.addEventListener('click', () => setDrawer(false)));
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && document.body.classList.contains('drawer-open')) setDrawer(false);
  });
  mobile.addEventListener('change', () => {
    if (!mobile.matches) setDrawer(false);
    setCollapsed(localStorage.getItem(storageKey) === 'true');
  });
});
