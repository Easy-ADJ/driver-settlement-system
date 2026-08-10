> [📚 문서 목록](../README.md) › [🔀 시퀀스 다이어그램](./index.html) › SD-03

# ⚖️ SD-03 — 대사 검증

대응: [`UC-02`](../03-use-case-diagram/03-uc-02-reconciliation.md) / `FR-R-01`~`FR-R-04`

```mermaid
sequenceDiagram
    autonumber
    participant Job
    participant Recon
    participant SettlementDB
    participant LedClient
    participant LedgerSvc

    Job->>Recon: reconcile batchId
    Recon->>SettlementDB: SUM amount FROM settlement_items WHERE batch=batchId
    SettlementDB-->>Recon: 정산 항목 합계

    Recon->>LedClient: findDriverPayableBalance
    Note over Recon,LedClient: 🚧 accountId 조회 방법 미확정 - IF-02 Q3
    LedClient->>LedgerSvc: GET /api/ledger/accounts/{accountId}/balance
    LedgerSvc-->>LedClient: balance
    LedClient-->>Recon: LedgerBalance

    alt 합계 일치
        Recon-->>Job: MATCHED
        Job->>SettlementDB: UPDATE status=CONFIRMED
    else 합계 불일치
        Recon-->>Job: MISMATCHED - RECONCILIATION_MISMATCH
        Note over Job,SettlementDB: 🚧 U6 미확정<br/>A CONFIRMED 보류<br/>B 확정하되 불일치 기록
        Note over Recon: 🚧 U5 미확정<br/>통지 수단 - 로그 / 응답 플래그 / 대시보드 배지
    else 원장 호출 실패
        LedgerSvc--xLedClient: 5xx 또는 타임아웃
        Note over LedClient: 최대 2회 재시도 - NFR-04
        LedClient--xRecon: LEDGER_SERVICE_UNAVAILABLE
        Recon-->>Job: 대사 불가
        Note over Job: 🚧 U6과 동일 논점
    end
```

**짚어둘 점**

- `Recon`은 `ledger_entries`를 SUM하지 않는다. 원장 API로만 잔액을 얻는다 (`FR-R-02`). 잔액 계산 로직이 두 서버에 중복되면 어느 쪽이 맞는지 판단할 근거가 사라진다.
- ⚠️ **절사·반올림 규칙(U12 / `FR-B-12`)이 원장과 다르면 이 `alt`의 두 번째 분기가 상시 발생한다.** 계산과 저장이 모두 정상인데 대사만 실패하는 상황이며, 원인 파악이 가장 어려운 유형이다. 원장 담당자와의 합의가 이 다이어그램의 선행 조건이다.
- 세 번째 분기(원장 호출 실패)와 두 번째 분기(불일치)는 **다른 상황**이다. 전자는 "모르겠다", 후자는 "틀렸다"다. 같은 처리로 묶지 않는 편이 좋다.

판정 로직의 분기 조건은 [`FC-03`](../05-flow-chart/03-fc-03-reconciliation-decision.md)에 있다.

---

**이전** → [SD-02 중복 실행 거부](./02-sd-02-duplicate-rejection.md) · **다음** → [SD-04 결제 서버 장애 시 Reader 실패](./04-sd-04-payment-service-failure.md)
