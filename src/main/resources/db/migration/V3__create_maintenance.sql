-- 정기 업데이트 공지 하나당 점검 시각 하나. 알림 큐는 기동마다 여기서 다시 채운다.
-- confirmed 는 이미지를 끝까지 읽은 결과인지. 받기 · OCR 실패로 평소 시각을 둔 행은 거짓이라 다시 읽혀 덮어써진다.
CREATE TABLE maintenance (
    gid       TEXT        PRIMARY KEY,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL,
    confirmed BOOLEAN     NOT NULL
);
