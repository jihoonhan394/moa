document.addEventListener('DOMContentLoaded', () => {
  const tree = document.querySelector('.org-tree');
  const rows = [...document.querySelectorAll('[data-user-group-row]')];
  const searchField = document.querySelector('[data-user-search-field]');
  const searchInput = document.querySelector('[data-user-search]');
  const searchButton = document.querySelector('[data-user-search-button]');
  const title = document.querySelector('[data-user-list-title]');
  const count = document.querySelector('[data-user-list-count]');
  const modal = document.querySelector('#user-management-modal');
  const deleteModal = document.querySelector('#user-delete-modal');
  if (!tree || !rows.length || !searchField || !searchInput || !searchButton || !title || !count) return;

  const labels = { all: '전체 사용자', management: '관리본부 사용자', development: '개발팀 사용자', infrastructure: '인프라운영팀 사용자', security: '정보보안팀 사용자' };
  let selectedGroup = 'all';

  const filter = () => {
    const query = searchInput.value.trim().toLowerCase();
    const field = searchField.value;
    let visibleCount = 0;
    rows.forEach((row) => {
      const data = { group: row.children[0].textContent, name: row.dataset.userName, email: row.dataset.userEmail };
      const searchTarget = field === 'all' ? Object.values(data).join(' ') : data[field];
      const groupMatched = selectedGroup === 'all' || selectedGroup === 'management' || row.dataset.userGroupRow === selectedGroup;
      const visible = groupMatched && searchTarget.toLowerCase().includes(query);
      row.hidden = !visible;
      row.classList.toggle('user-row-hidden', !visible);
      if (visible) visibleCount += 1;
    });
    title.textContent = labels[selectedGroup];
    count.textContent = `${visibleCount}명`;
  };

  tree.querySelectorAll('[data-org-toggle]').forEach((toggle) => toggle.addEventListener('click', () => {
    const item = toggle.closest('.org-tree-item');
    const children = item?.querySelector(':scope > .org-tree-children');
    if (!children) return;
    const collapsed = children.hidden = !children.hidden;
    toggle.textContent = collapsed ? '+' : '−';
    toggle.setAttribute('aria-label', collapsed ? '조직 펼치기' : '조직 접기');
  }));
  tree.querySelectorAll('[data-user-group]').forEach((node) => node.addEventListener('click', () => {
    selectedGroup = node.dataset.userGroup;
    tree.querySelectorAll('[data-user-group]').forEach((item) => item.classList.toggle('active', item === node));
    filter();
  }));
  searchButton.addEventListener('click', filter);
  searchInput.addEventListener('keydown', (event) => { if (event.key === 'Enter') { event.preventDefault(); filter(); } });

  const openModal = (mode, row) => {
    if (!modal) return;
    const name = row?.dataset.userName || '';
    const heading = modal.querySelector('[data-user-modal-title]');
    const nameInput = modal.querySelector('[data-user-modal-name]');
    if (heading) heading.textContent = mode === 'edit' ? `${name} 사용자 관리` : '사용자 등록';
    if (nameInput) nameInput.value = name;
    modal.showModal();
  };
  rows.forEach((row) => {
    const actions = row.querySelector('.table-actions');
    if (!actions) return;
    const manage = document.createElement('button'); manage.type = 'button'; manage.className = 'table-action-button'; manage.textContent = '관리'; manage.addEventListener('click', () => openModal('edit', row));
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'table-action-button danger-action'; remove.textContent = '삭제'; remove.addEventListener('click', () => deleteModal?.showModal());
    actions.append(manage, remove);
  });
  document.querySelector('[data-user-modal="create"]')?.addEventListener('click', () => openModal('create'));
  document.querySelectorAll('[data-user-modal-close], [data-user-modal-save]').forEach((button) => button.addEventListener('click', () => modal?.close()));
  document.querySelectorAll('[data-user-delete-close], [data-user-delete-confirm]').forEach((button) => button.addEventListener('click', () => deleteModal?.close()));
  filter();
});
