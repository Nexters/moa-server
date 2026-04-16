-- ============================================================
-- ShedLock 테이블 (로컬 개발 H2 전용)
-- 로컬에서 H2 기동 시 자동으로 생성되도록 설정.
-- ============================================================

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
