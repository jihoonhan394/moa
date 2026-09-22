(() => {
  const messages = new Map([
    ['Dashboard', '대시보드'], ['Assets', '자산'], ['Servers', '서버'], ['Users', '사용자'], ['Audit Logs', '감사 로그'],
    ['Manage assets', '자산 관리'], ['Register server', '서버 등록'], ['Servers', '서버'], ['Software', '소프트웨어'], ['Physical assets', '실물 자산'], ['Managed servers', '관리 서버'], ['Online assets', '온라인 자산'], ['Software assets', '소프트웨어 자산'], ['Total inventory', '전체 인벤토리'],
    ['Server overview', '서버 현황'], ['All servers', '전체 서버'], ['Operational snapshot', '운영 현황'], ['Review queue', '검토 대기'], ['Access and changes', '접속 및 변경 이력'], ['Audit logs', '감사 로그'], ['Asset inventory', '자산 인벤토리'], ['Register asset', '자산 등록'], ['Create asset', '자산 등록'], ['Create user', '사용자 생성'], ['Manage', '관리'], ['Edit', '수정'], ['View', '상세 보기'], ['Delete', '삭제'], ['Disable', '비활성화'], ['Disabled', '비활성화됨'], ['Status', '상태'], ['Owner', '담당자'], ['Columns', '표시 열'],
    ['Physical assets', '실물 자산'], ['Software assets', '소프트웨어 자산'], ['All servers', '전체 서버'], ['Information', '서버 정보'], ['Access history', '접속 이력'], ['Admin changes', '관리자 변경 이력'], ['Connect', '접속'], ['Test connection', '접속 테스트'], ['User logs', '사용자 로그'], ['Approval requests', '승인 요청'], ['Administrator logs', '관리자 로그'], ['Create temporary user', '임시 사용자 생성'],
    ['Sign in', '로그인'], ['Sign in to console', '콘솔 로그인'], ['Username', '아이디'], ['Password', '비밀번호'], ['Name', '이름'], ['Email', '이메일'], ['Cancel', '취소'], ['Save changes', '변경 저장'], ['Back to list', '목록으로'], ['Register asset', '자산 등록'], ['Register server', '서버 등록'],
    ['No assets yet', '등록된 자산이 없습니다'], ['No managed users', '등록된 사용자가 없습니다'], ['No registered server', '등록된 서버가 없습니다'], ['No queued transfers', '대기 중인 전송이 없습니다'], ['Page not found', '페이지를 찾을 수 없습니다']
    ,['Infrastructure, asset lifecycle and access operations in one view.', '인프라 현황, 자산 수명주기, 접근 운영을 한 화면에서 관리합니다.']
    ,['Connection-enabled infrastructure', '접속 가능한 인프라']
    ,['Available for approved access', '승인된 사용자 접속 가능']
    ,['License and renewal tracking', '라이선스 및 갱신 관리']
    ,['Infrastructure and managed targets', '인프라 및 관리 대상 전체']
    ,['SERVER INVENTORY', '서버 인벤토리']
    ,['SERVER OVERVIEW', '서버 현황']
    ,['SELECTED SERVER', '선택한 서버']
    ,['OPERATIONAL SNAPSHOT', '운영 현황']
    ,['Review approved access routes', '승인된 접속 경로를 검토합니다']
    ,['Validate connectivity before release', '배포 전 연결 상태를 확인합니다']
    ,['Production', '운영 환경']
    ,['Staging', '검증 환경']
    ,['ready', '준비됨']
    ,['review', '검토 필요']
    ,['Access history will appear after connection logging is enabled.', '접속 로그 연동 후 최근 접속 이력이 표시됩니다.']
    ,['Monitoring integration pending', '모니터링 연동 대기']
    ,['Collected server facts appear here', '서버 자원 정보가 표시됩니다.']
    ,['Expiring software licenses will appear here.', '만료 예정 소프트웨어 라이선스가 표시됩니다.']
    ,['Assets without an owner require review.', '담당자가 없는 자산은 검토가 필요합니다.']
    ,['Offline servers require confirmation.', '오프라인 서버는 상태 확인이 필요합니다.']
    ,['Server access history is recorded here.', '서버 접속 이력이 기록됩니다.']
    ,['Administrative changes are audited.', '관리자 변경 작업은 감사 로그에 기록됩니다.']
    ,['Pending approval requests are highlighted.', '승인 대기 요청이 강조 표시됩니다.']
  ]);
  const translate = (value) => messages.get(value.trim()) || value;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  const nodes = [];
  while (walker.nextNode()) nodes.push(walker.currentNode);
  nodes.forEach((node) => { const translated = translate(node.nodeValue); if (translated !== node.nodeValue) node.nodeValue = translated; });
  document.querySelectorAll('[placeholder], [aria-label], [title]').forEach((element) => {
    ['placeholder', 'aria-label', 'title'].forEach((name) => { if (element.hasAttribute(name)) element.setAttribute(name, translate(element.getAttribute(name))); });
  });
  document.title = translate(document.title.replace(/^MOA\s*-\s*/, '')) === document.title.replace(/^MOA\s*-\s*/, '') ? document.title : `MOA - ${translate(document.title.replace(/^MOA\s*-\s*/, ''))}`;
})();
