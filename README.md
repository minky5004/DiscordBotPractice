# DiscordBotPractice

[![ci](https://github.com/minky5004/DiscordBotPractice/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/minky5004/DiscordBotPractice/actions/workflows/ci.yml)

> 필요해진 기능을 그때그때 공부해 들여오는 디스코드 봇 연습장 — 친구 서버에서 쌓인 림버스 컴퍼니 도우미 기능들

기능이 들어온 순서 — `!ping` 텍스트 명령 → `/ping` 슬래시 명령 → 엔케팔린 완충 멘션 예약(PostgreSQL 영속화 · Docker) →
Steam 공지 중계 → 정기 점검 알림(공지 이미지 OCR) → 인격 조회 · 검색(Components V2 화면) → 키워드 · E.G.O · 기프트 · 환상체 조회 → `/help`.

실제 `/인격 이름:엄숙한 애도` 응답 — 게임 한국어 원문에 위키 수치 · 일러스트를 붙인 컨테이너.
첫 화면은 훑는 자리, 버튼 하나가 스킬 하나.

```
## [로보토미 E.G.O::엄숙한 애도] 이상 · ★★★          [인격 일러스트]
내성  참격 ×1.0 · 관통 ×0.5 · 타격 ×2.0
────────────────────────────────
**`1`  떠난이에게 축하를**
-# 오만 · 관통 · 위력 4 · 코인 +4 × 2
**`2`  남은자에게 엄숙한 애도를**
-# 우울 · 관통 · 위력 4 · 코인 +6 × 2
…
**`7`  서포트 패시브 · 구원의 손**
-# 우울 6 보유
-# 수치: Limbus Company Wiki (wiki.gg)

[목록*] [1 떠난이에게 축하를] [2 남은자에게 엄숙한 애…] [3 이상으로 장례는 이상…] [4 관에서나비가날아오리라]
[5 쏘아라.쏘으리로다.] [6 죽어가는나비를본다.] [7 구원의 손]
```

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 25 |
| Discord | JDA 6.5.0 — 슬래시 명령만 · 음성 모듈(opus-java) 제외 |
| Data | 게임 설치 폴더의 한국어 · 영어 텍스트 + wiki.gg MediaWiki API — 24시간마다 다시 읽음 · 의존성 없이 `HttpClient` · JDA `DataObject` |
| Database | PostgreSQL 17 · JDBC · Flyway — 예약 · 중계한 공지 기록 · 서버별 공지 채널 · 점검 시각의 원본 |
| OCR | Tesseract 한국어 모델 — 컨테이너 이미지 전용 · 로컬 `gradlew run` 은 평소 점검 시각 |
| Test | JUnit 5 · Testcontainers · 125개 — DB 로직은 H2 대신 실제 PostgreSQL(`ON CONFLICT` · `TIMESTAMPTZ` 동작 차이) |
| Infra | Docker 멀티스테이지 · 비루트 JRE 이미지 · Docker Compose |
| Build · CI | Gradle 9.7.1 wrapper · GitHub Actions — push 마다 빌드 · 테스트 · 이미지 빌드 |

## 실행

JDK 25 · Docker 필요 · Gradle 은 wrapper 동봉 · 봇 토큰은 [Discord Developer Portal](https://discord.com/developers/applications)
→ 앱 생성 → **Bot** → **Reset Token**. 초대 URL 은 **OAuth2** → **URL Generator** 의 `bot` · `applications.commands`
스코프 + `Send Messages` · `Embed Links` · `Attach Files` 권한 · 공지 역할이 `@everyone` 이거나 멘션 허용이 꺼진 역할인 서버만 `Mention Everyone`.

```bash
git clone https://github.com/minky5004/DiscordBotPractice.git && cd DiscordBotPractice
cp .env.example .env               # DISCORD_BOT_TOKEN · DB_PASSWORD(영숫자) · LIMBUS_DIR(게임 폴더 · 선택) 채우기
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
│   ├── HelpListener.java             /help · 등록 목록 그대로 · DM 폴백
│   ├── EnkephalinListener.java       /엔케팔린 · /엔케팔린취소 · 완충 시각 계산 · 멘션 · DM 폴백
│   ├── ReminderScheduler.java        유저당 예약 하나 · 단일 데몬 스레드 · 기동 시 복구
│   ├── ReminderStore.java            reminder 테이블 JDBC
│   ├── NoticeListener.java           /공지채널 · /공지채널해제 · 10분 주기 Steam 조회 · BBCode → 임베드 · 이미지 첨부
│   ├── NoticeStore.java              중계한 공지 기록 · 서버별 공지 채널 · 점검 시각 JDBC
│   ├── MaintenanceAlert.java         정기 업데이트 공지 제목의 날짜 · 이미지 OCR 시각 · 점검 시작 · 종료 멘션 예약
│   ├── IdentityCatalog.java          게임 텍스트 · 위키 수치 · 그림 주소를 영어 이름으로 맞춘 인격 목록 · 키워드 사전 · 24시간 갱신
│   ├── EgoCatalog.java               E.G.O 텍스트 · 위키 EGPage 수치 · 일러스트 · 같은 갱신에서 함께
│   ├── GiftCatalog.java              거울 던전 기프트 텍스트 · 위키 Lua 데이터 모듈 · 강화판 · 같은 갱신에서 함께
│   ├── AbnoCatalog.java              위키 AbnoInfo 문서 목록 · 게임 도감의 한국어 관찰 로그 · 같은 갱신에서 함께
│   ├── IdentityListener.java         /인격 · 자동완성 · 컨테이너 · 버튼 페이지 · 죄악 색 띠 · 키워드 메뉴
│   ├── IdentitySearchListener.java   /인격검색 · 스킬 단위 조건 · /인격 상세로 잇는 결과 버튼
│   ├── EgoListener.java              /에고 · 자동완성 · 각성 · 침식 · 패시브 한 화면
│   ├── GiftListener.java             /기프트 · 자동완성 · 강화판 버튼
│   ├── GiftSearchListener.java       /기프트검색 · 키워드 · 등급 · 죄악 · /기프트 화면으로 잇는 결과 버튼
│   ├── KeywordListener.java          /키워드 · 자동완성 · 설명 · 쓰는 인격 · E.G.O · /인격 키워드 메뉴의 응답
│   └── AbnoListener.java             /환상체 · 자동완성 · 위험 등급 색 띠 · 관찰 로그 버튼
├── src/main/resources/db/migration/  스키마 이력
├── src/test/java/…/                  단위(계산 · 설정 파싱) · 통합(저장소 · 스케줄러 — 실제 PostgreSQL)
├── Dockerfile                        JDK 빌드 → tesseract 를 얹은 비루트 JRE 이미지
├── compose.yaml                      PostgreSQL · 봇 (db 서비스를 가리키는 DB_URL · 빈 토큰의 기동 거부 · 게임 폴더 읽기 전용 마운트)
└── .env.example                      토큰 · 비밀번호 자리를 비워 둔 견본
```
