> [📚 문서 목록](../README.md) › [🎭 유스케이스 다이어그램](./index.html) › UC-01

# 🔁 UC-01 — 일일 정산 배치 실행

| 항목 | 내용 |
|---|---|
| **주 Actor** | 배치 스케줄러 |
| **보조 Actor** | 결제 서버 (`IF-01`) |
| **목적** | 전일 완료된 결제를 기사별로 집계해 지급액을 확정한다 |
| **관련 요구사항** | `FR-B-01`~`FR-B-05`, `FR-B-08`, `FR-S-01`, `NFR-06` |
| **사전조건** | ① `targetDate`가 Job 파라미터로 주어진다 ② 같은 `targetDate`로 `CONFIRMED`인 배치가 없다 ([`UC-04`](./05-uc-04-duplicate-rejection.md)) |
| **사후조건** | `settlement_batches` 1건 + 기사 수만큼의 `settlement_items`가 생성되고, 대사 통과 시 상태가 `CONFIRMED`가 된다 |

**주 흐름**

1. 스케줄러가 `targetDate`로 Job을 트리거한다
2. 시스템이 중복 실행 여부를 검사한다 → [`UC-04`](./05-uc-04-duplicate-rejection.md)
3. 시스템이 `settlement_batches`에 `RUNNING` 상태 레코드를 생성한다
4. 시스템이 결제 서버에서 해당 일자 결제 내역을 조회한다 (`IF-01`)
5. 시스템이 성공 결제만 필터링하고 기사별로 운임을 합산한다
6. 시스템이 수수료 20%를 차감해 지급액을 산출한다
7. 시스템이 기사별 `settlement_items`를 저장한다 (포함된 운행 ID 목록 포함)
8. 4~7을 청크 단위로 반복하며 청크마다 커밋한다
9. 시스템이 대사를 수행한다 → [`UC-02`](./03-uc-02-reconciliation.md)
10. 시스템이 배치 상태를 `CONFIRMED`로 전이한다

**대안·예외 흐름**

| # | 분기점 | 처리 |
|---|---|---|
| A1 | 2단계에서 이미 확정된 배치 발견 | `SETTLEMENT_ALREADY_CONFIRMED`로 거부하고 종료. **`RUNNING` 레코드를 만들지 않는다** |
| A2 | 4단계에서 결제 서버 호출 실패 | 5xx·타임아웃이면 최대 2회 재시도(`NFR-04`). 소진 시 배치 `FAILED`. 🚧 이후 처리는 U2 |
| A3 | 4단계에서 4xx 응답 | 재시도하지 않고 즉시 `FAILED` — 요청 자체가 틀린 것이므로 |
| A4 | 5단계에서 해당 일자 결제가 0건 | 정상 종료. 항목 0건의 배치를 `CONFIRMED`로 남긴다 (배치가 돌았다는 기록 자체가 필요하다) |
| A5 | 8단계 청크 처리 중 예외 | 해당 청크만 롤백하고 배치 `FAILED`. 이전 청크 결과는 남는다 → [`UC-05`](./06-uc-05-failed-restart.md) |
| A6 | 9단계 대사 불일치 | 🚧 U6 — `CONFIRMED` 보류 여부 미확정 |

> **A4를 명시한 이유**: "결제가 없으면 배치를 안 돌린 것"과 "돌렸는데 0건"은 다르다. 후자를 기록해야 "그날 정산이 왜 없나"에 답할 수 있다.

관련 다이어그램: [`SD-01`](../04-sequence-diagram/01-sd-01-batch-normal-flow.md), [`FC-01`](../05-flow-chart/01-fc-01-batch-control-flow.md), [`FC-02`](../05-flow-chart/02-fc-02-payout-calculation.md)

---

**이전** → [유스케이스 다이어그램](./01-diagram.md) · **다음** → [UC-02 정산 대사 검증](./03-uc-02-reconciliation.md)
