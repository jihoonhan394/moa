document.addEventListener('DOMContentLoaded', () => {
  const filterBar = document.querySelector('[data-physical-asset-filters]');
  const summary = document.querySelector('[data-physical-filter-summary]');
  const rows = [...document.querySelectorAll('[data-physical-category]')];
  if (!filterBar || !summary || !rows.length) return;
  const labels = { all: '전체 실물 자산', it: 'IT 장비', infrastructure: '인프라 장비', vehicle: '차량', external: '외부 설치/납품 자산' };
  filterBar.querySelectorAll('[data-physical-filter]').forEach((button) => button.addEventListener('click', () => {
    const category = button.dataset.physicalFilter;
    let count = 0;
    rows.forEach((row) => { const visible = category === 'all' || row.dataset.physicalCategory === category; row.hidden = !visible; row.classList.toggle('asset-row-hidden', !visible); if (visible) count += 1; });
    filterBar.querySelectorAll('[data-physical-filter]').forEach((item) => item.classList.toggle('active', item === button));
    summary.textContent = `${labels[category]} ${count}개`;
  }));
});
