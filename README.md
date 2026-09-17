# DiscordBotPractice

[![ci](https://github.com/minky5004/DiscordBotPractice/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/minky5004/DiscordBotPractice/actions/workflows/ci.yml)

> 림버스 컴퍼니 엔케팔린 완충 시각 계산 · 그 시각 **봇 재시작에도 사라지지 않는** 멘션 예약 · Steam 공식 공지의 한국어 채널 중계 · 인격 조회 · 조건 검색

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

`3` 을 누른 화면 — 왼쪽 색 띠는 그 스킬의 죄악 · 오른쪽 그림은 스킬 아이콘으로 바뀐다.

```
### 이상으로 장례는 이상이오                          [스킬 아이콘]
-# [로보토미 E.G.O::엄숙한 애도] 이상 · ★★★
나태 · 관통 · 위력 4 · 코인 +3 × 4
────────────────────────────────
보유한 산나비·죽은나비 5 마다 기본 위력 +1 (최대 4)
[합 승리시] 침잠 횟수 3 증가
[공격 종료시] 재장전
**코인 1** 산나비·죽은나비 1 소모 · [적중시] 소모한 산나비·죽은나비만큼 나비 부여
**코인 4** [적중시] 대상의 모든 나비만큼 우울 피해
```

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
| `/인격 이름:엄숙한 애도` | 인격 187종 조회 · 이름 칸은 자동완성 | 스킬 · 패시브 목록 · 버튼으로 하나씩 · 나만 보이는 응답 |
| `/인격 이름:엄숙한 애도 공개:True` | 같은 조회 | 채널 공개 |
| `/인격검색 죄악:분노 유형:관통` | 조건에 맞는 인격 목록 · 조건 다섯 전부 선택 | 번호 목록 · 버튼 하나가 인격 상세 |

유저당 예약 하나 — 재실행은 교체(PK 의 `ON CONFLICT`) · 서버 채널 전용 · 보관된 스레드처럼 캐시에서 빠진
채널은 DM 으로.

공지는 10분마다 Steam 스토어 이벤트 확인 — 새 공지 하나당 메시지 하나(이미지 갤러리 · 한국어 본문 임베드) ·
봇이 꺼져 있던 사이 공지는 다음 기동 때 올라온 순서대로.

같은 채널로 가는 정기 점검 시작 · 종료 멘션 — 점검 시각의 출처는 정기 업데이트 공지 이미지 OCR(지난 1년 61건 중 60건 일치) ·
못 읽은 주의 평소 시각 10:00 ~ 12:00.

인격 조회의 텍스트는 설치된 게임의 한국어 파일 · 수치 · 그림은 [림버스 컴퍼니 위키](https://limbuscompany.wiki.gg) — 인격 안에서
영어 이름으로 맞춘 짝(인격 187 · 스킬 842 중 829). 그림은 위키 문서에 적힌 파일 이름 그대로 — 전신 일러스트 우선 · 대체는 대기
스프라이트 · 인격 187 중 185. `LIMBUS_DIR` 없는 기동 — 인격 명령 둘이 빠진 목록.

검색의 죄악 · 유형은 스킬 단위 — 분노 스킬 따로 · 관통 스킬 따로인 인격이 빠진 목록. 수감자 · 등급 · 시즌은 인격 단위 ·
조건끼리는 AND · 아무 조건 없는 실행은 전체 목록. 결과 버튼이 여는 곳은 `/인격` 상세 화면.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 25 |
| Discord | JDA 6.5.0 — 슬래시 명령만 · 음성 모듈(opus-java) 제외 |
| Data | 게임 설치 폴더의 한국어 · 영어 텍스트 + wiki.gg MediaWiki API — 24시간마다 다시 읽음 · 의존성 없이 `HttpClient` · JDA `DataObject` |
| Database | PostgreSQL 17 · JDBC · Flyway — 예약 · 중계한 공지 기록 · 서버별 공지 채널 · 점검 시각의 원본 |
| OCR | Tesseract 한국어 모델 — 컨테이너 이미지 전용 · 로컬 `gradlew run` 은 평소 점검 시각 |
| Test | JUnit 5 · Testcontainers · 75개 — DB 로직은 H2 대신 실제 PostgreSQL(`ON CONFLICT` · `TIMESTAMPTZ` 동작 차이) |
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
│   ├── EnkephalinListener.java       /엔케팔린 · /엔케팔린취소 · 완충 시각 계산 · 멘션 · DM 폴백
│   ├── ReminderScheduler.java        유저당 예약 하나 · 단일 데몬 스레드 · 기동 시 복구
│   ├── ReminderStore.java            reminder 테이블 JDBC
│   ├── NoticeListener.java           /공지채널 · /공지채널해제 · 10분 주기 Steam 조회 · BBCode → 임베드 · 이미지 첨부
│   ├── NoticeStore.java              중계한 공지 기록 · 서버별 공지 채널 · 점검 시각 JDBC
│   ├── MaintenanceAlert.java         정기 업데이트 공지 제목의 날짜 · 이미지 OCR 시각 · 점검 시작 · 종료 멘션 예약
│   ├── IdentityCatalog.java          게임 텍스트 · 위키 수치 · 그림 주소를 영어 이름으로 맞춘 인격 목록 · 24시간 갱신
│   ├── IdentityListener.java         /인격 · 자동완성 · 컨테이너 · 버튼 페이지 · 죄악 색 띠
│   └── IdentitySearchListener.java   /인격검색 · 스킬 단위 조건 · /인격 상세로 잇는 결과 버튼
├── src/main/resources/db/migration/  스키마 이력
├── src/test/java/…/                  단위(계산 · 설정 파싱) · 통합(저장소 · 스케줄러 — 실제 PostgreSQL)
├── Dockerfile                        JDK 빌드 → tesseract 를 얹은 비루트 JRE 이미지
├── compose.yaml                      PostgreSQL · 봇 (db 서비스를 가리키는 DB_URL · 빈 토큰의 기동 거부 · 게임 폴더 읽기 전용 마운트)
└── .env.example                      토큰 · 비밀번호 자리를 비워 둔 견본
```
