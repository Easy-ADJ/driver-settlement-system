> [📚 문서 목록](../README.md) › [🧭 플로우 차트](./index.html) › FC-03

# ⚖️ FC-03 — 대사 판정

대응: [`UC-02`](../03-use-case-diagram/03-uc-02-reconciliation.md) / `FR-R-01`~`FR-R-04`

```mermaid
flowchart TB
    start(["시작 — batch"]) --> callPayment["결제 서버에 전일 결제 조회<br/>GET /api/payments?date= — IF-01<br/>승인 완료 건만 합산"]
    callPayment --> callLedger["원장 서버에 미지급 목록 조회<br/>GET /api/ledger/unpaid?date=<br/>미지급금 합산"]

    callLedger --> bothOk{"양쪽 호출 성공?"}
    bothOk -->|"아니오<br/>(재시도 2회 소진)"| unavailable["SKIPPED<br/>대사 불가 — '모르겠다'"]
    unavailable --> pending

    bothOk -->|예| compare{"결제 합계<br/>== 원장 미지급 합계?<br/>(compareTo)"}
    compare -->|예| matched["MATCHED"]
    matched --> confirm(["→ FC-01: CONFIRMED 전이"])

    compare -->|아니오| realMismatch["MISMATCHED<br/>실제 정합성 이상 — '틀렸다'<br/>차이 금액을 log.error 에 남긴다"]
    realMismatch --> pending

    pending["확정 보류 — RUNNING 유지<br/>판정 결과를 BATCHES 에 저장<br/>조회 API 응답에 그대로 노출"]
    pending --> endPending(["종료 — 관리자 확인 후 /confirm"])

    classDef decision fill:#3d4a5c,stroke:#22252d,color:#ffffff;
    classDef bad fill:#7a3b3b,stroke:#4d2323,color:#ffffff;
    classDef good fill:#2f6f4f,stroke:#1c4430,color:#ffffff;
    classDef unknown fill:#5a5a5a,stroke:#3a3a3a,color:#dddddd,stroke-dasharray:4 3;
    class bothOk,compare decision;
    class unavailable,realMismatch bad;
    class matched,confirm good;
    class pending,endPending bad;
```

**짚어둘 점**

| # | 내용 |
|---|---|
| 1 | **"대사 불가"와 "대사 불일치"는 다르다.** 전자는 원장에 물어보지 못한 것("모르겠다"), 후자는 물어봤는데 답이 다른 것("틀렸다"). 같은 처리로 묶으면 원장 서버 재시작 중에 정합성 경보가 뜬다 |
| 2 | **1원 단위 미세 차이 분기를 제거했다.** U12(절사 규칙)가 **버림 `FLOOR`로 확정**되면서 오탐 원인이 사라졌다. 대신 불일치 시 **차이 금액을 로그에 남긴다** — 없으면 어디를 볼지 알 수 없다 |
| 3 | **금액 비교는 `compareTo`다.** `BigDecimal.equals`는 자릿수까지 비교해서 `16000`과 `16000.00`을 다르다고 본다. DB 값과 API 값은 scale이 다르기 마련이라, `equals`로 비교하면 금액이 같은데도 항상 불일치가 난다 |
| 3 | 정산 합계는 정산 소유 테이블에서, 원장 잔액은 API에서 얻는다. 정산이 `ledger_entries`를 SUM하는 경로는 없다 (`FR-R-02`, `CST-02`) |
| 4 | 부호 규약([`IF-02` Q4](../02-requirements-specification/04-external-interfaces.md)) — 미지급금이 양수인지 음수인지 원장과 맞춰야 한다. 잘못 잡으면 `compare`가 항상 불일치로 떨어진다 |

---

**이전** → [FC-02 기사별 지급액 계산](./02-fc-02-payout-calculation.md) · **다음** → [FC-04 정산 배치 상태 전이](./04-fc-04-state-transition.md)
