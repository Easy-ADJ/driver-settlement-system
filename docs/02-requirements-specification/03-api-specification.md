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

**에러 응답**

| 상태 | `code` | 상황 |
|---|---|---|
| 400 | `INVALID_DATE_FORMAT` | `date`가 `yyyy-MM-dd`가 아님 |
| 400 | `MISSING_REQUIRED_PARAMETER` | `date` 누락 |
| 404 | `SETTLEMENT_NOT_FOUND` | 해당 일자·기사의 정산 내역 없음 |
| 500 | `INTERNAL_ERROR` | 그 외 |

전체 에러 코드 목록은 [§7 에러 코드](./06-error-codes.md)에 있다.

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
- **`batchStatus`가 `RUNNING`으로 나올 수 있다.** 배치가 성공해도 **대사가 `MATCHED`를 낼 때만** 확정된다. `MISMATCHED`·`SKIPPED`면 `RUNNING`에 머물고, 사람이 아래 **다.** 로 진행시킨다
- `batchId`로 곧바로 `GET /api/settlements?date=`를 불러 결과를 확인할 수 있다

**에러 응답**

| 상태 | `code` | 상황 |
|---|---|---|
| 400 | `MISSING_REQUIRED_PARAMETER` | `targetDate` 누락 |
| 400 | `INVALID_DATE_FORMAT` | `targetDate` 형식 오류 |
| 409 | `SETTLEMENT_ALREADY_CONFIRMED` | 이미 확정된 날짜이거나, 같은 날짜로 이미 실행이 완료됨 |

> 두 가지 상황에 같은 코드를 쓴다. 호출자 입장에서는 **둘 다 "그날은 이미 돌았다"** 이고 후속 조치도 같다.

---

## 다. `POST /api/settlements/{batchId}/confirm` — 배치 확정 (`FR-S-01`)

`RUNNING` → `CONFIRMED` 전이. **이 호출이 원장에 지급 상쇄 분개를 남긴다.**

> ⚠️ **확정과 상쇄 분개는 하나의 트랜잭션이다.** 원장 기록이 재시도 후에도 실패하면 배치는 확정되지 않는다.
> 둘을 떼어 놓으면 **미지급금이 남은 채로 확정된 배치**가 생기고, 다음날 배치가 같은 금액을 또 정산한다.
> 그리고 당일 결과만 보면 금액이 정확해서 눈에 띄지 않는다.

**보류된 배치를 사람이 푸는 지점이기도 하다.** 대사가 `MISMATCHED`를 내면 자동 확정은 일어나지 않는데, 그 배치를 진행시킬 방법이 이 API다. **`MISMATCHED`여도 거부하지 않는다** — 거부하면 보류를 풀 수단이 없어진다.

> 대신 `reconciliation_status`는 그대로 남는다. 나중에 레코드를 보면 **`CONFIRMED` + `MISMATCHED`** 조합이 "사람이 밀어붙인 확정"이라는 증거가 된다.
> 확정을 건너뛰고 바로 `PAID`로 보내는 방식을 택했다면 이 구분이 남지 않는다 — `PAID` 레코드만 보고는 검증을 통과한 것인지 사람이 밀어붙인 것인지 알 수 없다.

**요청 경로 변수**

| 이름 | 타입 | 설명 |
|---|---|---|
| `batchId` | number | 확정할 배치 |

**응답 200** — `가.`의 조회 응답과 같은 형태다. 단 `payments`는 항상 빈 배열이다 (상태를 바꾼 직후에 결제 건별 근거가 필요하지 않다).

```json
{
  "targetDate": "2026-08-19",
  "batchStatus": "CONFIRMED",
  "reconciliationStatus": null,
  "settlements": [
    { "driverId": 1, "fareTotal": "42000.00", "feeAmount": "8400.00",
      "payoutAmount": "33600.00", "payoutStatus": "CONFIRMED", "payments": [] }
  ]
}
```

**원장으로 나가는 호출**

기사마다 한 번씩 `POST /api/ledger/entries`를 부른다. 상쇄 금액은 **지급액이 아니라 운임 합계**다 — 수수료를 뺀 금액만 상쇄하면 그 수수료가 미지급금으로 남아 다음날 이 기사가 또 선별된다.

```json
{
  "idempotencyKey": "settlement-42-1",
  "driverId": 1,
  "entryType": "SETTLEMENT",
  "entries": [
    { "direction": "DEBIT",  "amount": "42000", "paymentId": null, "ownerType": "DRIVER" },
    { "direction": "CREDIT", "amount": "42000", "paymentId": null, "ownerType": "PLATFORM" }
  ]
}
```

> ⚠️ **`ownerType`이 어느 leg을 기사 잔액에 반영할지 정한다.** 원장은 두 leg에 같은 요청 값을 받으므로 이걸로만 구분한다.
> 빠뜨리거나 양쪽 다 `DRIVER`로 보내면 **두 leg이 서로 상쇄돼 미지급금이 전혀 움직이지 않는다.**
> 요청은 201로 성공하고 분개도 쌓이는데 잔액만 그대로여서, **응답으로는 알 수 없고 다음날 같은 기사가 또 정산돼야 드러난다.**
> ([ledger#7](https://github.com/Easy-ADJ/driver-ledger-system/issues/7)에서 실제로 있던 버그다)

멱등 키는 `settlement-{batchId}-{driverId}`다. **재계산 가능해야** 재시도가 같은 키를 만든다. 헤더와 본문 양쪽에 같은 값이 들어간다.

**에러 응답**

| 상태 | `code` | 상황 |
|---|---|---|
| 404 | `SETTLEMENT_NOT_FOUND` | 없는 `batchId` |
| 409 | `INVALID_STATE_TRANSITION` | `RUNNING`이 아님 (이미 확정·지급됨) |
| 500 | `LEDGER_SERVICE_UNAVAILABLE` | 원장 상쇄 분개 기록이 재시도 후에도 실패 |

## 라. `POST /api/settlements/{batchId}/pay` — 지급 완료 표시 (`FR-S-02`)

`CONFIRMED` → `PAID` 전이. 배치와 기사별 항목(`payout_status`)을 **함께** 바꾼다.

> ⚠️ **실제 송금은 일어나지 않는다.** 데모 범위에서 `PAID`는 "정산 처리 완료" 표식이다.

> 배치만 바꾸고 항목을 두면 조회 응답에서 **배치는 지급 완료인데 기사별 항목은 확정 상태**로 보인다. 보는 사람이 어느 쪽을 믿어야 할지 알 수 없다.

**응답 200** — `다.`와 같은 형태이며 `batchStatus`·`payoutStatus`가 `PAID`다.

**에러 응답**

| 상태 | `code` | 상황 |
|---|---|---|
| 404 | `SETTLEMENT_NOT_FOUND` | 없는 `batchId` |
| 409 | `INVALID_STATE_TRANSITION` | `CONFIRMED`가 아님 |

> **이미 `PAID`인 배치에 다시 부르면 409다.** 같은 결과를 돌려주는 대신 거부하는 쪽을 택했다 — 두 번 눌렀다는 사실 자체를 호출자가 알아야 한다.

> 🔓 **다. 와 라. 도 인증이 없다.** `나.`와 같은 이유이며, 운영 전환 시 함께 잠가야 한다.

---

**이전** → [비기능 요구사항](./02-non-functional-requirements.md) · **다음** → [외부 인터페이스 요구사항](./04-external-interfaces.md)
