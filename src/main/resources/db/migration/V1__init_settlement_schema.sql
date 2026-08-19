-- 정산 서버가 소유하는 테이블 2개. 확정 ERD(erd.md) 기준이다.
--
-- 식별자를 큰따옴표로 감싸지 않는다. PostgreSQL이 소문자로 접어 batches·settlements가
-- 되지만, 엔티티의 @Table(name = "BATCHES")도 따옴표 없이 나가므로 그대로 매칭된다.
-- 따옴표를 쓰면 이후 모든 쿼리에 따옴표를 붙여야 해서 훨씬 비싸다.

CREATE TABLE BATCHES (
    batch_id              BIGSERIAL PRIMARY KEY,
    target_date           DATE        NOT NULL,
    status                VARCHAR(20) NOT NULL,
    executed_at           TIMESTAMPTZ NOT NULL,
    total_payout_amount   NUMERIC(19, 2),
    reconciliation_status VARCHAR(20),
    confirmed_at          TIMESTAMPTZ
);

-- 🔑 이 인덱스가 "중복 없이 한 번만"의 마지막 방어선이다.
-- CONFIRMED인 행에만 UNIQUE를 걸어서, 같은 날짜의 실패·재시도 이력(RUNNING/FAILED)은
-- 여러 건 남기면서 확정만 1건으로 강제한다. 애플리케이션 사전검사(DuplicateBatchGuard)는
-- 두 프로세스가 동시에 검사를 통과하는 경합을 막지 못한다.
CREATE UNIQUE INDEX uq_batches_confirmed_date
    ON BATCHES (target_date)
    WHERE status = 'CONFIRMED';

-- 배치 대상 일자 조회용 (조회 API·중복 검사)
CREATE INDEX idx_batches_target_date ON BATCHES (target_date);

CREATE TABLE SETTLEMENTS (
    batch_id      BIGINT         NOT NULL REFERENCES BATCHES (batch_id),
    driver_id     BIGINT         NOT NULL,
    ledger_id     BIGINT,
    fare_total    NUMERIC(19, 2) NOT NULL,
    fee_amount    NUMERIC(19, 2) NOT NULL,
    amount        NUMERIC(19, 2) NOT NULL,
    payout_status VARCHAR(20)    NOT NULL,
    PRIMARY KEY (batch_id, driver_id)
);

-- driver_id·ledger_id에 FK가 없는 것은 의도된 것이다. 각각 로그인 DB와 원장 DB에 있어
-- 경계를 넘는 FK가 물리적으로 불가능하다. ID 값만 보관하며, 불일치는 대사가 잡는다.
CREATE INDEX idx_settlements_driver ON SETTLEMENTS (driver_id);
