# 사내 배포

30~50명 규모 사내 시연·도그푸딩 기준이다. 고객사 납품 구성(DMZ 중계·망분리)은 다르다 —
그쪽은 `moa-mobile/doc/02-아키텍처.md` 를 본다.

## 1. 서버 사양

**한 대면 된다.** 앱과 DB 를 같이 올린다.

| | 권장 | 최소 |
|---|---|---|
| vCPU | 2 | 2 |
| 메모리 | **4GB** | 2GB |
| 디스크 | 50GB SSD | 20GB |

근거 — 앱 JVM 이 1GB 안쪽, PostgreSQL 이 0.5~1GB, 나머지는 OS 와 여유다.
디스크는 **위키 첨부가 기관당 최대 2GB**(`moa.wiki.attachment-quota-mb`)라 거기서 정해진다.
DB 자체는 자산 수백~수천 건이면 수백 MB 수준이다.

> 사용자가 늘어도 CPU 보다 **동시 접속 세션**이 먼저 는다. DB 커넥션 풀이 5 로 묶여 있어
> (`spring.datasource.hikari.maximum-pool-size`) 50명 정도까지는 이 사양으로 버틴다.

### 몇 대가 필요한가

**물리 서버는 따로 없어도 된다. VM 1대면 전부 돌아간다.**

| | |
|---|---|
| MOA 앱 | VM 1대 |
| PostgreSQL | 같은 VM |
| nginx | 같은 VM (터널을 쓰면 생략 가능 — 아래) |
| 관리 **대상** 서버 | 이미 사내에 있는 것들. MOA 용으로 새로 살 것은 없다 |

쪼개야 할 때는 **DB 를 뺄 때**다. 그 전까지 한 대로 둔다 — 쪼개면 백업·네트워크·장애 지점이
같이 늘어난다.

## 1-1. 클라우드 터널로 노출할 때

사내 DNS·고정 IP·인증서 발급이 번거로워 Cloudflare Tunnel / Tailscale 같은 터널로 여는 경우다.
**동작은 한다. 다만 세 가지가 달라진다.**

### ⚠️ ① 신뢰 홉 수가 달라진다 — 가장 자주 틀리는 지점

터널은 앞단에 홉을 하나 더 만든다. `MOA_TRUSTED_PROXY_COUNT` 를 그만큼 올려야 한다.

| 구성 | 홉 수 |
|---|---|
| 사용자 → nginx → MOA | `1` |
| 사용자 → 터널 → MOA (nginx 없음) | `1` |
| 사용자 → 터널 → nginx → MOA | `2` |

**추측하지 말고 잰다.** 로그인에 한 번 실패시키고 로그를 본다.

```bash
# 잠깐 켠다
sudo systemctl edit moa      # [Service] Environment=LOGGING_LEVEL_COM_MOARA_MOA_SECURITY_CLIENTIPRESOLVER=DEBUG
sudo systemctl restart moa
journalctl -u moa -f | grep 출발지
```

```
[출발지] trustedProxyCount=1 remoteAddr=10.0.0.5 X-Forwarded-For=203.0.113.9 -> 203.0.113.9
```

`X-Forwarded-For` 의 **칸 수**가 곧 홉 수다. 맞춘 뒤 **DEBUG 를 끈다** — 요청마다 출발지를
남기면 그 자체가 접속 기록이 되고 로그가 커진다.

> 틀리면 조용히 잘못 돈다. 적게 잡으면 터널 IP 하나로 집계돼 **한 사람이 20번 틀리면 전체가
> 잠기고**, 많이 잡으면 체인이 짧아 헤더를 버리고 터널 IP 를 쓴다(같은 증상).

### ② TLS 와 nginx 가 겹친다

터널이 TLS 를 종단해 주면 nginx 의 인증서 설정은 필요 없다. 다만 **본문 크기와 타임아웃은
여전히 맞춰야 한다** — 터널 쪽 설정에서 본다.

| 값 | 왜 |
|---|---|
| 업로드 한도 **25MB 이상** | 위키 첨부가 20MB 다. Cloudflare 무료 플랜은 100MB 라 여유 있다 |
| 응답 타임아웃 **90초 이상** | 솔루션 로그 조회가 SSH 로 최대 60초다. Cloudflare 는 100초라 괜찮다 |

nginx 를 빼면 `MOA_COOKIE_SECURE=true` 는 그대로 켠다(터널이 HTTPS 를 준다).
대신 앱을 **루프백에만 묶는다** — 터널 데몬만 붙게 한다.

```
server.address=127.0.0.1
```

### ⚠️ ③ 트래픽이 외부 사업자를 지난다

MOA 는 **자격증명 볼트와 감사 기록을 들고 있는 PAM** 이다. 터널을 쓰면 그 화면이 외부
사업자의 망을 지난다(TLS 로 암호화되지만 종단은 그쪽이다).

| 용도 | 판단 |
|---|---|
| 사내 시연·도그푸딩 | 괜찮다. 다만 **실제 서버 비밀번호를 볼트에 넣지 않는 편**이 낫다 |
| 사내 상시 운영 | 사내 DNS + 사내 CA 로 가는 쪽을 권한다 |
| 고객사 납품 | **못 쓴다.** 설계가 DMZ 중계를 전제하는 이유다(`moa-mobile/doc/02`) |

### 추천 — 이미 Tailscale 을 쓰고 있다

테스트 서버가 Tailscale 위에 있다. **시연 대상자 폰·PC 에 Tailscale 만 깔면 터널이 따로 필요
없다.** 트래픽이 외부 사업자를 지나지 않고(WireGuard 직결), 인증서·DNS 준비도 줄어든다.

| | Tailscale | Cloudflare Tunnel |
|---|---|---|
| 보는 사람 준비 | 앱 설치 + 로그인 | 없음(주소만) |
| 트래픽 경로 | 직결(P2P) | 외부 사업자 경유 |
| 인증서 | 불필요(또는 MagicDNS + TLS) | 자동 |
| 홉 수 | **0** (앱에 직접) | 1 이상 |

**보는 사람이 사내 인원이면 Tailscale, 외부 손님에게도 보여야 하면 Cloudflare Tunnel** 이
갈림길이다.

## 2. 소프트웨어

| | 버전 | 왜 |
|---|---|---|
| OS | Ubuntu 22.04/24.04 LTS 또는 Rocky 9 | |
| **JDK** | **17** | `build.gradle` 이 17 로 고정돼 있다. 21 로 올리지 않는다 |
| PostgreSQL | 14 이상 | 운영 DB. H2 는 로컬 전용이다 |
| nginx | 1.18 이상 | TLS 종단 |
| (선택) Docker | | 웹 SSH/RDP 게이트웨이(Guacamole)를 쓸 때만 |

**톰캣을 따로 설치하지 않는다.** jar 안에 들어 있다.

## 3. 포트

| 출발 | 도착 | 포트 | 열 범위 |
|---|---|---|---|
| 사내 PC·폰 | nginx | 443 | 사내망 |
| nginx | MOA | 8081 | **127.0.0.1 만** |
| MOA | PostgreSQL | 5432 | **127.0.0.1 만** |
| MOA | 관리 대상 서버 | 22 / 5985 / 5986 | 사내망 |
| MOA | 사내 SMTP | 25 / 587 | 초대·알림 메일 |

8081 과 5432 를 사내망에 열어 두면 nginx 를 우회할 수 있다. 루프백에만 묶는다.

## 4. 준비물 — 이것부터 모은다

| | 무엇 | 비고 |
|---|---|---|
| ☐ | **TLS 인증서** | 사내 CA 또는 공인 인증서. 자체 서명이면 폰에 CA 를 설치해야 앱이 붙는다 |
| ☐ | **볼트 마스터 키** | `openssl rand -hex 32`. ⚠️ **잃으면 저장된 서버 비밀번호를 아무도 복호화할 수 없다.** 금고에 따로 보관 |
| ☐ | DB 비밀번호 | |
| ☐ | 플랫폼 관리자 비밀번호 | 최초 1회용. 계정을 만든 뒤 env 에서 지운다 |
| ☐ | 사내 SMTP 계정 | 초대 메일이 안 나가면 **초대 링크를 손으로 전달**해도 된다(화면에 나온다) |
| ☐ | 도메인 | 예: `moa.사내도메인`. DNS 를 서버로 |

## 5. 배포 절차

```bash
# ── 빌드 (개발 PC) ─────────────────────────────────────────
set JAVA_HOME=C:\Program Files\Java\jdk-17.0.19+10
gradlew.bat clean bootJar
#   build/libs/moa-<버전>.jar 가 나온다

# ── 서버 준비 (최초 1회) ───────────────────────────────────
sudo useradd -r -m -d /opt/moa -s /usr/sbin/nologin moa
sudo mkdir -p /opt/moa/data /etc/moa
sudo chown -R moa:moa /opt/moa

sudo -u postgres createuser moa --pwprompt
sudo -u postgres createdb db_moa -O moa

sudo install -m 600 -o root -g root deploy/moa.env.example /etc/moa/moa.env
sudo nano /etc/moa/moa.env          # 4번에서 모은 값을 채운다

sudo cp deploy/moa.service /etc/systemd/system/
sudo cp deploy/nginx-moa.conf /etc/nginx/conf.d/moa.conf
sudo systemctl daemon-reload

# ── 올리기 ─────────────────────────────────────────────────
scp build/libs/moa-*.jar 서버:/tmp/moa.jar
ssh 서버 'sudo systemctl stop moa \
  && sudo cp /opt/moa/moa.jar /opt/moa/moa.jar.bak 2>/dev/null; \
  sudo mv /tmp/moa.jar /opt/moa/moa.jar \
  && sudo chown moa:moa /opt/moa/moa.jar \
  && sudo systemctl start moa'

sudo nginx -t && sudo systemctl reload nginx
```

**스키마는 따로 손대지 않는다.** 기동할 때 Flyway 가 V1~V75 를 순서대로 적용한다
(`spring.jpa.hibernate.ddl-auto=validate` 라 JPA 가 테이블을 만들지 않는다).

## 6. 올린 뒤 확인

```bash
systemctl status moa
journalctl -u moa -f                       # Flyway 가 어디까지 올렸는지 보인다
curl -sI https://moa.사내도메인/login       # 302 면 정상
```

| ☐ | 확인 |
|---|---|
| ☐ | `/platform` 으로 플랫폼 관리자 로그인 → 기관 생성 |
| ☐ | **기관의 기능 모듈을 켠다** — 끄면 관리자가 로그인해도 자산·서버가 전부 403 이다 |
| ☐ | 기관 대표 관리자 생성 → 그 계정에 필요한 역할 부여 |
| ☐ | `MOA_BOOTSTRAP_ADMIN_PASSWORD` 를 env 에서 **지우고 재시작** |
| ☐ | 폰에서 열어 로그인 (앱 없이 브라우저로 먼저) |

## 7. 모바일 앱

앱은 별도 레포(`moa-mobile`)다. 사내 배포는 **APK 직접 전달**이다(스토어 없음).

```
config/env.prod.json 의 serverUrl 을 https://moa.사내도메인 으로
scripts\build-android.cmd prod release
```

| ☐ | 준비물 |
|---|---|
| ☐ | **릴리스 keystore** — `keytool -genkeypair`. ⚠️ 비밀번호를 잃으면 같은 앱을 업데이트할 수 없다 |
| ☐ | 설치 대상 폰에서 "출처를 알 수 없는 앱" 허용 |
| ☐ | 자체 서명 TLS 인증서를 쓴다면 폰에 사내 CA 설치 |

## 8. 백업

| 대상 | 방법 | 주기 |
|---|---|---|
| DB | `pg_dump db_moa` | 매일 |
| 위키 첨부 | `/opt/moa/data/wiki-attachments` | 매일 |
| **볼트 마스터 키** | 금고·비밀번호 관리자 | 1회 (바뀔 때) |

> ⚠️ **DB 만 백업하면 복구가 안 된다.** 자격증명은 마스터 키로 암호화돼 있어, 키가 없으면
> 복원한 DB 에서 서버 비밀번호를 꺼낼 수 없다.

## 9. 지금 상태에서 사내 공개 시 알아 둘 것

| | |
|---|---|
| **TLS 를 반드시 붙인다** | 평문이면 세션 쿠키가 그대로 지나간다. `MOA_COOKIE_SECURE=true` 는 HTTPS 를 전제한다 |
| **`MOA_TRUSTED_PROXY_COUNT` 를 맞춘다** | nginx 한 대면 1. 0 으로 두면 전원이 같은 출발지로 집계돼 한 사람 때문에 전체가 잠긴다 |
| 볼트 열람에 2차 인증이 없다 | 자격증명 열람 권한을 꼭 필요한 사람에게만 준다(알려진 미완 — `moa-mobile/doc/12` S3) |
| 퇴사 처리가 열린 세션을 끊지 않는다 | 알려진 한계. 퇴사 직후에는 세션 만료(30분)를 기다려야 한다 |
| 헬스 체크가 깨져도 알림이 없다 | 상태는 화면을 열어야 보인다(`moa-mobile/doc/07` P19) |
