// 위키 에디터 — 마크다운 툴바 + 서버 렌더 라이브 미리보기(저장과 동일 파이프라인 → 미리보기·저장 결과 일치).
(function () {
  var ta = document.getElementById('wikiContent');
  if (!ta) { return; }
  var preview = document.getElementById('wikiPreview');
  var previewBody = preview ? preview.querySelector('.wiki-content') : null;
  var toolbar = document.getElementById('wikiToolbar');
  var previewUrl = ta.getAttribute('data-preview-url') || '/wiki/preview';
  var aiUrl = ta.getAttribute('data-ai-url') || '/wiki/ai-assist';
  var aiStatus = document.getElementById('wikiAiStatus');
  var tokenMeta = document.querySelector('meta[name="_csrf"]');
  var headerMeta = document.querySelector('meta[name="_csrf_header"]');

  function csrfHeaders() {
    var headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (tokenMeta && headerMeta && headerMeta.getAttribute('content')) {
      headers[headerMeta.getAttribute('content')] = tokenMeta.getAttribute('content');
    }
    return headers;
  }

  // ── 툴바: 선택 영역을 마크다운으로 감싸거나 줄 앞에 삽입 ──
  function surround(before, after) {
    var s = ta.selectionStart, e = ta.selectionEnd;
    var sel = ta.value.substring(s, e);
    ta.setRangeText(before + sel + after, s, e, 'end');
    ta.focus();
    schedule();
  }
  function prefixLines(prefix) {
    var s = ta.selectionStart, e = ta.selectionEnd;
    var startLine = ta.value.lastIndexOf('\n', s - 1) + 1;
    var block = ta.value.substring(startLine, e) || '';
    var replaced = block.split('\n').map(function (l) { return prefix + l; }).join('\n');
    ta.setRangeText(replaced, startLine, e, 'end');
    ta.focus();
    schedule();
  }
  function insert(text) {
    ta.setRangeText(text, ta.selectionStart, ta.selectionEnd, 'end');
    ta.focus();
    schedule();
  }
  var actions = {
    bold: function () { surround('**', '**'); },
    italic: function () { surround('*', '*'); },
    h2: function () { prefixLines('## '); },
    ul: function () { prefixLines('- '); },
    quote: function () { prefixLines('> '); },
    code: function () { surround('`', '`'); },
    link: function () { surround('[', '](https://)'); },
    table: function () { insert('\n| 제목1 | 제목2 |\n| --- | --- |\n| 값1 | 값2 |\n'); }
  };
  if (toolbar) {
    toolbar.addEventListener('click', function (ev) {
      var md = ev.target.closest('[data-md]');
      if (md) {
        var fn = actions[md.getAttribute('data-md')];
        if (fn) { fn(); }
        return;
      }
      var ai = ev.target.closest('[data-ai]');
      if (ai) { aiAction(ai.getAttribute('data-ai')); }
    });
  }

  // ── 인라인 저작 AI: 선택 영역(없으면 본문 전체)을 요약/개선/번역/이어쓰기 ──
  var aiBusy = false;
  function aiAction(action) {
    if (aiBusy) { return; }
    var s = ta.selectionStart, e = ta.selectionEnd;
    var hasSel = e > s;
    var rangeStart = hasSel ? s : 0;
    var rangeEnd = hasSel ? e : ta.value.length;
    var input = ta.value.substring(rangeStart, rangeEnd);
    if (!input.trim()) {
      if (aiStatus) { aiStatus.textContent = '내용을 입력하거나 텍스트를 선택하세요.'; }
      return;
    }
    aiBusy = true;
    if (aiStatus) { aiStatus.textContent = 'AI 처리 중…'; }
    fetch(aiUrl, {
      method: 'POST', headers: csrfHeaders(),
      body: 'action=' + encodeURIComponent(action) + '&text=' + encodeURIComponent(input)
    })
      .then(function (r) { return r.text(); })
      .then(function (result) {
        if (action === 'continue') {
          ta.setRangeText('\n' + result, rangeEnd, rangeEnd, 'end');
        } else {
          ta.setRangeText(result, rangeStart, rangeEnd, 'end');
        }
        if (aiStatus) { aiStatus.textContent = ''; }
        ta.focus();
        schedule();
      })
      .catch(function () { if (aiStatus) { aiStatus.textContent = 'AI 요청에 실패했습니다.'; } })
      .finally(function () { aiBusy = false; });
  }

  // ── 라이브 미리보기(서버 렌더, 디바운스) ──
  var timer = null;
  function schedule() {
    if (!previewBody) { return; }
    if (timer) { clearTimeout(timer); }
    timer = setTimeout(render, 400);
  }
  function render() {
    fetch(previewUrl, { method: 'POST', headers: csrfHeaders(), body: 'content=' + encodeURIComponent(ta.value) })
      .then(function (r) { return r.text(); })
      .then(function (html) { previewBody.innerHTML = html; }) // 서버가 새니타이즈한 안전 HTML
      .catch(function () { /* 미리보기 실패는 조용히 무시 */ });
  }
  ta.addEventListener('input', schedule);
  render(); // 최초 1회
})();
