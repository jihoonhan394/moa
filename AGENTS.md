# MOA AI 개발 공통 규칙

Codex와 Claude Code는 작업 전 이 파일과 `doc/1차-진행/AGENTS.md`를 읽는다. 문서 간 충돌 시 1차 진행 문서와 보안 규칙을 우선하며, 충돌 사실을 문서화한다.

## 작업 원칙

- Java는 Google Java Style Guide를 따른다. 들여쓰기는 2칸, 클래스는 `UpperCamelCase`, 메서드·변수는 `lowerCamelCase`를 사용한다.
- Spring MVC 책임을 분리한다: Controller는 HTTP/화면, Service는 비즈니스 규칙, Repository는 영속성, Entity는 매핑만 담당한다.
- DB 변경은 Flyway migration으로만 수행한다. 공유 PostgreSQL 개발 DB에서 JPA DDL 자동 변경을 사용하지 않는다.
- 기능 작업은 작은 단위로 나누고 `doc/tasks/`에 개별 tasklist와 테스트 결과를 남긴다.
- 관련 코드와 문서를 함께 갱신한다. 범위 밖 기능(세션 녹화, 파일 전송, AI 분석 등)은 추가하지 않는다.

## KISA 시큐어코딩 최소 기준 (KISA 소프트웨어 개발보안 가이드 기준)

- 모든 폼·경로·쿼리 입력을 서버에서 Bean Validation으로 검증한다.
- JPA/파라미터 바인딩만 사용하며 문자열 결합 SQL을 금지한다.
- Thymeleaf escaping을 유지하고 `th:utext`, `innerHTML`을 사용하지 않는다.
- Spring Security의 CSRF, 세션 고정 방지, 로그아웃 세션 무효화를 유지한다.
- URL 보안과 Service 레벨 권한 검사를 함께 구현한다.
- 비밀번호는 BCrypt 등 단방향 해시만 저장한다. SSH/RDP/DB 비밀번호·키·토큰은 코드, 문서, 로그, 테스트 픽스처, Git에 기록하지 않는다.
- 오류 화면과 로그에서 stack trace, DB 연결 정보, 내부 경로 및 민감정보를 노출하지 않는다.

## 멀티테넌트 격리 (불변식)

- 모든 조회·수정·삭제는 현재 테넌트로 스코프한다. 리포지토리 쿼리에서 tenant 조건 누락을 금지한다.
- 테넌트 식별자는 인증 주체(SecurityContext)에서 도출한다. 클라이언트가 보낸 `tenant_id`/파라미터를 신뢰하지 않는다.
- 크로스 테넌트 접근은 기능 버그가 아니라 **보안 사고**로 취급한다. 서비스 진입 시 대상 리소스의 테넌트 소속을 재검증한다.
- 신규 엔티티/쿼리 추가 시 tenant 스코프 테스트(다른 테넌트 데이터가 보이지 않음)를 함께 작성한다.

## 원격 명령 실행 안전 (SSH 제어)

- 사용자 입력(식별자·경로·컨테이너명)을 쉘 명령 문자열에 그대로 결합하지 않는다.
- 유형(SolutionType)별 **허용된 명령 템플릿**만 사용한다. 식별자는 화이트리스트 패턴(예: `^[A-Za-z0-9._-]+$`)으로 검증 후 대입한다.
- `CUSTOM_COMMAND`는 관리자 전용이며 모든 실행을 감사 로그로 남긴다. 임의 쉘 명령 실행 경로를 일반 사용자에게 노출하지 않는다.
- 실행 결과(stdout/stderr)를 화면·로그로 반환할 때 secret·내부 경로가 섞이지 않도록 확인한다.

## 감사 로그 (PAM 필수)

- 자격증명 열람·솔루션 제어·권한 변경·로그인/로그인 실패 등 특권 행위는 **누가·언제·무엇을·결과**를 기록한다.
- 감사 로그는 append-only를 지향하고 사후 삭제·수정을 허용하지 않는다.
- 감사 로그·이벤트 페이로드에 평문 secret·비밀번호·키를 남기지 않는다(마스킹).

## 자격증명·비밀 수명주기

- secret은 로그·예외 메시지·화면·URL·쿼리스트링에 절대 노출하지 않는다. 표시가 필요하면 마스킹한다.
- 볼트는 쓰기전용이며, 사용 시 step-up 인증을 거친다. 복호화된 secret은 메모리 체류를 최소화하고 사용 후 즉시 제로화한다.
- KEK는 DB 밖(env/KMS/Vault)에 두고 키버전·로테이션 정책을 유지한다.

## 인가 default-deny · 최소권한

- Spring Security는 명시적으로 허용한 경로 외 전부 차단한다. 신규 라우트는 "기본 거부"에서 출발해 필요한 권한만 연다.
- URL 보안과 Service 레벨 권한 검사를 이중으로 둔다(컨트롤러 통과가 곧 인가가 아니다).
- 각 주체에는 필요한 최소 권한만 부여한다. 기본 권한 모델은 default-deny를 유지한다.

## 테스트·완료 기준 (Definition of Done)

- 새 서비스 로직은 단위 테스트, 컨트롤러는 MockMvc 테스트를 함께 작성한다.
- 실패하거나 `@Disabled`/skip된 테스트가 있으면 머지하지 않는다. 커밋 전 `gradlew.bat test`를 통과시킨다.
- "완료"는 다음을 모두 만족한다: 테스트 통과 · 관련 문서(README/tasklist) 갱신 · 비밀정보 미유입 확인 · 회귀 없음.

## 마이그레이션 불변성

- 이미 머지·적용된 Flyway 마이그레이션 파일은 수정하지 않는다(forward-only). 스키마 변경은 항상 새 버전으로 추가한다.
- 마이그레이션은 재현 가능해야 하며, 파괴적 변경은 되돌림/데이터 이관 전략을 문서화한다.

## 로깅·에러 표준

- 구조적 로깅과 요청 상관관계 식별자를 사용하고, 로그 레벨 규칙을 지킨다.
- 로그·에러 응답에서 secret·PII·내부 경로·stack trace를 마스킹/제거한다.
- 예외를 화면·API로 그대로 흘리지 않고 표준 에러 응답 형식으로 반환한다.

## 의존성·공급망 위생

- 의존성 버전을 고정한다. 새 의존성은 **3축으로 평가**한 뒤 추가한다: ① 유지보수(활발한 릴리스·배경 조직/커뮤니티) ② 라이선스(Apache/BSD/MIT 등 상용 OK — GPL/AGPL·BSL 회피) ③ 결합도.
- **현세대 정합성 우선**: 우리 스택(Spring Boot·Jakarta·Java 17)과 같은 세대인지 확인한다. 이미 구세대(javax/EOL 전이 의존)에 좌초된 라이브러리는 신규 채택하지 않는다. (예: winrm4j→CXF 3.x(javax)는 채택 불가로 판단해 배제)
- **결합도 최소화**: 아웃오브프로세스/프로토콜 경계 연동(예: Guacamole)을 깊은 인프로세스 라이브러리보다 선호한다. 핵심 기능이 낡은 전이 의존을 끌고 오면, **JDK만으로 자체 구현해 소유**하는 편이 EOL/버전불일치 인질 리스크를 없앤다. (예: WinRM은 winrm4j 대신 JDK HttpClient로 자작)
- 원본이 방치된 라이브러리는 활발한 유지보수 포크로 대체한다(예: JSch → mwiede 포크).
- 정기적으로 취약점 점검(OWASP dependency-check 등)을 수행하고 알려진 취약 버전을 배제한다.

## 향후 강화 (성숙도)

- 로그인 스로틀·계정 잠금, 세션 타임아웃·동시 세션 정책.
- 운영 환경 HTTPS 강제, actuator 등 관리 엔드포인트 보안.
- 응답은 엔티티 직접 노출 대신 DTO 경계를 두고, 트랜잭션 경계는 서비스 계층에 둔다.
- 설정 하드코딩 금지(프로필/환경변수 외부화), 사용자 노출 문자열은 i18n 메시지로 외부화한다.

## 개발 DB 설정

- 개발 DB는 Tailscale 접속 후 접근 가능한 PostgreSQL을 사용한다.
- DB URL은 반드시 `SPRING_DATASOURCE_URL` 환경변수로 주입한다.
- DB 사용자명은 `SPRING_DATASOURCE_USERNAME` 환경변수로 주입한다.
- DB 비밀번호는 반드시 `SPRING_DATASOURCE_PASSWORD` 환경변수로만 주입한다.
- 개인별 DB명, 사용자명, WAS 포트는 별도 DB 접속 가이드의 매핑을 따른다.
- `.env`, `application-local.*`은 Git 추적 대상이 아니다. 새 비밀 파일을 추가하기 전 `.gitignore`를 확인한다.

## Git 브랜치 워크플로우

- 브랜치: `main`(항상 그린·배포 기준)을 보호하고, 기능은 기능별 `feature/<이름>` 브랜치에서 작업한다. `main`에는 사소한 문서/설정 외 직접 커밋하지 않는다.
- 기능은 `main`에서 `feature/<이름>` 브랜치를 따서 작업한다: `git checkout main && git checkout -b feature/<이름>`.
- 테스트 통과 후 `main`에 **스쿼시 머지**한다: `git checkout main && git merge --squash feature/<이름>` 후 커밋.
- **스쿼시 머지 시 반드시 포함**한다: ① 기능 단위의 커밋 메시지, ② **README.md 갱신**(현재 범위/기능 반영), ③ **버전 올리기**(`build.gradle`의 `version`).
- 원격은 `origin`(github.com/jihoonhan394/moa). 머지 후 `git push origin main`. (옛 스터디 저장소 `mtcmhjh20/moa`는 아카이브 — 전체 히스토리는 로컬 번들 `moa-archive-20260922.bundle`에 보존.)
- 커밋 위생: `.env`·비밀·`*.exe`·`*.zip`·`doc/`(로컬 문서)는 커밋 금지(`.gitignore` 확인). 스테이징에 비밀정보 유입 여부를 커밋 전 확인한다. 커밋 메시지 끝에 `Co-Authored-By` 라인을 남긴다.

## 인프라·환경 참고

- **웹 SSH/RDP 게이트웨이(Apache Guacamole)**: 공유 서버에 docker-compose로 상주(`/opt/moa-guacamole/`, 포트 8090, `guacamole-auth-json`). MOA 연동은 `MOA_GUACAMOLE_BASE_URL`, `MOA_GUACAMOLE_SECRET_KEY` env. **공유키(SECRET_KEY) 값은 서버 `.env`에만 두고 git·코드·문서에 절대 저장하지 않는다.**
- **자격증명 볼트 KEK**: `MOA_VAULT_MASTER_KEY`(64 hex = 32바이트, `openssl rand -hex 32`)를 env로 주입한다. 미설정 시 자격증명 저장/사용이 실패한다. KEK는 DB 밖(env/KMS/Vault)에 둔다.
- **테스트 환경 접근 정보**(서버 IP·포트·계정·비밀번호, DB, 테스트 솔루션)는 `doc/test-environment.md`(로컬 전용, `.gitignore`)에 정리한다. 값은 저장소에 커밋하지 않는다.
- **공용 테스트 솔루션**(솔루션 제어 시험용) 등록 가이드는 `doc/test-solution-guide.md`(로컬). 다른 스터디 사용자도 이 값으로 MOA에서 제어를 시험할 수 있다.
- 모든 접속/제어용 계정·키·토큰은 env 또는 로컬 `.env`/볼트로만 관리하고 저장소에 값을 남기지 않는다.

## 협업 인수인계

- 1차 MVP 구현은 `doc/1차-구현/README.md` 진행 보드에서 시작한다. 작업 착수 전 보드에서 진행 중/다음 예정 태스크를 고르고, `doc/1차-구현/tasks/Txx-*.md`의 체크박스로만 상태를 공유한다. Codex/Claude가 번갈아 이어받는 기준은 이 보드다.
- 각 변경은 관련 tasklist의 체크박스와 테스트 결과를 갱신한다.
- SSH/RDP, 자산 권한, 감사 로그 변경은 다른 팀원의 리뷰 대상으로 명시한다.
- 커밋 전 `gradlew.bat test`와 비밀정보 유입 여부를 확인한다.
