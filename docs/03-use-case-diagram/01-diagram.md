> [📚 문서 목록](../README.md) › [🎭 유스케이스 다이어그램](./index.html) › §1

# 🎭 유스케이스 다이어그램

표기 관례는 [§0](./00-notation.md)을 따른다.

```mermaid
flowchart LR
    scheduler["배치 스케줄러<br/>(외부 시스템)"]
    admin["관리자<br/>(사람)"]
    paysvc["결제 서버<br/>(외부 시스템)"]
    ledsvc["원장 서버<br/>(외부 시스템)"]

    subgraph SYS["정산 서버 (driver-settlement-system)"]
        direction TB
        uc1(["UC-01<br/>일일 정산 배치 실행"])
        uc2(["UC-02<br/>정산 대사 검증"])
        uc3(["UC-03<br/>정산 내역 조회"])
        uc4(["UC-04<br/>중복 배치 실행 거부"])
        uc5(["UC-05<br/>실패 배치 재시작"])
        uc6(["UC-06 🚧<br/>지급 완료 처리"])
        uc7(["UC-07 🚧<br/>정산 내역서 생성·보관"])
    end

    scheduler --> uc1
    admin --> uc3
    admin --> uc5
    admin --> uc6

    uc1 -.->|include| uc4
    uc1 -.->|include| uc2
    uc5 -.->|include| uc1
    uc6 -.->|include| uc7

    uc1 -->|"GET /api/payments?date="| paysvc
    uc2 -->|"GET /api/ledger/accounts/{id}/balance"| ledsvc
    uc6 -.->|"🚧 POST /api/ledger/entries"| ledsvc

    classDef confirmed fill:#2f6f4f,stroke:#1c4430,color:#ffffff;
    classDef pending fill:#5a5a5a,stroke:#3a3a3a,color:#dddddd,stroke-dasharray:4 3;
    classDef actor fill:#3a3f4b,stroke:#22252d,color:#e6e6e6;
    class uc1,uc2,uc3,uc4,uc5 confirmed;
    class uc6,uc7 pending;
    class scheduler,admin,paysvc,ledsvc actor;
```

**읽는 법**

- `UC-01`이 `UC-04`(중복 거부)와 `UC-02`(대사)를 include한다 — 배치를 돌리면 사전검사와 대사가 항상 함께 일어난다. 별개로 실행하는 기능이 아니다.
- 결제·원장 서버는 **정산 서버가 호출하는 대상**이므로 화살표가 시스템에서 밖으로 나간다. 두 서버는 정산 서버를 호출하지 않는다.
- 기사는 이 다이어그램에 없다. 정산 서버를 직접 쓰지 않는다 — 문의 시 관리자가 `UC-03`으로 답한다. **`UC-03`이 존재하는 이유가 기사다.**

---

**이전** → [표기 관례](./00-notation.md) · **다음** → [UC-01 일일 정산 배치 실행](./02-uc-01-batch-execution.md)
