# 🗂️ 정산 서버 문서 안내

운전자 정산 시스템(`driver-settlement-system`)의 요구사항·설계 문서다. 팀 코어 문서([Easy-ADJ/core-documents](https://github.com/Easy-ADJ/core-documents))의 기획서·설계 초안을 **정산 서버 관점으로** 구체화한 것이다.

| 항목 | 값 |
|---|---|
| 담당자 | 허진수 |
| 작성일 | 2026-08-10 |
| 상태 | 초안 — 🚧 항목은 **08.11(화) 중간 회의** 후 갱신 |

---

## 폴더 구조

**산출물 하나가 폴더 하나다.** 각 폴더의 `index.html`을 브라우저로 열면 그 산출물이 어떤 파일로 구성돼 있고 각 파일에 무엇이 있는지 카드 형태로 한눈에 볼 수 있다.

| # | 산출물 | 폴더 | 한눈에 보기 | 내용 |
|---|---|---|---|---|
| 1 | 요구사항 기술서 | [`01-requirements-statement/`](./01-requirements-statement/) | [index.html](./01-requirements-statement/index.html) | 무엇을 왜 만드는가. 시스템 경계, Actor, 제약, 가정, 🚧 미확정 목록(U1~U12), 용어 정의 |
| 2 | 요구사항 정의서 | [`02-requirements-specification/`](./02-requirements-specification/) | [index.html](./02-requirements-specification/index.html) | 1번을 `FR`/`NFR`/`IF`/`CST` ID로 분해. 수락 기준, 제공 API 명세, 데이터 요구사항, 에러 코드, **추적성 매트릭스** |
| 3 | 유스케이스 다이어그램 | [`03-use-case-diagram/`](./03-use-case-diagram/) | [index.html](./03-use-case-diagram/index.html) | `UC-01`~`UC-07`. Actor별 유스케이스와 대안·예외 흐름 |
| 4 | 시퀀스 다이어그램 | [`04-sequence-diagram/`](./04-sequence-diagram/) | [index.html](./04-sequence-diagram/index.html) | `SD-01`~`SD-06`. 시간 순 상호작용 |
| 5 | 플로우 차트 | [`05-flow-chart/`](./05-flow-chart/) | [index.html](./05-flow-chart/index.html) | `FC-01`~`FC-04`. 배치 제어 흐름, 지급액 계산, 대사 판정, 상태 전이 |
| 6 | 클래스 다이어그램 | [`06-class-diagram/`](./06-class-diagram/) | [index.html](./06-class-diagram/index.html) | 전 계층 클래스 구조와 의존 규칙 |

**읽는 순서는 1 → 6이다.** 각 파일 상단·하단에 이전/다음 링크가 있어 순서대로 따라갈 수 있다.

**빠르게 훑을 때**: 정의서의 [추적성 매트릭스](./02-requirements-specification/08-traceability-matrix.md)가 6개 산출물을 잇는 색인이다. 특정 요구사항이 어느 다이어그램·클래스에 대응하는지 한 표에서 볼 수 있다.

---

## 이 문서들이 규정하지 않는 것

- **결제 서버·원장 서버의 내부 설계** — 각각 김주엽·이치헌 소유다. 여기서는 정산 서버가 호출하는 **외부 시스템**으로만 등장한다
- **공동 소유 문서의 내용** — `architecture.md`·`service-contracts.md`·`erd.md`는 팀 합의 사항이며, 이 문서들은 그것을 인용할 뿐 바꾸지 않는다
- **실제 송금 연동** — 범위 밖이다. `PAID`는 "지급 완료로 표시함"까지를 뜻한다

진행 현황은 조직 레벨 GitHub Projects 보드에서만 본다. 이 폴더에 현황판 파일을 두지 않는다.

---

## 🚧 다음에 해야 할 일

### 08.11 회의 안건 (정산 서버 관련)

기술서 [🚧 미확정 항목](./01-requirements-statement/09-open-issues.md)의 U1~U12다. 그중 **금액 판단에 직접 영향을 주는 것**:

| # | 항목 | 왜 급한가 |
|---|---|---|
| **U12** | 금액 절사·반올림 규칙 | 팀 문서에 없던 공백이다. 원장과 규칙이 다르면 **계산이 정상인데도 대사가 실패한다** |
| **U6** | 대사 실패 시 `CONFIRMED` 보류 여부 | "대사 실패한 금액을 지급 대상으로 볼 것인가"를 정한다 |
| U4 | 수수료율 20% 하드코딩 여부 | 계산 로직의 값 출처 |
| U10 | 서비스 경계를 넘는 FK | 경계 규칙과 충돌 여부 |

### 결제·원장 담당자에게 확인할 것

정의서 [외부 인터페이스 요구사항](./02-requirements-specification/04-external-interfaces.md)의 Q1~Q5다. **`contract` 라벨 이슈를 제공자 레포에 올리고 소비자(정산)를 멘션하는 방식**으로 진행한다 — 정산 레포에 쌓으면 정작 답할 사람이 보지 않는다.

| # | 확인 대상 | 대상자 | 없으면 |
|---|---|---|---|
| **Q1** | 결제 내역 응답에 `driverId`가 있는가 | 김주엽 | **기사별 집계가 불가능하다.** `trips`를 직접 읽는 것은 규칙 위반 |
| **Q3** | 기사 계정의 `accountId`를 어떻게 얻는가 | 이치헌 | 대사 검증을 구현할 수 없다 |
| Q2 | 취소 결제를 어떻게 식별하는가 | 김주엽 | 집계 제외 기준이 없다 |
| Q4 | 잔액 부호 규약 | 이치헌 | 대사가 상시 불일치로 떨어진다 |
| Q5 | 절사·반올림 규칙 (U12) | 이치헌 | 위와 동일 |

> **Q1과 Q3이 정산 구현의 선행 조건이다.** 결제·원장 레포가 아직 생성되지 않았으므로, 그때까지는 mock/stub으로 개발한다.

### 회의 후 갱신 절차

1. 확정된 U번호에 대해 **기술서 [🚧 미확정 항목](./01-requirements-statement/09-open-issues.md) 표의 해당 행을 결정 내용으로 교체**
2. 정의서의 해당 `FR`/`NFR`/`IF` 상태를 `🚧` → `확정`으로 바꾸고 **수락 기준을 채운다**
3. 관련 다이어그램의 🚧 노드·노트를 확정 흐름으로 교체
4. 클래스 다이어그램 [🚧 미확정이 구조에 미치는 영향](./06-class-diagram/08-open-issues-impact.md)에서 해당 행을 제거하고 본문에 반영
5. 각 폴더 `index.html`의 🚧 배지·요약 문구를 함께 갱신한다 (본문만 고치면 인덱스가 낡는다)
6. 스키마·계약이 바뀌었으면 `core-documents`의 `erd.md`·`service-contracts.md`·`services/settlement.md` 갱신이 **구현보다 먼저**다

---

## 관련 문서 (core-documents)

| 문서 | 역할 |
|---|---|
| [`01-planning/service-spec.md`](https://github.com/Easy-ADJ/core-documents/blob/main/01-planning/service-spec.md) | 확정 기획서. §1~§3이 요구사항 원천. **§4 아키텍처는 현행 아님** |
| [`02-design/architecture.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/architecture.md) | **현행 아키텍처 단일 출처** |
| [`02-design/service-contracts.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/service-contracts.md) | 서버 간 호출 계약, 테이블 직접 접근 금지, 공통 규약 |
| [`02-design/erd.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/erd.md) | 테이블 소유권·주요 제약 |
| [`02-design/services/settlement.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/services/settlement.md) | 정산 서버 설계 초안 (이 폴더의 직접 상위) |
