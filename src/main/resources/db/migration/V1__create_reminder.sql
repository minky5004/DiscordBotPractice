-- 유저당 예약 하나. 재실행이 앞의 예약을 교체하는 규칙을 PK 가 강제한다.
CREATE TABLE reminder (
    user_id    BIGINT      PRIMARY KEY,
    channel_id BIGINT      NOT NULL,
    fire_at    TIMESTAMPTZ NOT NULL
);
