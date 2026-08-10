> [📚 문서 목록](../README.md) › [📑 요구사항 정의서](./index.html) › §7

# ⛔ 에러 코드

공통 포맷 (`CTR §2`):

```json
{
  "code": "SETTLEMENT_ALREADY_CONFIRMED",
  "message": "2026-08-10 정산이 이미 확정되었습니다",
  "transactionId": "..."
}
```

- HTTP 상태코드로 종류를 구분(400 요청 오류 / 404 없음 / 409 충돌·중복 / 500 서버 오류), `code`로 원인을 특정한다
- 호출자는 **`code`로만 분기**하고 `message`는 로그에만 쓴다

| `code` | HTTP | 상황 | 관련 |
|---|---|---|---|
| `SETTLEMENT_ALREADY_CONFIRMED` | 409 | 같은 `targetDate`로 이미 확정된 배치 존재 | `FR-B-06` |
| `SETTLEMENT_NOT_FOUND` | 404 | 조회한 일자·기사의 정산 내역 없음 | `FR-Q-01` |
| `INVALID_DATE_FORMAT` | 400 | `date` 형식 오류 | `FR-Q-01` |
| `MISSING_REQUIRED_PARAMETER` | 400 | 필수 파라미터 누락 | `FR-Q-01` |
| `PAYMENT_SERVICE_UNAVAILABLE` | 500 | 결제 서버 호출 실패 (재시도 소진) | `IF-01`, `FR-B-10` |
| `LEDGER_SERVICE_UNAVAILABLE` | 500 | 원장 서버 호출 실패 (재시도 소진) | `IF-02` |
| `RECONCILIATION_MISMATCH` | 409 | 대사 불일치 | `FR-R-01` |
| `INVALID_STATE_TRANSITION` | 409 | 정의되지 않은 상태 전이 시도 | `FR-S-01` |
| `INTERNAL_ERROR` | 500 | 그 외 | — |

> ⚠️ **위 코드는 정산 서버가 새로 제안하는 것이다.** `CTR §2`에는 원장 예시(`LEDGER_ENTRY_UNBALANCED`) 하나만 있다. 정산 서버만 쓰는 코드라 다른 서버가 분기할 일은 없지만, 팀 공통 네이밍(대문자 스네이크, 도메인 접두사)에 맞췄다. `service-contracts.md`에 반영할지는 회의에서 확인한다.

각 코드에 대응하는 예외 클래스는 [클래스 다이어그램 §6 예외 계층](../06-class-diagram/06-exception-layer.md)에 있다.

---

**이전** → [데이터 요구사항](./05-data-requirements.md) · **다음** → [제약 사항](./07-constraints.md)
