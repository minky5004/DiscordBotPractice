-- 정기 업데이트 공지 하나당 점검 시각 하나. 알림 큐는 기동마다 여기서 다시 채운다.
CREATE TABLE maintenance (
    gid       TEXT        PRIMARY KEY,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL
);
