(() => {
  const applyPlannedAssetCopy = () => {
    const tab = document.querySelector('[data-preview-tab="retired"]');
    if (!tab) return;
    tab.textContent = '폐기 및 만료 예정 자산';
    const panel = document.querySelector('[data-preview-panel="retired"]');
    const samples = [['업무용 노트북 교체 예정', '사용 주기 종료 예정 · 2026-08-31'], ['프린터 관리 라이선스 갱신 예정', '라이선스 만료 예정 · 2026-07-15']];
    panel?.querySelectorAll('.data-table.compact .table-row:not(.table-head)').forEach((row, index) => {
      const sample = samples[index]; if (!sample) return;
      const name = row.querySelector('strong'); const summary = row.querySelector('span:not(.table-actions)');
      if (name) name.textContent = sample[0]; if (summary) summary.textContent = sample[1];
    });
    const copy = panel?.querySelector('.subcopy');
    if (copy) copy.textContent = '담당자 알림과 사전 조치가 필요한 폐기·만료 예정 자산입니다.';
    const container = tab.closest('[data-preview-tabs]');
    if (!container || container.querySelector('[data-preview-tab="disposed"]')) return;
    const disposedTab = document.createElement('button'); disposedTab.type = 'button'; disposedTab.dataset.previewTab = 'disposed'; disposedTab.textContent = '폐기 자산';
    const disposedPanel = document.createElement('section'); disposedPanel.className = 'preview-panel'; disposedPanel.dataset.previewPanel = 'disposed'; disposedPanel.hidden = true;
    const card = document.createElement('div'); card.className = 'panel'; const heading = document.createElement('div'); heading.className = 'panel-head'; const headingCopy = document.createElement('div'); const eyebrow = document.createElement('p'); eyebrow.className = 'eyebrow'; eyebrow.textContent = '폐기 완료 자산'; const h2 = document.createElement('h2'); h2.textContent = '폐기 자산 목록'; const description = document.createElement('p'); description.className = 'subcopy'; description.textContent = '폐기 처리가 완료되어 더 이상 사용하지 않는 자산입니다.'; headingCopy.append(eyebrow, h2, description); heading.append(headingCopy);
    const table = document.createElement('div'); table.className = 'data-table compact'; const head = document.createElement('div'); head.className = 'table-row table-head'; ['자산명', '폐기 완료일', '폐기 사유'].forEach((text) => { const span = document.createElement('span'); span.textContent = text; head.append(span); }); table.append(head);
    [['구형 업무용 모니터', '2026-05-10', '노후 장비 교체 완료'], ['구형 보안 솔루션 라이선스', '2026-04-28', '계약 종료 및 대체 솔루션 전환']].forEach((row) => { const item = document.createElement('div'); item.className = 'table-row'; row.forEach((text, index) => { const cell = index === 0 ? document.createElement('strong') : document.createElement('span'); cell.textContent = text; item.append(cell); }); table.append(item); });
    card.append(heading, table); disposedPanel.append(card); container.querySelector('.console-tabs')?.append(disposedTab); container.append(disposedPanel);
    disposedTab.addEventListener('click', () => { container.querySelectorAll('[data-preview-tab]').forEach((button) => { const selected = button === disposedTab; button.classList.toggle('active', selected); button.setAttribute('aria-selected', String(selected)); }); container.querySelectorAll('[data-preview-panel]').forEach((section) => section.hidden = section !== disposedPanel); });
  };
  const selectPreviewItem = (item) => {
    const group = item.closest('[data-preview-list]');
    if (!group) return;
    group.querySelectorAll('[data-preview-select]').forEach((entry) => entry.classList.remove('active'));
    item.classList.add('active');
    const scope = item.closest('[data-preview-panel]') || document;
    const detail = scope.querySelector(`[data-preview-detail="${item.dataset.previewSelect}"]`);
    scope.querySelectorAll('[data-preview-detail]').forEach((entry) => entry.hidden = true);
    if (detail) detail.hidden = false;
  };

  const activate = (container, target) => {
    container.querySelectorAll('[data-preview-tab]').forEach((tab) => {
      const selected = tab.dataset.previewTab === target;
      tab.classList.toggle('active', selected);
      tab.setAttribute('aria-selected', String(selected));
    });
    container.querySelectorAll('[data-preview-panel]').forEach((panel) => {
      panel.hidden = panel.dataset.previewPanel !== target;
    });
    const activePanel = container.querySelector(`[data-preview-panel="${target}"]`);
    const firstItem = activePanel?.querySelector('[data-preview-list] [data-preview-select]');
    if (firstItem) selectPreviewItem(firstItem);
  };

  document.querySelectorAll('[data-preview-tabs]').forEach((container) => {
    const initial = container.dataset.previewInitial;
    if (initial) activate(container, initial);
    container.querySelectorAll('[data-preview-tab]').forEach((tab) => {
      tab.addEventListener('click', () => activate(container, tab.dataset.previewTab));
    });
  });

  document.querySelectorAll('[data-preview-select]').forEach((item) => {
    item.addEventListener('click', () => selectPreviewItem(item));
  });

  document.querySelectorAll('[data-server-filter-tabs]').forEach((tabs) => {
    const list = tabs.closest('.panel')?.querySelector('[data-server-filter-list]');
    tabs.querySelectorAll('[data-server-filter]').forEach((tab) => {
      tab.addEventListener('click', () => {
        const filter = tab.dataset.serverFilter;
        tabs.querySelectorAll('[data-server-filter]').forEach((entry) => entry.classList.toggle('active', entry === tab));
        const visible = [];
        list?.querySelectorAll('[data-server-kind]').forEach((item) => {
          const show = filter === 'all' || item.dataset.serverKind === filter;
          item.hidden = !show;
          item.style.display = show ? '' : 'none';
          if (show) visible.push(item);
        });
        if (visible[0]) selectPreviewItem(visible[0]);
      });
    });
  });

  const serverList = document.querySelector('[data-server-filter-list]');
  if (serverList) {
    const detailScript = document.createElement('script');
    detailScript.src = '/js/server-detail-demo.js';
    document.head.append(detailScript);
    const historySamples = {
      '운영-웹-01': ['MTCM', '2026-06-23 10:11', '2026-06-23 13:00', 'administrator', '서버 포트 변경'],
      '운영-DB-01': ['김개발', '2026-06-23 09:32', '2026-06-23 10:05', 'administrator', '접속 계정 추가'],
      '업무-윈도우-01': ['이운영', '2026-06-22 15:20', '2026-06-22 16:02', '김관리', 'RDP 연결 설정 변경'],
      '업무용 웹 포털': ['박지원', '2026-06-23 08:50', '2026-06-23 09:14', 'administrator', 'HTTPS 인증서 갱신']
    };
    const makeList = (rows) => {
      const list = document.createElement('dl'); list.className = 'detail-list';
      rows.forEach(([term, value]) => { const row = document.createElement('div'); const dt = document.createElement('dt'); dt.textContent = term; const dd = document.createElement('dd'); dd.textContent = value; row.append(dt, dd); list.append(row); });
      return list;
    };
    document.querySelectorAll('.detail-panel [data-preview-detail]').forEach((detail) => {
      if (detail.querySelector('[data-server-detail-tabs]')) return;
      const name = detail.querySelector('h2')?.textContent.trim() || '운영-웹-01';
      const sample = historySamples[name] || historySamples['운영-웹-01'];
      const tabs = document.createElement('div'); tabs.className = 'console-tabs compact-tabs'; tabs.dataset.serverDetailTabs = 'true';
      const info = document.createElement('div');
      const history = document.createElement('div'); history.hidden = true;
      const admin = document.createElement('div'); admin.hidden = true;
      const detailList = detail.querySelector('.detail-list'); const actions = detail.querySelector('.inline-actions');
      if (detailList) info.append(detailList);
      history.append(makeList([['접속 사용자', sample[0]], ['접속 시간', sample[1]], ['접속 해지 시간', sample[2]]]));
      admin.append(makeList([['변경 사용자', sample[3]], ['변경 이력', sample[4]], ['변경 시간', '2026-06-22 16:24']]));
      const panels = [info, history, admin];
      ['서버 정보', '마지막 접속 이력', '관리자 변경 이력'].forEach((label, index) => { const button = document.createElement('button'); button.type = 'button'; button.textContent = label; if (index === 0) button.classList.add('active'); button.addEventListener('click', () => { tabs.querySelectorAll('button').forEach((entry) => entry.classList.toggle('active', entry === button)); panels.forEach((panel, panelIndex) => panel.hidden = panelIndex !== index); }); tabs.append(button); });
      detail.append(tabs, info, history, admin);
      if (actions) detail.append(actions);
    });
  }
  const retiredTab = document.querySelector('[data-preview-tab="retired"]');
  if (retiredTab) retiredTab.textContent = '폐기 및 만료 예정 자산';

  document.querySelectorAll('.detail-panel [data-preview-detail]').forEach((detail) => {
    let tabs = detail.querySelector('.compact-tabs');
    if (!tabs) {
      tabs = document.createElement('div');
      tabs.className = 'console-tabs compact-tabs';
      ['서버 정보', '마지막 접속 이력', '관리자 변경 이력'].forEach((label, index) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = label;
        if (index === 0) button.classList.add('active');
        tabs.append(button);
      });
      const details = detail.querySelector('.detail-list');
      if (details) detail.insertBefore(tabs, details);
    }
    tabs.querySelectorAll('button').forEach((button) => {
      if (button.textContent.trim() === '접속 이력') button.textContent = '마지막 접속 이력';
    });
  });

  document.querySelectorAll('[data-modal-open]').forEach((button) => {
    button.addEventListener('click', () => document.getElementById(button.dataset.modalOpen)?.showModal());
  });
  document.querySelectorAll('[data-modal-close]').forEach((button) => {
    button.addEventListener('click', () => button.closest('dialog')?.close());
  });
  const createModal = document.getElementById('create-asset-modal') || document.getElementById('create-user-modal');
  if (createModal) {
    document.querySelectorAll('a.button.primary[href$="/new"]').forEach((link) => {
      link.addEventListener('click', (event) => { event.preventDefault(); createModal.showModal(); });
    });
  }
  document.querySelectorAll('[data-demo-modal]').forEach((button) => {
    button.disabled = false;
    button.addEventListener('click', () => document.getElementById(button.dataset.demoModal)?.showModal());
  });
  const userModal = document.getElementById('user-registration-modal');
  if (userModal) {
    const userButtons = document.querySelectorAll('[data-demo-modal="user-registration-modal"]');
    if (userButtons.length > 1) userButtons[0].hidden = true;
    const grid = userModal.querySelector('.modal-form-grid');
    if (grid && !userModal.querySelector('[data-temporary-user]')) {
      const temporary = document.createElement('label');
      temporary.dataset.temporaryUser = 'true';
      const check = document.createElement('input');
      check.type = 'checkbox';
      temporary.append(check, document.createTextNode(' 임시 사용자로 등록'));
      const expiry = document.createElement('label');
      expiry.hidden = true;
      expiry.textContent = '만료 날짜';
      const date = document.createElement('input');
      date.type = 'date';
      date.disabled = true;
      expiry.append(date);
      check.addEventListener('change', () => { expiry.hidden = !check.checked; date.disabled = !check.checked; });
      grid.append(temporary, expiry);
    }
  }
  if (document.querySelector('[data-preview-panel="users"]')) { const orgScript = document.createElement('script'); orgScript.src = '/js/user-org-demo.js'; document.head.append(orgScript); }
  document.querySelectorAll('[data-user-edit]').forEach((button) => {
    button.addEventListener('click', () => {
      const modal = document.getElementById('user-registration-modal');
      if (!modal) return;
      const title = modal.querySelector('[data-modal-title]');
      if (title) title.textContent = '사용자 정보 수정';
      ['name', 'username', 'email', 'status'].forEach((field) => {
        const input = modal.querySelector(`[data-user-field="${field}"]`);
        if (input) input.value = button.dataset[field] || '';
      });
      modal.showModal();
    });
  });
  const showDemoResult = (message) => {
    const toast = document.createElement('div');
    toast.className = 'demo-toast';
    toast.textContent = message;
    document.body.append(toast);
    window.setTimeout(() => toast.remove(), 2400);
  };
  document.querySelectorAll('.app-modal a.button.primary[href$="/new"]').forEach((link) => {
    link.textContent = '등록';
    link.addEventListener('click', (event) => {
      event.preventDefault();
      link.closest('dialog')?.close();
      showDemoResult('데모 등록이 완료되었습니다. 실제 저장 기능은 추후 연결됩니다.');
    });
  });
  document.querySelectorAll('[data-demo-register]').forEach((button) => {
    button.addEventListener('click', () => {
      button.closest('dialog')?.close();
      showDemoResult('데모 등록이 완료되었습니다. 실제 저장 기능은 추후 연결됩니다.');
    });
  });
  document.querySelectorAll('[data-email-send]').forEach((button) => {
    button.addEventListener('click', () => {
      const modal = button.closest('dialog');
      button.textContent = '인증 메일이 발송되었습니다.';
      button.disabled = true;
      if (modal?.querySelector('[data-verification-code]')) return;
      const label = document.createElement('label');
      label.dataset.verificationCode = 'true';
      label.textContent = '인증번호';
      const input = document.createElement('input');
      input.type = 'text';
      input.inputMode = 'numeric';
      input.maxLength = 6;
      input.placeholder = '이메일로 받은 6자리 번호 입력';
      label.append(input);
      button.closest('label')?.after(label);
    });
  });
  document.querySelectorAll('[data-asset-edit]').forEach((button) => {
    button.addEventListener('click', () => {
      const modal = document.getElementById('asset-manage-modal');
      if (!modal) return;
      const name = button.dataset.assetName || '자산';
      modal.querySelector('[data-asset-modal-title]').textContent = `${name} 수정`;
      const input = modal.querySelector('[data-asset-name]');
      if (input) input.value = name;
      modal.showModal();
    });
  });
  document.querySelectorAll('[data-asset-delete]').forEach((button) => {
    button.addEventListener('click', () => {
      const modal = document.getElementById('asset-delete-modal');
      if (!modal) return;
      modal.querySelector('[data-delete-name]').textContent = button.dataset.assetName || '선택한 자산';
      modal.showModal();
    });
  });
  const assetSamples = {
    physical: [
      ['업무용 노트북 A', [['브랜드', '삼성전자'], ['모델명', '갤럭시 북 프로'], ['MAC 주소', '00:1A:2B:3C:4D:5E'], ['구매일', '2025-03-20'], ['담당자', '김개발']]],
      ['업무용 휴대폰 B', [['브랜드', '삼성전자'], ['모델명', '갤럭시 S24'], ['MAC 주소', '10:2B:3C:4D:5E:6F'], ['구매일', '2025-01-10'], ['담당자', '이운영']]],
      ['업무 차량 C', [['차량 번호', '12가 3456'], ['차종', '중형 승용'], ['소유 형태', '리스'], ['주행거리', '18,420 km'], ['보험 만료일', '2027-02-28'], ['담당자', '경영지원팀']]],
      ['고객사 납품 방화벽-01', [['자산 분류', '인프라 장비 · 외부 설치'], ['모델명', 'SecureGate X500'], ['설치처', '에이치아이 물류센터'], ['납품일', '2026-04-14'], ['검수일', '2026-04-16'], ['보증 만료일', '2029-04-13'], ['소유 형태', '자사 소유'], ['유지보수 담당자', '인프라운영팀']]],
      ['지사 임차 UPS-01', [['자산 분류', '인프라 장비 · UPS'], ['모델명', 'PowerGuard 3000'], ['설치 위치', '부산 지사 전산실'], ['소유 형태', '임차'], ['계약 만료일', '2027-12-31'], ['담당자', '자산관리팀']]]
    ],
    software: [
      ['윈도우 서버 라이선스', [['소프트웨어 이름', '윈도우 서버 2022'], ['라이선스 번호', 'DEMO-WS22-001'], ['만료 기간', '영구 라이선스'], ['라이선스 비용', '₩3,200,000'], ['담당자', '인프라운영팀']]],
      ['오피스 구독', [['소프트웨어 이름', '오피스 구독'], ['라이선스 번호', 'DEMO-OFFICE-185'], ['만료 기간', '2027-01-14'], ['라이선스 비용', '연 ₩5,550,000'], ['담당자', '경영지원팀']]]
    ],
    retired: [
      ['구형 업무용 노트북', [['브랜드', 'LG전자'], ['모델명', '그램 15'], ['폐기 사유', '사용 주기 종료'], ['폐기 예정일', '2026-05-31'], ['담당자', '자산관리팀']]],
      ['구형 프린터 관리 라이선스', [['소프트웨어 이름', '프린터 관리 솔루션'], ['라이선스 번호', 'DEMO-PRINT-OLD'], ['폐기 사유', '라이선스 만료'], ['만료일', '2026-06-01'], ['담당자', '인프라운영팀']]]
    ]
  };
  const detailModal = document.createElement('dialog');
  detailModal.className = 'app-modal';
  document.body.append(detailModal);
  const openAssetDetail = (title, rows) => {
    detailModal.replaceChildren();
    const form = document.createElement('form'); form.className = 'modal-body'; form.method = 'dialog';
    const header = document.createElement('div'); header.className = 'modal-header';
    const heading = document.createElement('div'); const eyebrow = document.createElement('p'); eyebrow.className = 'eyebrow'; eyebrow.textContent = '자산 상세'; const h2 = document.createElement('h2'); h2.textContent = title; heading.append(eyebrow, h2);
    const close = document.createElement('button'); close.type = 'button'; close.textContent = '×'; close.addEventListener('click', () => detailModal.close()); header.append(heading, close);
    const list = document.createElement('dl'); list.className = 'detail-list'; rows.forEach(([key, value]) => { const row = document.createElement('div'); const dt = document.createElement('dt'); dt.textContent = key; const dd = document.createElement('dd'); dd.textContent = value; row.append(dt, dd); list.append(row); });
    const footer = document.createElement('div'); footer.className = 'modal-footer'; const done = document.createElement('button'); done.type = 'button'; done.textContent = '확인'; done.className = 'button primary'; done.addEventListener('click', () => detailModal.close()); footer.append(done); form.append(header, list, footer); detailModal.append(form); detailModal.showModal();
  };
  document.querySelectorAll('[data-preview-panel]').forEach((panel) => {
    const sampleRows = assetSamples[panel.dataset.previewPanel];
    if (!sampleRows) return;
    panel.querySelectorAll('.data-table.compact .table-row:not(.table-head)').forEach((row, index) => {
      row.classList.add('asset-detail-row');
      row.addEventListener('click', (event) => {
        if (event.target.closest('button, a, form')) return;
        const sample = sampleRows[index];
        if (sample) openAssetDetail(sample[0], sample[1]);
      });
    });
  });
  applyPlannedAssetCopy();
})();
