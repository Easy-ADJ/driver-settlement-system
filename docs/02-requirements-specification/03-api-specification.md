> [📚 문서 목록](../README.md) › [📑 요구사항 정의서](./index.html) › §4

# 🔌 제공 API 명세

정산 서버가 **제공하는** API다. 정산 서버가 **호출하는** 외부 API는 [§5 외부 인터페이스](./04-external-interfaces.md)에 있다.

## 가. `GET /api/settlements` — 정산 내역 조회 (`FR-Q-01`)

관리자용. 기사별·기간별 정산 내역과 상세 항목을 반환한다.

**요청 파라미터**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `driverId` | string | 아니오 | 기사 ID. 생략하면 해당 일자 전체 기사 |
| `date` | string (`yyyy-MM-dd`) | 예 | 정산 대상 일자 (`targetDate`) |

> 🚧 `date` 범위 조회(`from`/`to`)를 지원할지, `date` 단건만 받을지는 `FR-Q-04`와 함께 확정한다. 현재 팀 문서의 시그니처(`?driverId=&date=`)는 단건 기준이다.

**응답 200 (예시)**

```json
{
  "targetDate": "2026-08-10",
  "batchStatus": "CONFIRMED",
  "reconciliationStatus": "MATCHED",
  "settlements": [
    {
      "driverId": "driver-001",
      "fareTotal": "20000",
      "feeAmount": "4000",
      "payoutAmount": "16000",
      "payoutStatus": "CONFIRMED",
      "tripIds": ["trip-1001", "trip-1002"]
    }
  ]
}
```

- 금액은 모두 **문자열** (`NFR-05`)
- `tripIds`가 `FR-B-08`의 추적성을 실현하는 필드다. 기사 문의에 답하는 데 실제로 쓰이는 값이므로 생략하지 않는다
- `fareTotal` - `feeAmount` = `payoutAmount`가 항상 성립한다. 계산 근거를 함께 노출해 "왜 이 금액인가"를 응답만으로 설명한다

**에러 응답**

| 상태 | `code` | 상황 |
|---|---|---|
| 400 | `INVALID_DATE_FORMAT` | `date`가 `yyyy-MM-dd`가 아님 |
| 400 | `MISSING_REQUIRED_PARAMETER` | `date` 누락 |
| 404 | `SETTLEMENT_NOT_FOUND` | 해당 일자·기사의 정산 내역 없음 |
| 500 | `INTERNAL_ERROR` | 그 외 |

전체 에러 코드 목록은 [§7 에러 코드](./06-error-codes.md)에 있다.

## 나. 🚧 정산 확정·지급 처리 API (`FR-S-02`)

`CONFIRMED` → `PAID` 전이를 API로 노출할지 미확정 (U7). 노출한다면 쓰기 호출이므로 `Idempotency-Key` 헤더 전파 규칙(`CTR §2`)이 적용된다.

---

**이전** → [비기능 요구사항](./02-non-functional-requirements.md) · **다음** → [외부 인터페이스 요구사항](./04-external-interfaces.md)
