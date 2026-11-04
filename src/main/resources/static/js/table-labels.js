// 폰(600px 이하)에서 표 행은 카드가 된다(gyeoljae.css). 머리 행이 숨으므로
// 각 칸에 머리 행의 이름을 data-label로 붙여 두면 CSS가 칸 앞에 이름표로 보여 준다.
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.data-table').forEach((table) => {
    const head = table.querySelector(':scope > .table-head');
    if (!head) return;
    const labels = [...head.children].map((cell) => cell.textContent.trim());
    table.querySelectorAll(':scope > .table-row:not(.table-head)').forEach((row) => {
      [...row.children].forEach((cell, i) => {
        if (labels[i] && !cell.hasAttribute('data-label')) cell.setAttribute('data-label', labels[i]);
      });
    });
  });
});
