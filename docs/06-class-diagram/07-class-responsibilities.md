> [📚 문서 목록](../README.md) › [🧩 클래스 다이어그램](./index.html) › §7

# 🗂️ 클래스별 책임

이름만으로 드러나지 않는 것만 적는다.

| 클래스 | 책임 | 관련 |
|---|---|---|
| `SettlementJobScheduler` | 정해진 시각에 전일 `targetDate`로 Job을 실행만 한다. 중복 판정·상태 전이를 하지 않는다 | `FR-B-09`, `NFR-10` |
| `DailySettlementJobConfig` | Job·Step 정의와 청크 크기. 비즈니스 로직을 담지 않는다 | `FR-B-01`, `FR-B-05` |
| `DuplicateBatchGuard` | `targetDate`로 확정 배치를 조회해 Job 시작 여부를 판정 | `FR-B-06` |
| `PaymentItemReader` | 결제 내역을 청크 분량씩 공급. 🚧 페이지네이션이 여기 들어온다 | `FR-B-02`, `FR-B-11` |
| `DriverSettlementProcessor` | 성공 결제 필터 → 기사별 합산 → 수수료 차감. **[`FC-02`](../05-flow-chart/02-fc-02-payout-calculation.md) 전체가 이 클래스다** | `FR-B-03`, `FR-B-12` |
| `SettlementItemWriter` | 항목 저장. `tripIds`를 빠뜨리지 않는 것이 이 클래스의 실질 책임 | `FR-B-04`, `FR-B-08` |
| `SettlementJobListener` | 배치 상태 전이, 대사 트리거, 실행 로그 | `FR-S-01`, `FR-R-01`, `NFR-06` |
| `ReconciliationService` | 정산 합계와 원장 잔액 대조 및 판정 | `FR-R-01`, `FR-R-02` |
| `SettlementQueryService` | 조회 결과를 "계산 근거가 보이는" 응답으로 조립 | `FR-Q-01` |
| `PaymentClient` / `LedgerClient` | 외부 HTTP 호출을 아는 유일한 지점. 타임아웃·재시도·응답 매핑 | `IF-01`~`IF-03`, `NFR-03`, `NFR-04` |
| `RestClientConfig` | 타임아웃·재시도 정책과 환경변수 기반 URL 주입 | `NFR-03`, `CST-03` |
| `GlobalExceptionHandler` | 예외를 공통 에러 포맷으로 변환 | `FR-Q-02` |

---

**이전** → [예외 계층](./06-exception-layer.md) · **다음** → [🚧 미확정이 클래스 구조에 미치는 영향](./08-open-issues-impact.md)
