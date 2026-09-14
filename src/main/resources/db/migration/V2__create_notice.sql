-- 이미 중계한 Steam 공지. 비어 있으면 첫 기동으로 보고 알림 없이 채운다.
CREATE TABLE posted_notice (
    gid TEXT PRIMARY KEY
);

-- 서버당 공지 채널 하나. 재설정이 앞의 설정을 교체하는 규칙을 PK 가 강제한다.
CREATE TABLE notice_channel (
    guild_id   BIGINT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    role_id    BIGINT
);
