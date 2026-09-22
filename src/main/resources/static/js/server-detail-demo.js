document.addEventListener('DOMContentLoaded', () => {
  const samples = {'운영-웹-01':['MTCM','2026-06-23 10:11','2026-06-23 13:00','administrator','서버 포트 변경'],'운영-DB-01':['김개발','2026-06-23 09:32','2026-06-23 10:05','administrator','접속 계정 추가'],'업무-윈도우-01':['이운영','2026-06-22 15:20','2026-06-22 16:02','김관리','RDP 연결 설정 변경'],'업무용 웹 포털':['박지원','2026-06-23 08:50','2026-06-23 09:14','administrator','HTTPS 인증서 갱신']};
  const list = (values) => { const dl=document.createElement('dl'); dl.className='detail-list'; values.forEach(([a,b])=>{const r=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=a;dd.textContent=b;r.append(dt,dd);dl.append(r)});return dl };
  document.querySelectorAll('.detail-panel [data-preview-detail]').forEach((detail) => {
    if(detail.querySelector('[data-server-demo-tabs]')) return;
    const original=detail.querySelector('.detail-list'); if(!original) return;
    const data=samples[detail.querySelector('h2')?.textContent.trim()]||samples['운영-웹-01']; const tabs=document.createElement('div');tabs.className='console-tabs compact-tabs';tabs.dataset.serverDemoTabs='true';
    const info=document.createElement('div'),history=document.createElement('div'),admin=document.createElement('div');info.append(original);history.hidden=true;admin.hidden=true;history.append(list([['접속 사용자',data[0]],['접속 시간',data[1]],['접속 해지 시간',data[2]]]));admin.append(list([['변경 사용자',data[3]],['변경 이력',data[4]],['변경 시간','2026-06-22 16:24']])); const panels=[info,history,admin];
    ['서버 정보','마지막 접속 이력','관리자 변경 이력'].forEach((name,index)=>{const b=document.createElement('button');b.type='button';b.textContent=name;if(!index)b.classList.add('active');b.addEventListener('click',()=>{tabs.querySelectorAll('button').forEach(x=>x.classList.toggle('active',x===b));panels.forEach((p,i)=>p.hidden=i!==index)});tabs.append(b)});detail.append(tabs,info,history,admin);
  });
});
