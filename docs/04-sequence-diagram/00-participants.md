> [📚 문서 목록](../README.md) › [🔀 시퀀스 다이어그램](./index.html) › §0

# 🎬 등장 요소

| 항목 | 값 |
|---|---|
| 대상 시스템 | 운전자 정산 시스템 (`driver-settlement-system`) |
| 상위 문서 | [요구사항 정의서](../02-requirements-specification/index.html) · [유스케이스 다이어그램](../03-use-case-diagram/index.html) |
| 상태 | 초안 — 🚧 흐름은 **08.11(화) 중간 회의** 후 확정 |

---

참여자 이름은 [클래스 다이어그램](../06-class-diagram/index.html)의 클래스명과 일치한다.

| 참여자 | 종류 | 역할 |
|---|---|---|
| `Scheduler` | 외부 | Job 트리거 (🚧 방식 미확정 — U1) |
| `Job` | 내부 | `DailySettlementJobConfig`가 구성한 Spring Batch Job |
| `Guard` | 내부 | `DuplicateBatchGuard` — 중복 실행 사전검사 |
| `Reader` | 내부 | `PaymentItemReader` |
| `Processor` | 내부 | `DriverSettlementProcessor` |
| `Writer` | 내부 | `SettlementItemWriter` |
| `Recon` | 내부 | `ReconciliationService` |
| `PayClient` | 내부 | `PaymentClient` — 결제 서버 호출 전담 |
| `LedClient` | 내부 | `LedgerClient` — 원장 서버 호출 전담 |
| `Controller` | 내부 | `SettlementController` |
| `QueryService` | 내부 | `SettlementQueryService` |
| `SettlementDB` | 내부 | 정산 소유 테이블 — `settlement_batches`, `settlement_items`, Batch 메타 |
| `PaymentSvc` | 외부 | 결제 서버 |
| `LedgerSvc` | 외부 | 원장 서버 |

> **`SettlementDB`는 정산 소유 테이블만이다.** `payments`·`ledger_*`로 가는 화살표는 이 문서 어디에도 없다 — 반드시 `PayClient`/`LedClient`를 거쳐 외부 서버로 간다 (`CST-02`).

---

**다음** → [SD-01 정산 배치 정상 흐름](./01-sd-01-batch-normal-flow.md)
