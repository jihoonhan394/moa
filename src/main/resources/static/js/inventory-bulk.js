/*
 * 자산 목록의 일괄 작업.
 *
 * 전에는 표의 행마다 사용자·소유팀 드롭다운이 들어 있었다. 목록 HTML 이
 * `행수 × 사용자수` 로 커져서 자산 130건·사용자 33명에 696KB 였다(실측).
 * 드롭다운을 화면에 하나씩만 두고, 체크한 행에 적용한다.
 *
 * JS 가 필요한 이유: 체크박스 하나를 두 폼(배정·소유팀)이 함께 써야 한다.
 * HTML 의 form 속성은 요소를 폼 하나에만 묶을 수 있다.
 *
 * JS 가 죽어도 목록은 읽히고 행별 회수·폐기·삭제는 그대로 동작한다 —
 * 일괄 작업만 안 된다.
 */
(function () {
  'use strict';

  var table = document.querySelector('[data-bulk-scope]');
  if (!table) {
    return;
  }

  function checkedIds() {
    return Array.prototype.slice
      .call(table.querySelectorAll('input[data-bulk-item]:checked'))
      .map(function (box) { return box.value; });
  }

  function countLabel() {
    var label = document.querySelector('[data-bulk-count]');
    if (!label) {
      return;
    }
    var count = checkedIds().length;
    label.textContent = count === 0 ? '선택 없음' : count + '건 선택';
    // 아무것도 안 고르고 누르면 서버가 안내로 돌려보내지만, 누르기 전에 보이는 편이 낫다.
    Array.prototype.forEach.call(
      document.querySelectorAll('[data-bulk-submit]'),
      function (button) { button.disabled = count === 0; }
    );
  }

  table.addEventListener('change', function (event) {
    if (event.target.matches('input[data-bulk-item]')) {
      countLabel();
    }
  });

  var toggleAll = document.querySelector('[data-bulk-all]');
  if (toggleAll) {
    toggleAll.addEventListener('change', function () {
      Array.prototype.forEach.call(
        table.querySelectorAll('input[data-bulk-item]'),
        function (box) { box.checked = toggleAll.checked; }
      );
      countLabel();
    });
  }

  // 제출 직전에 선택을 숨은 필드로 옮긴다. 폼마다 자기 것을 채운다.
  Array.prototype.forEach.call(
    document.querySelectorAll('form[data-bulk-form]'),
    function (form) {
      form.addEventListener('submit', function (event) {
        var ids = checkedIds();
        if (ids.length === 0) {
          event.preventDefault();
          return;
        }
        Array.prototype.forEach.call(
          form.querySelectorAll('input[name="itemIds"]'),
          function (old) { old.parentNode.removeChild(old); }
        );
        ids.forEach(function (id) {
          var hidden = document.createElement('input');
          hidden.type = 'hidden';
          hidden.name = 'itemIds';
          hidden.value = id;
          form.appendChild(hidden);
        });
      });
    }
  );

  countLabel();
})();
