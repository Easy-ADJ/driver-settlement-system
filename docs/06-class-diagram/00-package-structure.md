> [📚 문서 목록](../README.md) › [🧩 클래스 다이어그램](./index.html) › §0

# 📦 패키지 구조

| 항목 | 값 |
|---|---|
| 대상 시스템 | 운전자 정산 시스템 (`driver-settlement-system`) |
| 패키지 루트 | `com.example.driversettlementsystem` |
| 상위 문서 | [요구사항 정의서](../02-requirements-specification/index.html) · [시퀀스 다이어그램](../04-sequence-diagram/index.html) · [플로우 차트](../05-flow-chart/index.html) |
| 상태 | **설계안** — 현재 레포에는 `DriverSettlementSystemApplication` 하나만 존재한다. 아래는 전부 미구현이다 |

> 클래스명은 [시퀀스 다이어그램](../04-sequence-diagram/00-participants.md)의 참여자명과 일치한다. 명명은 `CST-05`(클래스 `PascalCase`, 메서드·변수 `camelCase`)를 따른다.

---

```
com.example.driversettlementsystem
├── DriverSettlementSystemApplication      (기존)
├── config
│   └── RestClientConfig
└── settlement
    ├── controller   SettlementController
    ├── service      SettlementQueryService · ReconciliationService
    ├── batch        DailySettlementJobConfig · SettlementJobScheduler
    │                PaymentItemReader · DriverSettlementProcessor
    │                SettlementItemWriter · DuplicateBatchGuard
    │                SettlementJobListener
    ├── domain       SettlementBatch · SettlementItem
    │                BatchStatus · PayoutStatus · ReconciliationStatus
    ├── repository   SettlementBatchRepository · SettlementItemRepository
    ├── client       PaymentClient · LedgerClient · PaymentSummary · LedgerBalance
    ├── dto          SettlementResponse · DriverSettlementResponse
    └── exception    DuplicateSettlementException · ReconciliationMismatchException
                     InvalidStateTransitionException · ExternalServiceException
                     GlobalExceptionHandler · ErrorResponse
```

---

**다음** → [계층 의존 관계](./01-layer-dependencies.md)
