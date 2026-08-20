> [📚 문서 목록](../README.md) › [📑 요구사항 정의서](./index.html) › §4

# 🔌 제공 API 명세

정산 서버가 **제공하는** API다. 정산 서버가 **호출하는** 외부 API는 [§5 외부 인터페이스](./04-external-interfaces.md)에 있다.

## 가. `GET /api/settlements` — 정산 내역 조회 (`FR-Q-01`)

관리자용. 기사별·기간별 정산 내역과 상세 항목을 반환한다.

**요청 파라미터**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `driverId` | number | 아니오 | 기사 ID. 생략하면 해당 일자 전체 기사 |
| `date` | string (`yyyy-MM-dd`) | 예 | 정산 대상 일자 (`targetDate`) |

> 🚧 `date` 범위 조회(`from`/`to`)를 지원할지, `date` 단건만 받을지는 `FR-Q-04`와 함께 확정한다. 현재 팀 문서의 시그니처(`?driverId=&date=`)는 단건 기준이다.

**응답 200 (예시)**

```json
{
  "targetDate": "2026-08-19",
  "batchStatus": "CONFIRMED",
  "reconciliationStatus": "MATCHED",
  "settlements": [
    {
      "driverId": 1,
      "fareTotal": "20000",
      "feeAmount": "4000",
      "payoutAmount": "16000",
      "payoutStatus": "CONFIRMED",
      "payments": [
        { "paymentId": 100, "amount": "15000", "approvedAt": "2026-08-19T14:30:00Z" },
        { "paymentId": 101, "amount": "5000",  "approvedAt": "2026-08-19T18:00:00Z" }
      ]
    }
  ]
}
```

- **금액은 모두 문자열, ID는 숫자다** (`NFR-05`). 정밀도가 깨지는 것은 금액뿐이라 ID까지 문자열로 만들 이유가 없다
- `fareTotal` - `feeAmount` = `payoutAmount`가 항상 성립한다. 계산 근거를 함께 노출해 "왜 이 금액인가"를 응답만으로 설명한다
- `payments`가 `FR-B-08`의 추적성을 실현하는 필드다. 기사가 "이 금액이 왜 이렇게 나왔냐"고 물었을 때 답이 되는 단위이므로 **비어 있어도 생략하지 않는다**

> ⚠️ **`payments`는 `driverId`를 지정했을 때만 채워진다.**
>
> | 조회 | 원장 호출 | `payments` |
> |---|---|---|
> | `?date=` (전체) | 0번 | **빈 배열** |
> | `?date=&driverId=` (기사 지정) | 1번 | 채워짐 |
>
> 이 값은 정산 DB에 없고 기사마다 원장 `GET /api/ledger?driver_id=`를 불러야 나온다.
> 전체 조회에서 채우면 **기사 수만큼 원장을 호출**하게 되고, 각 호출에 응답 타임아웃 10초가 걸려 있다.
> 목록에서 훑고 한 명을 눌러 상세를 보는 흐름이라 목록에서는 필요하지 않다.

> 🔄 **`tripIds` → `payments` 로 바뀌었다.** `trips` 테이블이 ERD에서 사라지면서(#7) 정산 DB에는 운행 단위 근거가 남지 않는다.
> 그 자리를 원장 `GET /api/ledger?driver_id=` 응답의 `paymentDetails`가 채운다 — 결제 건별 금액과 승인 시각이다.
> 즉 **이 필드는 정산 DB가 아니라 원장에서 조회 시점에 채워진다.**

## 나. `POST /api/settlements/batch` — 배치 수동 실행 (시연·놓친 날 메우기)

자정을 기다리지 않고 원하는 날짜로 정산 배치를 돌린다. **스케줄 실행과 같은 경로를 타므로** 중복 검사·상태 전이가 동일하게 동작한다.

> 🔓 **이 API는 인증이 없다.** 아무나 부르면 정산을 돌릴 수 있다. 이미 확정된 날짜는 중복 검사와 부분 UNIQUE 인덱스가 막지만, 확정 전 날짜로는 얼마든지 실행된다.
> 데모 범위에서는 이대로 두되 **운영으로 갈 때 가장 먼저 잠가야 할 엔드포인트다.**

**요청 파라미터**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `targetDate` | string (`yyyy-MM-dd`) | 예 | 정산 대상 일자 |

**응답 200 (예시)**

```json
{
  "batchId": 42,
  "targetDate": "2026-08-19",
  "batchStatus": "RUNNING",
  "settlementCount": 2
}
```

- **`202 Accepted`가 아니라 `200 OK`다.** 배치가 동기로 돌아 응답 시점에는 이미 끝나 있다. "접수했다"고 답하면 사실과 다르다
- **`batchStatus`가 `RUNNING`으로 나오는 것은 정상이다.** 배치는 성공해도 확정되지 않는다 — 확정은 대사가 `MATCHED`를 낼 때만 한다
- `batchId`로 곧바로 `GET /api/settlements?date=`를 불러 결과를 확인할 수 있다

**에러 응답**

| 상태 | `code` | 상황 |
|---|---|---|
| 400 | `MISSING_REQUIRED_PARAMETER` | `targetDate` 누락 |
| 400 | `INVALID_DATE_FORMAT` | `targetDate` 형식 오류 |
| 409 | `SETTLEMENT_ALREADY_CONFIRMED` | 이미 확정된 날짜이거나, 같은 날짜로 이미 실행이 완료됨 |

> 두 가지 상황에 같은 코드를 쓴다. 호출자 입장에서는 **둘 다 "그날은 이미 돌았다"** 이고 후속 조치도 같다.

---

**에러 응답**

| 상태 | `code` | 상황 |
|---|---|---|
| 400 | `INVALID_DATE_FORMAT` | `date`가 `yyyy-MM-dd`가 아님 |
| 400 | `MISSING_REQUIRED_PARAMETER` | `date` 누락 |
| 404 | `SETTLEMENT_NOT_FOUND` | 해당 일자·기사의 정산 내역 없음 |
| 500 | `INTERNAL_ERROR` | 그 외 |

전체 에러 코드 목록은 [§7 에러 코드](./06-error-codes.md)에 있다.

## 다. 🚧 정산 확정·지급 처리 API (`FR-S-02`)

`CONFIRMED` → `PAID` 전이를 API로 노출할지 미확정 (U7). 노출한다면 쓰기 호출이므로 `Idempotency-Key` 헤더 전파 규칙(`CTR §2`)이 적용된다.

---

**이전** → [비기능 요구사항](./02-non-functional-requirements.md) · **다음** → [외부 인터페이스 요구사항](./04-external-interfaces.md)
