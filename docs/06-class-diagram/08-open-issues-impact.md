> [📚 문서 목록](../README.md) › [🧩 클래스 다이어그램](./index.html) › §8

# 🚧 미확정이 클래스 구조에 미치는 영향

U번호의 원문은 [기술서 §10 미확정 항목](../01-requirements-statement/09-open-issues.md)에 있다.

| 🚧 | 결정에 따른 변화 |
|---|---|
| U1 배치 트리거 | `@Scheduled`면 `SettlementJobScheduler` **추가**. EventBridge면 추가 없음 |
| U2 재시도 정책 | `PaymentClient`의 재시도 범위, 또는 `SkipPolicy`/`RetryTemplate` 설정 추가 |
| U3 페이지네이션 | `PaymentItemReader` 내부만 변경. Job 구성은 그대로 |
| U4 수수료율 | `DriverSettlementProcessor.feeRate`의 출처 (상수 vs `@Value`) |
| U5·U6 대사 실패 처리 | `SettlementJobListener`의 `afterJob` 분기, 통지 컴포넌트 추가 여부 |
| U7 `PAID` 전이 | API면 `SettlementController`에 엔드포인트 + Service 메서드 **추가** |
| U8 지급 분개 | `LedgerClient.recordPayoutEntries()` **추가**. 안 하면 추가 없음 |
| U9 내역서 파일 | 파일 생성기·스토리지 클라이언트 클래스 **추가** |
| U10 `trip_ids` 매핑 | `SettlementItem.tripIds`의 JPA 매핑 방식, 또는 매핑 엔티티 추가 |
| U12 절사·반올림 | `DriverSettlementProcessor.calculateFee()`의 `RoundingMode` |

> **U1·U7·U8·U9는 "클래스가 생기느냐 마느냐"를 정한다.** 회의 전에 미리 만들어두면 결정이 반대로 났을 때 지워야 하는 코드가 된다. 확정 후 추가한다.

---

**이전** → [클래스별 책임](./07-class-responsibilities.md) · **다음** → [관찰 메모](./09-observations.md)
