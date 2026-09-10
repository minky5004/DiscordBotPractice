# DiscordBotPractice

> 엔케팔린 완충 시각 계산 · 그 시각 멘션 예약 — 림버스 컴퍼니 JDA 연습 봇

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 25 |
| Library | JDA 6.5.0 |
| Database | PostgreSQL 17 · JDBC · Flyway |
| Test | JUnit 5 · Testcontainers |
| Infra | Docker Compose |
| Build | Gradle 9.7.1 |

## 실행

### 1. 봇 토큰 발급

[Discord Developer Portal](https://discord.com/developers/applications) → **New Application** → 이름 입력 → 좌측 **Bot**

**Reset Token** → 표시되는 토큰 복사 (창을 닫은 뒤에는 재발급뿐)

불필요한 특권 인텐트 설정 — 게이트웨이 인텐트와 무관하게 도착하는 슬래시 상호작용

### 2. 서버 초대

좌측 **OAuth2** → **OAuth2 URL Generator**

- **SCOPES**: `bot` · `applications.commands`
- **BOT PERMISSIONS**: `Send Messages`
- 아래 **GENERATED URL** 을 브라우저에 붙여넣기 → 봇을 넣을 서버 선택

### 3. 로컬 실행

사전 설치 도구 Docker Desktop — 로컬 PostgreSQL · 테스트용 컨테이너

```powershell
git clone https://github.com/minky5004/DiscordBotPractice.git
cd DiscordBotPractice
Copy-Item .env.example .env      # .env 를 열어 DISCORD_BOT_TOKEN 에 1번의 토큰 · DB_PASSWORD 에 영숫자 비밀번호
docker compose up -d --wait db   # → PostgreSQL 컨테이너 healthy
.\gradlew.bat run                # → 콘솔에 "봇이 정상적으로 로그인되었습니다"
```

서버 채팅창에서

| 명령 | 응답 |
|---|---|
| `/ping` | `Pong!` |
| `/엔케팔린 현재:132 최대:142` | 6분 × 10개 뒤 완충 시각(뷰어 시간대로 렌더링되는 디스코드 타임스탬프) · 그 시각의 멘션 예약 |
| `/엔케팔린 현재:132 최대:142 알림:False` | 예약 없이 시각만 |
| `/엔케팔린취소` | 예약한 멘션 해제 |

유저당 예약 하나 — 앞의 것을 교체하는 재실행 · 봇 재시작을 버티는 DB 예약 · 꺼져 있던 사이 지난 예약의 기동 직후 멘션

전역 등록 명령의 클라이언트 캐시 지연 — `/` 자동완성에 명령이 안 보일 때 `Ctrl+R` 새로고침

## 구조

```
Main.java                   .env 로딩 · DB 연결 · JDA 로그인 · 예약 복구 · 슬래시 명령 등록
PingPongListener.java       /ping 정의 · Pong! 응답
EnkephalinListener.java     /엔케팔린 · /엔케팔린취소 정의 · 충전 주기 상수 · 완충 시각 계산 · 멘션
ReminderScheduler.java      유저당 예약 하나 · 단일 데몬 스레드 스케줄러 · 기동 시 DB 복구
ReminderStore.java          reminder 테이블 JDBC 저장소 · 기동 시 Flyway 마이그레이션
db/migration/               Flyway 스키마 이력
compose.yaml                로컬 PostgreSQL
.env.example                토큰 · 비밀번호 자리를 비워 둔 견본
```

테스트 `.\gradlew.bat test` — Testcontainers 로 띄운 실제 PostgreSQL 대상
