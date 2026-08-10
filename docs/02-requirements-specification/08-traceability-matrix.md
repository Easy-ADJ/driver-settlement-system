> [📚 문서 목록](../README.md) › [📑 요구사항 정의서](./index.html) › §9

# 🧭 추적성 매트릭스

**이 표가 6개 산출물을 잇는 색인이다.** 특정 요구사항이 어느 다이어그램·클래스에 대응하는지 한 표에서 볼 수 있다.

각 ID가 어디에 있는지: `UC` → [유스케이스 다이어그램](../03-use-case-diagram/index.html) · `SD` → [시퀀스 다이어그램](../04-sequence-diagram/index.html) · `FC` → [플로우 차트](../05-flow-chart/index.html) · 클래스 → [클래스 다이어그램](../06-class-diagram/index.html)

| 요구사항 | 유스케이스 | 시퀀스 | 플로우 | 주요 클래스 |
|---|---|---|---|---|
| `FR-B-01` | `UC-01` | `SD-01` | `FC-01` | `DailySettlementJobConfig` |
| `FR-B-02` | `UC-01` | `SD-01`, `SD-04` | `FC-01` | `PaymentItemReader`, `PaymentClient` |
| `FR-B-03` | `UC-01` | `SD-01` | `FC-02` | `DriverSettlementProcessor` |
| `FR-B-04` | `UC-01` | `SD-01` | `FC-01` | `SettlementItemWriter`, `SettlementItemRepository` |
| `FR-B-05` | `UC-01` | `SD-01` | `FC-01` | `DailySettlementJobConfig` (chunk 설정) |
| `FR-B-06` | `UC-04` | `SD-02` | `FC-01` | `DuplicateBatchGuard`, `SettlementBatchRepository` |
| `FR-B-07` | `UC-05` | — | `FC-04` | Spring Batch 메타 (프레임워크) |
| `FR-B-08` | `UC-01`, `UC-03` | `SD-01`, `SD-05` | `FC-02` | `SettlementItem` (`tripIds`) |
| `FR-B-09` 🚧 | `UC-01` | `SD-01` (트리거) | — | 🚧 |
| `FR-B-10` 🚧 | `UC-01` | `SD-04` | `FC-01` | `PaymentClient` (재시도) |
| `FR-B-11` 🚧 | `UC-01` | `SD-01` | — | `PaymentItemReader` |
| `FR-B-12` 🚧 | `UC-01` | — | `FC-02` | `DriverSettlementProcessor` |
| `FR-R-01` | `UC-02` | `SD-03` | `FC-03` | `ReconciliationService` |
| `FR-R-02` | `UC-02` | `SD-03` | `FC-03` | `LedgerClient` |
| `FR-R-03` 🚧 | `UC-02` | `SD-03` | `FC-03` | 🚧 |
| `FR-R-04` 🚧 | `UC-02` | `SD-03` | `FC-01`, `FC-03` | `SettlementBatch` (상태 전이) |
| `FR-Q-01` | `UC-03` | `SD-05` | — | `SettlementController`, `SettlementQueryService` |
| `FR-Q-02` | `UC-03` | `SD-05` | — | `GlobalExceptionHandler`, `ErrorResponse` |
| `FR-Q-03` 🚧 | `UC-07` | — | — | 🚧 |
| `FR-Q-04` 🚧 | `UC-03` | `SD-05` | — | `SettlementController` |
| `FR-S-01` | `UC-01`, `UC-06` | `SD-01` | `FC-04` | `SettlementBatch`, `BatchStatus` |
| `FR-S-02` 🚧 | `UC-06` | — | `FC-04` | 🚧 `SettlementController` |
| `FR-S-03` 🚧 | `UC-06` | `SD-06` | — | 🚧 `LedgerClient` |
| `NFR-01` | `UC-04` | `SD-02` | `FC-01` | `DuplicateBatchGuard` |
| `NFR-02` | `UC-01` | — | — | `SettlementItem` (setter 미제공) |
| `NFR-03`, `NFR-04` | `UC-01`, `UC-02` | `SD-04` | — | `RestClientConfig` |
| `NFR-05` | `UC-03` | `SD-05` | `FC-02` | DTO (`BigDecimal` + 문자열 직렬화) |
| `NFR-06` | `UC-01` | `SD-01` | `FC-01` | `SettlementJobListener` |
| `IF-01` | `UC-01` | `SD-01`, `SD-04` | — | `PaymentClient`, `PaymentSummary` |
| `IF-02` | `UC-02` | `SD-03` | `FC-03` | `LedgerClient`, `LedgerBalance` |
| `IF-03` 🚧 | `UC-06` | `SD-06` | — | 🚧 `LedgerClient` |

**커버리지 확인**

- 확정 `FR` 전부가 최소 1개 `UC`에 대응한다
- `FR-B-07`은 시퀀스 없이 `FC-04`(상태 전이)로만 표현한다 — Spring Batch가 제공하는 기능이라 우리 코드의 상호작용이 없다
- `NFR-07`~`NFR-09`는 설정·목표치라 다이어그램 대응이 없다

---

**이전** → [제약 사항](./07-constraints.md)
