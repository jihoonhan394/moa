document.addEventListener('DOMContentLoaded', () => {
  const instanceContainer = document.querySelector('[data-solution-instances]');
  const topology = document.querySelector('[data-solution-topology]');
  const eventList = document.querySelector('[data-solution-events]');
  const logDialog = document.querySelector('#solution-log-modal');
  const profileDialog = document.querySelector('#instance-profile-modal');
  const logTitle = document.querySelector('[data-solution-log-title]');
  const logView = document.querySelector('[data-solution-log-view]');
  if (!instanceContainer || !topology || !eventList || !logDialog || !profileDialog || !logTitle || !logView) return;

  const groups = {
    portal: {
      title: '업무포털 웹 서비스',
      subtitle: '내부 업무 포털의 고가용성 웹 서비스',
      status: '정상',
      available: '3 / 3',
      response: '124 ms',
      error: '0.02%',
      traffic: '184 req/s',
      check: '방금 전',
      topology: '고가용성 구성',
      nodes: [['접속 지점', 'VIP / Load Balancer', '정상'], ['웹 계층', '운영-웹-01 · 운영-웹-02', '정상'], ['WAS 계층', 'Tomcat-A · Tomcat-B', '정상']],
      instances: [['로드밸런서', 'LB-01', '10.10.0.10', 'v2.4.58', '정상'], ['WAS Active', 'Tomcat-A', '운영-웹-01:8080', 'v9.0.89', '정상'], ['WAS Active', 'Tomcat-B', '운영-웹-02:8080', 'v9.0.89', '정상']],
      events: [['online', '10:11 헬스체크가 전체 인스턴스에서 성공했습니다.'], ['online', '09:46 Tomcat-B 배포 버전이 정상으로 확인되었습니다.'], ['idle', '오늘 23:00 정기 점검 창이 예정되어 있습니다.']]
    },
    groupware: {
      title: '그룹웨어 서비스',
      subtitle: '사내 협업 시스템의 웹·WAS 이중화 구성',
      status: '주의',
      available: '1 / 2',
      response: '286 ms',
      error: '0.34%',
      traffic: '62 req/s',
      check: '1분 전',
      topology: '이중화 구성 · 인스턴스 1대 점검',
      nodes: [['접속 지점', '그룹웨어 도메인', '정상'], ['웹 계층', 'GW-Web-01', '정상'], ['WAS 계층', 'GW-WAS-01 · GW-WAS-02', '주의']],
      instances: [['웹 Active', 'GW-Web-01', '10.10.3.11:443', 'v1.8.3', '정상'], ['WAS Active', 'GW-WAS-01', '10.10.3.21:8080', 'v1.8.3', '정상'], ['WAS Standby', 'GW-WAS-02', '10.10.3.22:8080', 'v1.8.3', '점검']],
      events: [['idle', '10:02 GW-WAS-02가 계획 점검 모드로 전환되었습니다.'], ['online', '09:55 트래픽이 GW-WAS-01으로 정상 전환되었습니다.'], ['idle', '09:40 운영자 점검 작업이 시작되었습니다.']]
    },
    batch: {
      title: '정산 배치 서비스',
      subtitle: '일간 정산 및 내부 전송을 위한 Active-Standby 구성',
      status: '정상',
      available: '1 / 1',
      response: '대기',
      error: '0.00%',
      traffic: '예약 실행',
      check: '2분 전',
      topology: 'Active · Standby 구성',
      nodes: [['스케줄러', '정산 스케줄러', '정상'], ['처리 계층', 'Batch-Active', '정상'], ['대기 계층', 'Batch-Standby', '정상']],
      instances: [['스케줄러', 'Batch-Scheduler', '운영-DB-01', 'v3.2.1', '정상'], ['Active', 'Batch-Active', '10.10.1.31', 'v3.2.1', '정상'], ['Standby', 'Batch-Standby', '10.10.1.32', 'v3.2.1', '대기']],
      events: [['online', '02:10 일일 정산 작업이 정상 완료되었습니다.'], ['online', '02:08 내부 전송 파일 무결성 검사가 완료되었습니다.'], ['idle', '다음 예약 실행: 내일 02:00']]
    },
    database: {
      title: 'DB 클러스터',
      subtitle: 'Primary와 Replica로 구성된 데이터베이스 복제 그룹',
      status: '정상',
      available: '3 / 3',
      response: '8 ms',
      error: '0.01%',
      traffic: '1,248 qps',
      check: '방금 전',
      topology: 'Primary · Replica 2대',
      nodes: [['접속 계층', 'DB Proxy', '정상'], ['Primary', 'DB-Primary', '정상'], ['Replica', 'DB-Replica-01 · DB-Replica-02', '정상']],
      instances: [['Primary', 'DB-Primary', '운영-DB-01:5432', 'PostgreSQL 16', '정상'], ['Replica', 'DB-Replica-01', '10.10.1.22:5432', 'PostgreSQL 16', '정상'], ['Replica', 'DB-Replica-02', '10.10.1.23:5432', 'PostgreSQL 16', '정상']],
      events: [['online', '10:10 복제 지연 0.2초로 정상 범위입니다.'], ['online', '10:00 백업 검증 작업이 성공했습니다.'], ['idle', '다음 전체 백업: 오늘 23:30']]
    }
  };

  const setText = (selector, value) => {
    const node = document.querySelector(selector);
    if (node) node.textContent = value;
  };
  const make = (tag, text, className) => {
    const node = document.createElement(tag);
    if (text) node.textContent = text;
    if (className) node.className = className;
    return node;
  };
  const showLog = (label) => {
    const lowerLabel = label.toLowerCase();
    const type = lowerLabel.includes('tomcat') ? 'tomcat' : lowerLabel.includes('db-') ? 'database' : lowerLabel.includes('batch') ? 'batch' : lowerLabel.includes('web') ? 'web' : 'service';
    const catalog = {
      tomcat: { source: 'org.apache.catalina.core.StandardService', info: ['Apache Tomcat 9.0.89 서비스 상태가 정상입니다.', 'HTTP 커넥터 [8080] 상태 확인에 성공했습니다.', '세션 정리 작업이 완료되었습니다. activeSessions=18', '애플리케이션 컨텍스트 [/portal] 요청 처리가 정상입니다.', 'JDBC 연결 풀 상태가 정상입니다. active=12 idle=8'] },
      database: { source: 'postgresql', info: ['클라이언트 연결 상태가 정상입니다. activeConnections=42', '체크포인트 완료. buffers=1281 writeTime=18ms', '복제 상태가 정상입니다. replayLag=0.2s', '쿼리 대기열이 정상 범위입니다. queued=0', '백그라운드 정리 작업이 완료되었습니다.'] },
      batch: { source: 'batch.scheduler', info: ['예약 작업 상태를 확인했습니다. nextRun=02:00', '처리 대기 항목이 없습니다. queueDepth=0', '이전 배치 실행 결과가 정상으로 확인되었습니다.', '전송 대상 파일의 무결성 검사가 완료되었습니다.', '대기 인스턴스 상태 확인에 성공했습니다.'] },
      web: { source: 'web.gateway', info: ['헬스체크 응답이 정상입니다. status=200', '요청 라우팅이 정상 처리되었습니다. upstream=was-cluster', 'TLS 인증서 유효 기간을 확인했습니다. remainingDays=154', '접속 로그 집계 작업이 완료되었습니다.', '로드밸런서 대상 상태가 정상입니다. healthyTargets=2'] },
      service: { source: 'runtime.observer', info: ['관측 프로파일 상태 확인이 완료되었습니다.', '서비스 프로세스 상태가 정상입니다.', '포트 연결 상태를 확인했습니다.', '헬스체크 요청에 성공했습니다. status=200', '리소스 사용률이 정상 범위입니다.'] }
    }[type];
    logTitle.textContent = `${label} 로그`;
    logView.textContent = Array.from({ length: 500 }, (_, index) => {
      const minute = String(Math.floor(index / 60)).padStart(2, '0');
      const second = String(index % 60).padStart(2, '0');
      let level = 'INFO ';
      let message = catalog.info[index % catalog.info.length];
      if (index % 113 === 0 && index !== 0) {
        level = 'WARN';
        message = '응답 시간이 경고 기준에 근접했습니다. latency=820ms threshold=1000ms';
      }
      if (index % 197 === 0 && index !== 0) {
        level = 'WARN';
        message = '리소스 사용률 추이를 관찰합니다. 즉시 조치가 필요한 상태는 아닙니다.';
      }
      return `2026-06-24 10:${minute}:${second} ${level} ${catalog.source} [${label}] ${message}`;
    }).join('\n');
    logDialog.showModal();
    logView.scrollTop = logView.scrollHeight;
    logView.focus();
  };
  const openProfile = (name, host, version) => {
    setText('[data-instance-profile-title]', `${name} 관측 프로파일`);
    const instance = profileDialog.querySelector('[data-instance-profile-name]');
    const targetHost = profileDialog.querySelector('[data-instance-profile-host]');
    const profile = profileDialog.querySelector('[data-instance-profile-select]');
    if (instance) instance.value = `${name} · ${version}`;
    if (targetHost) targetHost.value = host;
    if (profile) profile.selectedIndex = name.toLowerCase().includes('php') ? 1 : name.toLowerCase().includes('batch') ? 2 : 0;
    profileDialog.showModal();
  };
  const renderTopology = (nodes) => {
    topology.replaceChildren();
    nodes.forEach(([role, name, state], index) => {
      const node = make('div', '', `topology-node ${state === '주의' ? 'warning' : ''}`);
      node.append(make('span', role, 'topology-role'), make('strong', name), make('small', state));
      topology.append(node);
      if (index < nodes.length - 1) topology.append(make('div', '→', 'topology-link'));
    });
  };
  const renderInstances = (instances) => {
    instanceContainer.replaceChildren();
    instances.forEach(([role, name, host, version, state]) => {
      const row = make('div', '', 'solution-instance-row');
      row.tabIndex = 0;
      row.setAttribute('role', 'button');
      row.setAttribute('aria-label', `${name} 관측 프로파일 열기`);
      const open = (event) => {
        if (!event.target.closest('button')) openProfile(name, host, version);
      };
      row.addEventListener('click', open);
      row.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          openProfile(name, host, version);
        }
      });
      const identity = make('span');
      identity.append(make('small', role), make('strong', name));
      row.append(identity, make('span', host), make('span', version), make('span', state, `solution-state ${state === '점검' ? 'warning' : ''}`));
      const actions = make('span', '', 'instance-actions');
      const status = make('button', '상태 확인');
      status.type = 'button';
      status.dataset.demoRegister = '';
      const log = make('button', '로그');
      log.type = 'button';
      log.addEventListener('click', () => showLog(name));
      actions.append(status, log);
      row.append(actions);
      instanceContainer.append(row);
    });
  };
  const renderEvents = (events) => {
    eventList.replaceChildren();
    events.forEach(([state, text]) => {
      const item = make('li');
      item.append(make('span', '', `state ${state === 'idle' ? 'idle' : 'online'}`), document.createTextNode(text));
      eventList.append(item);
    });
  };
  const render = (key) => {
    const group = groups[key];
    setText('[data-solution-title]', group.title);
    setText('[data-solution-subtitle]', group.subtitle);
    setText('[data-solution-status]', group.status);
    setText('[data-solution-available]', group.available);
    setText('[data-solution-response]', group.response);
    setText('[data-solution-error]', group.error);
    setText('[data-solution-traffic]', group.traffic);
    setText('[data-solution-check]', group.check);
    setText('[data-solution-topology-label]', group.topology);
    renderTopology(group.nodes);
    renderInstances(group.instances);
    renderEvents(group.events);
    document.querySelectorAll('[data-solution-group]').forEach((button) => button.classList.toggle('active', button.dataset.solutionGroup === key));
  };
  document.querySelectorAll('[data-solution-group]').forEach((button) => button.addEventListener('click', () => render(button.dataset.solutionGroup)));
  document.querySelectorAll('[data-solution-log-close]').forEach((button) => button.addEventListener('click', () => logDialog.close()));
  document.querySelectorAll('[data-instance-profile-close]').forEach((button) => button.addEventListener('click', () => profileDialog.close()));
  render('portal');
});
