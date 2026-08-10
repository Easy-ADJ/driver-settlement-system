> [📚 문서 목록](../README.md) › [🔀 시퀀스 다이어그램](./index.html) › SD-01

# 🔁 SD-01 — 정산 배치 정상 흐름

대응: [`UC-01`](../03-use-case-diagram/02-uc-01-batch-execution.md) / `FR-B-01`~`FR-B-05`, `FR-B-08`, `FR-S-01`, `NFR-06`

참여자 정의는 [§0 등장 요소](./00-participants.md)에 있다.

```mermaid
sequenceDiagram
    autonumber
    participant Scheduler
    participant Job
    participant Guard
    participant Reader
    participant PayClient
    participant PaymentSvc
    participant Processor
    participant Writer
    participant Recon
    participant SettlementDB

    Scheduler->>Job: launch targetDate=2026-08-10
    Note over Scheduler,Job: 🚧 트리거 방식 미확정 - EventBridge vs @Scheduled

    Job->>Guard: assertNotConfirmed targetDate
    Guard->>SettlementDB: findConfirmedByTargetDate
    SettlementDB-->>Guard: 없음
    Guard-->>Job: 통과

    Job->>SettlementDB: INSERT settlement_batches status=RUNNING
    SettlementDB-->>Job: batchId

    loop 청크 단위 반복 - 예 100건
        Job->>Reader: read
        Reader->>PayClient: findPaymentsByDate targetDate
        PayClient->>PaymentSvc: GET /api/payments?date=2026-08-10
        PaymentSvc-->>PayClient: 결제 목록
        PayClient-->>Reader: List of PaymentSummary
        Reader-->>Job: 아이템 청크

        Job->>Processor: process 청크
        Note over Processor: 성공 결제만 필터<br/>기사별 운임 합산<br/>수수료 20% 차감
        Processor-->>Job: List of SettlementItem

        Job->>Writer: write 항목들
        Writer->>SettlementDB: INSERT settlement_items
        Note over Writer,SettlementDB: tripIds 포함 - FR-B-08 추적성
        SettlementDB-->>Writer: ok
        Note over Job,SettlementDB: 청크 커밋 - FR-B-05
    end

    Job->>Recon: reconcile batchId
    Note over Recon: 상세는 SD-03
    Recon-->>Job: MATCHED

    Job->>SettlementDB: UPDATE status=CONFIRMED
    SettlementDB-->>Job: ok
    Job-->>Scheduler: COMPLETED
```

**짚어둘 점**

- **`RUNNING` 레코드는 사전검사를 통과한 뒤에 만든다** (4~6단계 순서). 거부된 실행이 흔적을 남기지 않게 하려는 의도다 ([`UC-04` D1](../03-use-case-diagram/05-uc-04-duplicate-rejection.md)).
- 청크 커밋이 루프 안에 있다. Job 전체가 하나의 트랜잭션이 아니므로, 도중에 실패해도 이전 청크 결과는 DB에 남는다 (`FR-B-05`, [`UC-01` A5](../03-use-case-diagram/02-uc-01-batch-execution.md)).
- 대사는 **모든 청크가 끝난 뒤 한 번** 수행한다. 청크마다 대사하면 원장 잔액이 계속 변하는 중간 상태와 비교하게 된다.
- `Reader`는 `PayClient`만 알고 HTTP를 모른다. 반대로 `PayClient`만 URL·타임아웃·재시도를 안다.

---

**이전** → [등장 요소](./00-participants.md) · **다음** → [SD-02 중복 실행 거부](./02-sd-02-duplicate-rejection.md)
