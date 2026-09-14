# DiscordBotPractice

[![ci](https://github.com/minky5004/DiscordBotPractice/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/minky5004/DiscordBotPractice/actions/workflows/ci.yml)

> 림버스 컴퍼니 엔케팔린 완충 시각 계산 · 그 시각 **봇 재시작에도 사라지지 않는** 멘션 예약 · Steam 공식 공지의 한국어 채널 중계

6분에 1개씩 차는 엔케팔린의 완충 시각 암산 대체. 알림 봇에 흔히 붙는 결함 둘 — 재시작에 사라지는 예약 ·
복구 뒤 두 번 가는 멘션. 원본은 PostgreSQL · 스케줄러 큐는 기동 때 되채우는 사본 · 꺼져 있던 사이 지난
예약의 기동 직후 1회 멘션.

## 명령

| 명령 | 하는 일 | 응답 |
| --- | --- | --- |
| `/ping` | 연결 확인 | `Pong!` |
| `/엔케팔린 현재:132 최대:142` | 부족분 10개 × 6분 · 완충 시각 멘션 예약 | 완충 시각 · 남은 시간 · 예약 안내 |
| `/엔케팔린 현재:132 최대:142 알림:False` | 계산만 | 완충 시각 · 남은 시간 |
| `/엔케팔린취소` | 예약 해제 | `완충 알림 예약 취소` |
| `/공지채널 채널:#공지 역할:@알림` | 서버 관리자 전용 · 공지 채널 · 멘션 역할 설정 | 설정 채널 · 역할 · 봇 권한 부족 경고 |
| `/공지채널해제` | 공지 중계 해제 | `림버스 컴퍼니 공지 중계 해제` |

유저당 예약 하나 — 재실행은 교체(PK 의 `ON CONFLICT`) · 서버 채널 전용 · 보관된 스레드처럼 캐시에서 빠진
채널은 DM 으로.

공지는 10분마다 Steam 스토어 이벤트 확인 — 새 공지 하나당 메시지 하나(이미지 갤러리 · 한국어 본문 임베드) ·
봇이 꺼져 있던 사이 공지는 다음 기동 때 올라온 순서대로.

같은 채널로 가는 정기 점검 시작 · 종료 멘션 — 점검 시각의 출처는 정기 업데이트 공지 이미지 OCR(지난 1년 61건 중 60건 일치) ·
못 읽은 주의 평소 시각 10:00 ~ 12:00.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 25 |
| Discord | JDA 6.5.0 — 슬래시 명령만 · 음성 모듈(opus-java) 제외 |
| Database | PostgreSQL 17 · JDBC · Flyway — 예약 · 중계한 공지 기록 · 서버별 공지 채널 · 점검 시각의 원본 |
| OCR | Tesseract 한국어 모델 — 컨테이너 이미지 전용 · 로컬 `gradlew run` 은 평소 점검 시각 |
| Test | JUnit 5 · Testcontainers · 33개 — DB 로직은 H2 대신 실제 PostgreSQL(`ON CONFLICT` · `TIMESTAMPTZ` 동작 차이) |
| Infra | Docker 멀티스테이지 · 비루트 JRE 이미지 · Docker Compose |
| Build · CI | Gradle 9.7.1 wrapper · GitHub Actions — push 마다 빌드 · 테스트 · 이미지 빌드 |

## 실행

JDK 25 · Docker 필요 · Gradle 은 wrapper 동봉 · 봇 토큰은 [Discord Developer Portal](https://discord.com/developers/applications)
→ 앱 생성 → **Bot** → **Reset Token**. 초대 URL 은 **OAuth2** → **URL Generator** 의 `bot` · `applications.commands`
스코프 + `Send Messages` · `Embed Links` · `Attach Files` 권한 · 공지 역할이 `@everyone` 이거나 멘션 허용이 꺼진 역할인 서버만 `Mention Everyone`.

```bash
git clone https://github.com/minky5004/DiscordBotPractice.git && cd DiscordBotPractice
cp .env.example .env               # DISCORD_BOT_TOKEN · DB_PASSWORD(영숫자) 채우기
docker compose up -d --wait db     # → PostgreSQL healthy
./gradlew run                      # → "봇이 정상적으로 로그인되었습니다"
```

컨테이너로는 마지막 두 줄 대신 · `gradlew run` 과 둘 중 하나만 — 같은 토큰 · 같은 DB 로 뜬 두 봇의 중복 멘션.

```bash
docker compose up -d --build       # → docker compose logs bot 에 같은 로그인 로그
```

`/` 자동완성에 명령이 없을 때 `Ctrl+R` — 전역 등록 명령의 클라이언트 캐시 지연.

## 구조

```
DiscordBotPractice/
├── .github/workflows/                push 마다 빌드 · 테스트 · 이미지 빌드
├── src/main/java/com/minky/discordbot/
│   ├── Main.java                     설정(환경 변수 → .env) · DB 연결 · Flyway 마이그레이션 · 로그인 · 예약 복구 · 명령 등록
│   ├── PingPongListener.java         /ping
│   ├── EnkephalinListener.java       /엔케팔린 · /엔케팔린취소 · 완충 시각 계산 · 멘션 · DM 폴백
│   ├── ReminderScheduler.java        유저당 예약 하나 · 단일 데몬 스레드 · 기동 시 복구
│   ├── ReminderStore.java            reminder 테이블 JDBC
│   ├── NoticeListener.java           /공지채널 · /공지채널해제 · 10분 주기 Steam 조회 · BBCode → 임베드 · 이미지 첨부
│   ├── NoticeStore.java              중계한 공지 기록 · 서버별 공지 채널 · 점검 시각 JDBC
│   └── MaintenanceAlert.java         정기 업데이트 공지 제목의 날짜 · 이미지 OCR 시각 · 점검 시작 · 종료 멘션 예약
├── src/main/resources/db/migration/  스키마 이력
├── src/test/java/…/                  단위(계산 · 설정 파싱) · 통합(저장소 · 스케줄러 — 실제 PostgreSQL)
├── Dockerfile                        JDK 빌드 → tesseract 를 얹은 비루트 JRE 이미지
├── compose.yaml                      PostgreSQL · 봇 (db 서비스를 가리키는 DB_URL · 빈 토큰의 기동 거부)
└── .env.example                      토큰 · 비밀번호 자리를 비워 둔 견본
```
