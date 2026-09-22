document.addEventListener('DOMContentLoaded', () => {
  const userPanel = document.querySelector('[data-preview-panel="users"] .panel');
  if (!userPanel || userPanel.querySelector('[data-org-demo]')) return;
  const people = [
    ['개발팀', '김개발', 'kimdev@example.com', '활성'],
    ['개발팀', '최프론트', 'front@example.com', '활성'],
    ['인프라운영팀', '이운영', 'ops@example.com', '활성'],
    ['인프라운영팀', '박서버', 'server@example.com', '비활성'],
    ['정보보안팀', '정보안', 'security@example.com', '활성']
  ];
  const table = userPanel.querySelector('.data-table');
  if (!table) return;
  const layout = document.createElement('div'); layout.className = 'org-user-layout'; layout.dataset.orgDemo = 'true';
  const tree = document.createElement('aside'); tree.className = 'org-tree';
  const treeTitle = document.createElement('p'); treeTitle.className = 'eyebrow'; treeTitle.textContent = '사용자 조직도'; tree.append(treeTitle);
  const search = document.createElement('input'); search.type = 'search'; search.placeholder = '그룹, 이름, 이메일 검색'; search.className = 'org-search'; tree.append(search);
  const listPane = document.createElement('div'); listPane.className = 'org-user-list';
  const render = (group = '전체', query = '') => {
    table.replaceChildren();
    const head = document.createElement('div'); head.className = 'table-row table-head'; ['그룹', '이름', '이메일', '상태', '작업'].forEach((text) => { const span = document.createElement('span'); span.textContent = text; head.append(span); }); table.append(head);
    people.filter((person) => (group === '전체' || person[0] === group) && person.join(' ').toLowerCase().includes(query.toLowerCase())).forEach((person) => { const row = document.createElement('div'); row.className = 'table-row'; [person[0], person[1], person[2]].forEach((text, index) => { const cell = index === 1 ? document.createElement('strong') : document.createElement('span'); cell.textContent = text; row.append(cell); }); const status = document.createElement('b'); status.className = person[3] === '활성' ? 'good' : 'warn'; status.textContent = person[3]; const action = document.createElement('span'); action.className = 'table-actions'; action.textContent = '관리'; row.append(status, action); table.append(row); });
  };
  const addGroup = (name, count) => { const button = document.createElement('button'); button.type = 'button'; button.className = 'org-group'; button.textContent = `+ ${name} (${count})`; let open = false; button.addEventListener('click', () => { open = !open; button.textContent = `${open ? '−' : '+'} ${name} (${count})`; render(name, search.value); }); tree.append(button); };
  addGroup('전체', people.length); addGroup('개발팀', 2); addGroup('인프라운영팀', 2); addGroup('정보보안팀', 1);
  search.addEventListener('input', () => render('전체', search.value));
  userPanel.insertBefore(layout, table); listPane.append(table); layout.append(tree, listPane); render();
});
