> [📚 문서 목록](../README.md) › [🎭 유스케이스 다이어그램](./index.html) › UC-03

# 🔍 UC-03 — 정산 내역 조회

| 항목 | 내용 |
|---|---|
| **주 Actor** | 관리자 |
| **목적** | 기사 문의에 답한다 — "이 금액이 어떤 운행에서 나왔는가" |
| **관련 요구사항** | `FR-Q-01`, `FR-Q-02`, `FR-Q-04`, `FR-B-08`, `NFR-05` |
| **사전조건** | 해당 일자의 정산 배치가 실행됐다 |
| **사후조건** | 없음 (조회만) |

**주 흐름**

1. 관리자가 `driverId`·`date`로 `GET /api/settlements`를 호출한다
2. 시스템이 해당 조건의 정산 항목을 조회한다
3. 시스템이 지급액·운임 합계·수수료·포함된 운행 ID 목록을 반환한다

**대안·예외 흐름**

| # | 분기점 | 처리 |
|---|---|---|
| C1 | `date` 형식 오류 | 400 `INVALID_DATE_FORMAT` |
| C2 | `date` 누락 | 400 `MISSING_REQUIRED_PARAMETER` |
| C3 | 해당 조건의 내역 없음 | 404 `SETTLEMENT_NOT_FOUND` |
| C4 | `driverId` 생략 | 해당 일자 전체 기사 반환 |

응답 스펙은 [정의서 §4 제공 API 명세](../02-requirements-specification/03-api-specification.md)에 있다.

관련 다이어그램: [`SD-05`](../04-sequence-diagram/05-sd-05-admin-query.md)

---

**이전** → [UC-02 정산 대사 검증](./03-uc-02-reconciliation.md) · **다음** → [UC-04 중복 배치 실행 거부](./05-uc-04-duplicate-rejection.md)
