> [📚 문서 목록](../README.md) › [🧭 플로우 차트](./index.html) › FC-03

# ⚖️ FC-03 — 대사 판정

대응: [`UC-02`](../03-use-case-diagram/03-uc-02-reconciliation.md) / `FR-R-01`~`FR-R-04`

```mermaid
flowchart TB
    start(["시작 — batchId"]) --> sumItems["정산 항목 합계 계산<br/>SUM settlement_items.amount"]
    sumItems --> callLedger["원장 서버에 기사 미지급금<br/>잔액 조회 — IF-02"]

    callLedger --> ledgerOk{"호출 성공?"}
    ledgerOk -->|"아니오<br/>(재시도 2회 소진)"| unavailable["LEDGER_SERVICE_UNAVAILABLE<br/>대사 불가 — '모르겠다'"]
    unavailable --> pending

    ledgerOk -->|예| compare{"정산 합계<br/>== 원장 잔액?"}
    compare -->|예| matched["MATCHED"]
    matched --> confirm(["→ FC-01: CONFIRMED 전이"])

    compare -->|아니오| diff{"차이가 1원 단위<br/>미세 차이인가?"}
    diff -->|예| roundingSuspect["⚠️ U12 절사·반올림 규칙<br/>불일치 의심 — 계산 오류가 아님"]
    diff -->|아니오| realMismatch["RECONCILIATION_MISMATCH<br/>실제 정합성 이상 — '틀렸다'"]

    roundingSuspect --> pending
    realMismatch --> pending

    pending["🚧 U5 통지 수단 미확정<br/>🚧 U6 CONFIRMED 보류 여부 미확정"]
    pending --> endPending(["종료 — 관리자 확인"])

    classDef decision fill:#3d4a5c,stroke:#22252d,color:#ffffff;
    classDef bad fill:#7a3b3b,stroke:#4d2323,color:#ffffff;
    classDef good fill:#2f6f4f,stroke:#1c4430,color:#ffffff;
    classDef unknown fill:#5a5a5a,stroke:#3a3a3a,color:#dddddd,stroke-dasharray:4 3;
    class ledgerOk,compare,diff decision;
    class unavailable,realMismatch,roundingSuspect bad;
    class matched,confirm good;
    class pending,endPending unknown;
```

**짚어둘 점**

| # | 내용 |
|---|---|
| 1 | **"대사 불가"와 "대사 불일치"는 다르다.** 전자는 원장에 물어보지 못한 것("모르겠다"), 후자는 물어봤는데 답이 다른 것("틀렸다"). 같은 처리로 묶으면 원장 서버 재시작 중에 정합성 경보가 뜬다 |
| 2 | **1원 단위 미세 차이를 별도 분기로 뒀다.** U12가 미해결인 동안 오탐의 주요 원인이므로, 원인 판별에 시간을 덜 쓰기 위해서다. U12가 확정되면 이 분기는 제거한다 |
| 3 | 정산 합계는 정산 소유 테이블에서, 원장 잔액은 API에서 얻는다. 정산이 `ledger_entries`를 SUM하는 경로는 없다 (`FR-R-02`, `CST-02`) |
| 4 | 부호 규약([`IF-02` Q4](../02-requirements-specification/04-external-interfaces.md)) — 미지급금이 양수인지 음수인지 원장과 맞춰야 한다. 잘못 잡으면 `compare`가 항상 불일치로 떨어진다 |

---

**이전** → [FC-02 기사별 지급액 계산](./02-fc-02-payout-calculation.md) · **다음** → [FC-04 정산 배치 상태 전이](./04-fc-04-state-transition.md)
