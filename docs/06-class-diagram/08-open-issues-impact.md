> [📚 문서 목록](../README.md) › [🧩 클래스 다이어그램](./index.html) › §8

# 🚧 미확정이 클래스 구조에 미치는 영향

U번호의 원문은 [기술서 §10 미확정 항목](../01-requirements-statement/09-open-issues.md)에 있다.

| 🚧 | 결정에 따른 변화 |
|---|---|
| U2 재시도 정책 | `PaymentClient`의 재시도 범위, 또는 `SkipPolicy`/`RetryTemplate` 설정 추가 |
| U3 페이지네이션 | `PaymentItemReader` 내부만 변경. Job 구성은 그대로 |
| U4 수수료율 | `DriverSettlementProcessor.feeRate`의 출처 (상수 vs `@Value`) |
| U9 내역서 파일 | 파일 생성기·스토리지 클라이언트 클래스 **추가** |
| U10 `trip_ids` 매핑 | `SettlementItem.tripIds`의 JPA 매핑 방식, 또는 매핑 엔티티 추가. **FK 참조 여부는 확정됐고**(불가) 저장 방식만 남았다 |
| U12 절사·반올림 | `DriverSettlementProcessor.calculateFee()`의 `RoundingMode` |

> **U9는 "클래스가 생기느냐 마느냐"를 정한다.** 회의 전에 미리 만들어두면 결정이 반대로 났을 때 지워야 하는 코드가 된다. 확정 후 추가한다.

## ✅ 닫힌 항목

| 🚧 | 결정 | 구조에 반영된 결과 |
|---|---|---|
| U1 배치 트리거 | Spring `@Scheduled` | `SettlementJobScheduler`를 [§0 패키지 구조](./00-package-structure.md)·[§3 배치 계층](./03-batch-layer.md)에 **확정 반영했다** |
| U7 `PAID` 전이 | API로 노출하되 `/confirm` → `/pay` 둘로 분리 | `SettlementLifecycleService` **추가**, `SettlementBatchController`에 엔드포인트 2개, `Settlement.markPaid()` |
| U8 지급 분개 | 기록한다 | `LedgerClient.recordPayoutEntry()` **추가**. 확정과 같은 트랜잭션이라 실패하면 확정도 롤백된다 |
| U5·U6 대사 실패 처리 | 로그+응답 노출 / 확정 보류 | `ReconciliationService` **추가**, `SettlementJobListener.afterJob`에 분기. **통지 컴포넌트는 추가하지 않았다** |
| U11 테이블 소유권 강제 | DB 인스턴스 분리로 대체 | `repository`가 정산 DB만 보는 것이 [§1 규칙 4](./01-layer-dependencies.md)에서 코드 규칙이 아니라 구성으로 바뀌었다 |

---

**이전** → [클래스별 책임](./07-class-responsibilities.md) · **다음** → [관찰 메모](./09-observations.md)
