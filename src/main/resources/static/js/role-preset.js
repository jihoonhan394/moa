/*
 * 사용자 폼의 관리 역할 한 번에 켜기/끄기.
 *
 * 작은 회사는 한 사람이 기관 관리자·인프라 관리자·자산 관리자를 다 맡는다.
 * 역할을 하나씩 찾아 체크하는 것이 사용자를 만들 때마다 반복됐다.
 *
 * JS 가 죽어도 체크박스는 그대로 쓸 수 있다 — 버튼만 동작하지 않는다.
 * 그래서 버튼은 type="button" 이고, 폼 제출은 체크박스가 결정한다.
 */
(function () {
  'use strict';

  function boxes() {
    return Array.prototype.slice.call(
      document.querySelectorAll('input[type="checkbox"][name="roles"]')
    );
  }

  function setAll(checked) {
    boxes().forEach(function (box) { box.checked = checked; });
  }

  var all = document.querySelector('[data-roles-all]');
  if (all) {
    all.addEventListener('click', function () { setAll(true); });
  }

  var none = document.querySelector('[data-roles-none]');
  if (none) {
    none.addEventListener('click', function () { setAll(false); });
  }
})();
