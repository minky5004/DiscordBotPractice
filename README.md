# DiscordBotPractice

> 디스코드 채팅창의 `!ping` 에 `Pong!` 으로 답하는 JDA 연습 봇

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 25 |
| Library | JDA 6.5.0 |
| Build | Gradle 9.7.1 |

## 실행

### 1. 봇 토큰 발급

[Discord Developer Portal](https://discord.com/developers/applications) → **New Application** → 이름 입력 → 좌측 **Bot**

- **Reset Token** → 표시되는 토큰 복사 (창을 닫은 뒤에는 재발급뿐)
- 같은 화면 아래 **Privileged Gateway Intents** → **MESSAGE CONTENT INTENT** 켜기 → **Save Changes**

꺼진 MESSAGE CONTENT INTENT 의 대가 — 빈 문자열로 도착하는 메시지 내용 · 영영 걸리지 않는 `!ping`

### 2. 서버 초대

좌측 **OAuth2** → **OAuth2 URL Generator**

- **SCOPES**: `bot`
- **BOT PERMISSIONS**: `Send Messages`
- 아래 **GENERATED URL** 을 브라우저에 붙여넣기 → 봇을 넣을 서버 선택

### 3. 로컬 실행

```powershell
git clone https://github.com/minky5004/DiscordBotPractice.git
cd DiscordBotPractice
Copy-Item .env.example .env    # .env 를 열어 DISCORD_BOT_TOKEN= 뒤에 1번의 토큰 붙여넣기
.\gradlew.bat run              # → 콘솔에 "봇이 정상적으로 로그인되었습니다"
```

서버 채팅창에 `!ping` → 봇이 `Pong!`

## 구조

```
Main.java              .env 토큰 로딩 · JDA 로그인
PingPongListener.java  !ping 수신 · Pong! 응답
.env.example           토큰 자리를 비워 둔 견본
```
