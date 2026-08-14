> [📚 문서 목록](../README.md) › [🧩 클래스 다이어그램](./index.html) › §3

# ⚙️ 배치 계층

```mermaid
classDiagram
    direction TB

    class SettlementJobScheduler {
        <<Component>>
        -JobLauncher jobLauncher
        -Job dailySettlementJob
        +runDailySettlement() void
    }

    class DailySettlementJobConfig {
        <<Configuration>>
        +dailySettlementJob(JobRepository) Job
        +settlementStep(JobRepository) Step
        +int CHUNK_SIZE
    }

    class DuplicateBatchGuard {
        <<Component>>
        -SettlementBatchRepository batchRepository
        +assertNotConfirmed(LocalDate) void
    }

    class PaymentItemReader {
        <<Component>>
        -PaymentClient paymentClient
        -LocalDate targetDate
        +read() PaymentSummary
    }

    class DriverSettlementProcessor {
        <<Component>>
        -BigDecimal feeRate
        +process(PaymentSummary) SettlementItem
        -isSettleable(PaymentSummary) boolean
        -calculateFee(BigDecimal) BigDecimal
    }

    class SettlementItemWriter {
        <<Component>>
        -SettlementItemRepository itemRepository
        +write(Chunk~SettlementItem~) void
    }

    class SettlementJobListener {
        <<Component>>
        -SettlementBatchRepository batchRepository
        -ReconciliationService reconciliationService
        +beforeJob(JobExecution) void
        +afterJob(JobExecution) void
    }

    class ItemReader {
        <<interface>>
    }
    class ItemProcessor {
        <<interface>>
    }
    class ItemWriter {
        <<interface>>
    }
    class JobExecutionListener {
        <<interface>>
    }

    ItemReader <|.. PaymentItemReader
    ItemProcessor <|.. DriverSettlementProcessor
    ItemWriter <|.. SettlementItemWriter
    JobExecutionListener <|.. SettlementJobListener

    SettlementJobScheduler --> DailySettlementJobConfig : launches
    DailySettlementJobConfig --> PaymentItemReader
    DailySettlementJobConfig --> DriverSettlementProcessor
    DailySettlementJobConfig --> SettlementItemWriter
    DailySettlementJobConfig --> SettlementJobListener
    DailySettlementJobConfig --> DuplicateBatchGuard

    note for SettlementJobScheduler "FR-B-09 — @Scheduled 트리거<br/>targetDate = 전일<br/>서버가 죽으면 그 시각 배치는 돌지 않는다"
    note for DuplicateBatchGuard "FR-B-06 — Job 시작 전 검사<br/>애플리케이션 검사만으로는 동시 실행 경합을 막지 못한다<br/>DB 부분 UNIQUE 인덱스 병행 권장"
    note for DriverSettlementProcessor "FC-02 로직<br/>🚧 feeRate 하드코딩 vs 설정값 U4<br/>🚧 절사·반올림 규칙 U12"
    note for SettlementJobListener "NFR-06 로깅 · 상태 전이 · 대사 트리거"
```

**설계 판단**

| # | 내용 |
|---|---|
| 1 | **`DuplicateBatchGuard`를 별도 클래스로 뺐다.** `JobParametersValidator`로 구현할 수도 있으나, 이 검사는 파라미터 형식 검증이 아니라 **DB 상태 조회**다. 이름이 하는 일을 드러내는 편이 낫다 |
| 2 | **대사를 `afterJob`에서 트리거한다.** 모든 청크가 끝난 뒤 한 번 수행해야 하므로([`SD-01`](../04-sequence-diagram/01-sd-01-batch-normal-flow.md)), Step 안에 두면 청크마다 돌게 된다 |
| 3 | 🚧 `feeRate`를 상수로 둘지 `@Value` 설정값으로 뺄지 미확정 (U4). 필드로 그려둔 것은 **어느 쪽이든 이 위치**라는 뜻이며, 값의 출처를 확정한 것은 아니다 |
| 4 | `PaymentItemReader`는 `PaymentClient`만 의존한다. 🚧 페이지네이션(U3)이 확정되면 이 클래스 안에서 흡수되고 Job 구성은 바뀌지 않는다 |
| 5 | ✅ `FR-B-09`(트리거)가 `@Scheduled`로 확정돼 **`SettlementJobScheduler`가 실재한다** (U1). 서버가 EC2에 상시 떠 있으므로 EventBridge를 둘 이유가 없었다. 정산 컨테이너가 1개뿐이라 스케줄러 중복 실행 문제도 없다 |
| 6 | **`SettlementJobScheduler`는 Job을 실행만 하고 아무것도 판단하지 않는다.** 중복 검사는 `DuplicateBatchGuard`, 상태 전이는 `SettlementJobListener`의 몫이다. 수동 재실행(`FR-B-07`)이 스케줄러를 거치지 않고도 같은 Job을 실행할 수 있어야 하기 때문이다 |

---

**이전** → [도메인 계층](./02-domain-layer.md) · **다음** → [대사·조회 계층](./04-query-reconciliation-layer.md)
