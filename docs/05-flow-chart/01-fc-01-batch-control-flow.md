> [📚 문서 목록](../README.md) › [🧭 플로우 차트](./index.html) › FC-01

# 🔁 FC-01 — 정산 배치 Job 제어 흐름

| 항목 | 값 |
|---|---|
| 대상 시스템 | 운전자 정산 시스템 (`driver-settlement-system`) |
| 상위 문서 | [요구사항 정의서](../02-requirements-specification/index.html) · [유스케이스 다이어그램](../03-use-case-diagram/index.html) |
| 상태 | 초안 — 🚧 분기는 **08.15(토) 2차 회의** 후 확정 |

[시퀀스 다이어그램](../04-sequence-diagram/index.html)이 "누가 누구를 부르는가"를 보여준다면, 이 문서는 **"어떤 조건에서 어디로 가는가"** 를 보여준다.

대응: [`UC-01`](../03-use-case-diagram/02-uc-01-batch-execution.md), [`UC-04`](../03-use-case-diagram/05-uc-04-duplicate-rejection.md) / `FR-B-01`~`FR-B-06`, `FR-R-04` 🚧, `FR-S-01`, `NFR-01`

```mermaid
flowchart TB
    start(["시작 — targetDate 주어짐"]) --> guard{"같은 targetDate로<br/>CONFIRMED 배치가<br/>이미 있는가?"}

    guard -->|예| reject["SETTLEMENT_ALREADY_CONFIRMED<br/>예외로 거부"]
    reject --> endReject(["종료 — DB에 아무것도 쓰지 않음"])

    guard -->|"아니오<br/>(없음 또는 FAILED만)"| createBatch["settlement_batches INSERT<br/>status = RUNNING"]

    createBatch --> chunkLoop{"읽을 결제가<br/>남았는가?"}

    chunkLoop -->|예| read["Reader: 결제 서버 API로<br/>청크 분량 조회"]
    read --> readOk{"조회 성공?"}
    readOk -->|아니오| failBatch["status = FAILED"]
    readOk -->|예| process["Processor: 기사별 지급액 계산<br/>→ FC-02"]
    process --> write["Writer: settlement_items INSERT<br/>tripIds 포함"]
    write --> commit["청크 커밋"]
    commit --> chunkErr{"청크 처리 중<br/>예외 발생?"}
    chunkErr -->|예| rollbackChunk["해당 청크만 롤백<br/>이전 청크 결과는 유지"]
    rollbackChunk --> failBatch
    chunkErr -->|아니오| chunkLoop

    chunkLoop -->|"아니오<br/>(0건이어도 진행)"| recon["대사 수행 → FC-03"]

    recon --> reconResult{"대사 결과"}
    reconResult -->|MATCHED| confirm["status = CONFIRMED"]
    confirm --> endOk(["정상 종료"])

    reconResult -->|"MISMATCHED<br/>또는 대사 불가"| pending["🚧 U6 미확정<br/>A: CONFIRMED 보류<br/>B: 확정하되 불일치 기록"]
    pending --> endPending(["종료 — 관리자 확인 필요"])

    failBatch --> endFail(["실패 종료 → UC-05 재시작 대상"])

    classDef decision fill:#3d4a5c,stroke:#22252d,color:#ffffff;
    classDef bad fill:#7a3b3b,stroke:#4d2323,color:#ffffff;
    classDef good fill:#2f6f4f,stroke:#1c4430,color:#ffffff;
    classDef unknown fill:#5a5a5a,stroke:#3a3a3a,color:#dddddd,stroke-dasharray:4 3;
    class guard,chunkLoop,readOk,chunkErr,reconResult decision;
    class reject,failBatch,rollbackChunk,endReject,endFail bad;
    class confirm,endOk good;
    class pending,endPending unknown;
```

**짚어둘 점**

| # | 내용 |
|---|---|
| 1 | **사전검사가 `RUNNING` 생성보다 앞에 있다.** 거부된 실행은 DB에 흔적을 남기지 않는다 |
| 2 | **`FAILED` 배치만 있으면 통과시킨다.** 실패한 배치는 재실행 대상이다 ([`UC-05`](../03-use-case-diagram/06-uc-05-failed-restart.md)) |
| 3 | **결제 0건이어도 대사·확정으로 진행한다.** "돌렸는데 0건"과 "안 돌렸다"는 구별돼야 한다 ([`UC-01` A4](../03-use-case-diagram/02-uc-01-batch-execution.md)) |
| 4 | **청크 롤백은 배치 전체 롤백이 아니다.** 이전 청크 결과는 남는다. 이것이 `FR-B-07` 실패 재시작의 전제다 |
| 5 | 🚧 대사 실패 분기(U6)가 이 흐름의 유일한 미확정 지점이다. **"지급 대상 금액을 확정할 것인가"** 를 정하는 분기라 임의로 채우지 않는다 |
| 6 | ⚠️ 좌상단 `guard`는 **애플리케이션 검사**다. 두 프로세스가 동시에 통과할 수 있다 ([`UC-04` D4](../03-use-case-diagram/05-uc-04-duplicate-rejection.md)). DB 부분 UNIQUE 인덱스를 함께 두는 것을 권한다 |

---

**다음** → [FC-02 기사별 지급액 계산](./02-fc-02-payout-calculation.md)
