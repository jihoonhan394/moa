// 위키 문서 보기 — AI 요약(Tier 1). 버튼 클릭 시 CSRF 헤더를 실어 POST하고 응답 텍스트를 패널에 표시.
(function () {
  var btn = document.getElementById('aiSummaryBtn');
  if (!btn) { return; }
  var panel = document.getElementById('aiSummaryPanel');
  var out = document.getElementById('aiSummaryText');
  var tokenMeta = document.querySelector('meta[name="_csrf"]');
  var headerMeta = document.querySelector('meta[name="_csrf_header"]');
  btn.addEventListener('click', function () {
    var label = btn.textContent;
    btn.disabled = true;
    btn.textContent = '요약 중…';
    panel.style.display = '';
    out.textContent = '생성 중입니다…';
    var headers = {};
    if (tokenMeta && headerMeta && headerMeta.getAttribute('content')) {
      headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
    fetch(btn.getAttribute('data-url'), { method: 'POST', headers: headers })
      .then(function (r) { return r.text(); })
      .then(function (t) { out.textContent = t; })
      .catch(function () { out.textContent = '요약 요청에 실패했습니다.'; })
      .finally(function () { btn.disabled = false; btn.textContent = label; });
  });
})();
