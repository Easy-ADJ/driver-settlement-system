> [📚 문서 목록](../README.md) › [🎭 유스케이스 다이어그램](./index.html) › UC-04

# 🚫 UC-04 — 중복 배치 실행 거부

| 항목 | 내용 |
|---|---|
| **주 Actor** | 시스템 ([`UC-01`](./02-uc-01-batch-execution.md)이 include) |
| **목적** | 같은 날짜를 두 번 정산해 지급액이 두 배가 되는 것을 막는다 |
| **관련 요구사항** | `FR-B-06`, `NFR-01` |
| **사전조건** | Job이 `targetDate`로 트리거됐다 |
| **사후조건** | 확정된 배치가 있으면 Job이 시작되지 않는다 |

**주 흐름**

1. 시스템이 `targetDate`로 `settlement_batches`를 조회한다
2. `CONFIRMED` 상태 배치가 없으면 통과시킨다

**대안·예외 흐름**

| # | 분기점 | 처리 |
|---|---|---|
| D1 | `CONFIRMED` 배치 존재 | `SETTLEMENT_ALREADY_CONFIRMED` 예외로 **Job 시작 전에** 거부. `RUNNING` 레코드를 만들지 않는다 |
| D2 | `FAILED` 배치만 존재 | 통과. 실패한 배치는 재실행 대상이다 → [`UC-05`](./06-uc-05-failed-restart.md) |
| D3 | `RUNNING` 배치 존재 (직전 실행이 비정상 종료) | 🚧 미확정 — 좀비 `RUNNING`을 어떻게 처리할지. 아래 참고 |
| D4 | 두 프로세스가 동시에 1단계를 통과 | 애플리케이션 검사만으로는 막히지 않는다. 부분 UNIQUE 인덱스 권장 ([정의서 §6.1](../02-requirements-specification/05-data-requirements.md)) |

> **D3은 검토가 필요한 공백이다.** 서버가 배치 도중 죽으면 `RUNNING` 레코드가 남는다. 다음 실행 때 이를 "진행 중"으로 볼지 "죽은 것"으로 볼지 정해야 한다. Spring Batch 메타 테이블의 Job 실행 상태로 판별하는 방법이 있다.
>
> **D4가 `FR-B-06`의 한계다.** 사전검사는 사람의 실수(같은 날짜 두 번 실행)를 막지만 동시 실행 경합은 막지 못한다. 결제의 `idempotency_key` UNIQUE가 하는 역할을 정산에서는 DB 제약이 해줘야 한다.

관련 다이어그램: [`SD-02`](../04-sequence-diagram/02-sd-02-duplicate-rejection.md), [`FC-01`](../05-flow-chart/01-fc-01-batch-control-flow.md)

---

**이전** → [UC-03 정산 내역 조회](./04-uc-03-settlement-query.md) · **다음** → [UC-05 실패 배치 재시작](./06-uc-05-failed-restart.md)
