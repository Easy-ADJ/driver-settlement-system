> [📚 문서 목록](../README.md) › [🔀 시퀀스 다이어그램](./index.html) › SD-03

# ⚖️ SD-03 — 대사 검증

대응: [`UC-02`](../03-use-case-diagram/03-uc-02-reconciliation.md) / `FR-R-01`~`FR-R-04`

```mermaid
sequenceDiagram
    autonumber
    participant Job
    participant Recon
    participant PayClient
    participant PaySvc
    participant LedClient
    participant LedgerSvc

    Job->>Recon: reconcile batch
    Note over Recon: 정산 합계는 쓰지 않는다<br/>원장에서 받은 값이라 자기 자신과의 비교가 된다

    Recon->>PayClient: findPaymentsByDate
    PayClient->>PaySvc: GET /api/payments?date=
    PaySvc-->>PayClient: payments
    PayClient-->>Recon: 승인 완료 건만 합산

    Recon->>LedClient: findUnpaidDrivers
    LedClient->>LedgerSvc: GET /api/ledger/unpaid?date=
    LedgerSvc-->>LedClient: data
    LedClient-->>Recon: 미지급금 합계

    alt 합계 일치
        Recon-->>Job: MATCHED
        Job->>Job: confirm batchId - 수동 확정과 같은 경로
    else 합계 불일치
        Recon-->>Job: MISMATCHED
        Note over Recon: log.error 에 차이 금액을 남긴다<br/>없으면 어디를 볼지 알 수 없다
        Note over Job: CONFIRMED 로 올리지 않는다<br/>사람이 확인 후 POST /{batchId}/confirm
    else 한쪽 호출 실패
        PaySvc--xPayClient: 5xx 또는 타임아웃
        Note over PayClient: 최대 2회 재시도 - NFR-04
        PayClient--xRecon: PAYMENT_SERVICE_UNAVAILABLE
        Recon-->>Job: SKIPPED - 확인 못 했다
        Note over Job: 배치는 죽지 않는다<br/>다만 확정도 하지 않는다
```

**짚어둘 점**

- **비교 대상이 결제 ↔ 원장이다.** 정산 합계를 쓰지 않는 이유는, 정산 금액이 원장에서 받아온 값이라 그 둘을 비교하면 **같은 값을 자기 자신과 대조**하는 것이 되어 항상 일치하기 때문이다. 결제 서버가 독립된 두 번째 출처다.
- `Recon`은 `ledger_entries`를 SUM하지 않는다. 원장 API로만 잔액을 얻는다 (`FR-R-02`). 잔액 계산 로직이 두 서버에 중복되면 어느 쪽이 맞는지 판단할 근거가 사라진다.
- **`MISMATCHED`와 `SKIPPED`는 둘 다 확정을 막지만 다른 정보다.** 전자는 "틀렸다"라 금액을 조사해야 하고, 후자는 "확인 못 했다"라 상대 서버가 돌아온 뒤 다시 대사하면 된다.
- ⚠️ **절사·반올림 규칙(U12 / `FR-B-12`)이 원장과 다르면 이 `alt`의 두 번째 분기가 상시 발생한다.** 계산과 저장이 모두 정상인데 대사만 실패하는 상황이며, 원인 파악이 가장 어려운 유형이다. 원장 담당자와의 합의가 이 다이어그램의 선행 조건이다.
- 세 번째 분기(원장 호출 실패)와 두 번째 분기(불일치)는 **다른 상황**이다. 전자는 "모르겠다", 후자는 "틀렸다"다. 같은 처리로 묶지 않는 편이 좋다.

판정 로직의 분기 조건은 [`FC-03`](../05-flow-chart/03-fc-03-reconciliation-decision.md)에 있다.

---

**이전** → [SD-02 중복 실행 거부](./02-sd-02-duplicate-rejection.md) · **다음** → [SD-04 결제 서버 장애 시 Reader 실패](./04-sd-04-payment-service-failure.md)
