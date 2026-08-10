> [📚 문서 목록](../README.md) › [📑 요구사항 정의서](./index.html) › §1

# 🔤 표기 규칙

| 항목 | 값 |
|---|---|
| 대상 시스템 | 운전자 정산 시스템 (`driver-settlement-system`) |
| 담당자 | 허진수 |
| 작성일 | 2026-08-10 |
| 상태 | 초안 — 🚧 항목은 **08.11(화) 중간 회의**에서 확정 |
| 상위 문서 | [요구사항 기술서](../01-requirements-statement/index.html) |

---

## 가. ID 체계

| 접두사 | 영역 |
|---|---|
| `FR-B-nn` | 기능 — 정산 배치 (Batch) |
| `FR-R-nn` | 기능 — 대사 (Reconciliation) |
| `FR-Q-nn` | 기능 — 조회 (Query) |
| `FR-S-nn` | 기능 — 상태 관리 (State) |
| `NFR-nn` | 비기능 |
| `IF-nn` | 외부 인터페이스 |
| `CST-nn` | 제약 |
| `UC-nn` | 유스케이스 ([유스케이스 다이어그램](../03-use-case-diagram/index.html)) |
| `SD-nn` | 시퀀스 다이어그램 ([시퀀스 다이어그램](../04-sequence-diagram/index.html)) |
| `FC-nn` | 플로우 차트 ([플로우 차트](../05-flow-chart/index.html)) |

## 나. 우선순위 (MoSCoW)

| 표기 | 뜻 |
|---|---|
| **M** | Must — 없으면 이 프로젝트의 목표([기술서 §3](../01-requirements-statement/02-problem.md) 설명 가능성·정합성)가 성립하지 않는다 |
| **S** | Should — 있어야 정상 운영이 되지만, 데모는 없이도 가능하다 |
| **C** | Could — 시간이 남으면 |

## 다. 상태

| 표기 | 뜻 |
|---|---|
| 확정 | 팀 문서로 합의된 내용 |
| 🚧 | **미확정.** 08.11 회의에서 결정. [기술서 §10](../01-requirements-statement/09-open-issues.md)의 U번호와 대응 |

## 라. 출처 약어

| 약어 | 문서 |
|---|---|
| `SPEC §n` | [`core-documents/01-planning/service-spec.md`](https://github.com/Easy-ADJ/core-documents/blob/main/01-planning/service-spec.md) |
| `ARCH §n` | [`core-documents/02-design/architecture.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/architecture.md) |
| `CTR §n` | [`core-documents/02-design/service-contracts.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/service-contracts.md) |
| `ERD §n` | [`core-documents/02-design/erd.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/erd.md) |
| `SET` | [`core-documents/02-design/services/settlement.md`](https://github.com/Easy-ADJ/core-documents/blob/main/02-design/services/settlement.md) |

---

**다음** → [기능 요구사항](./01-functional-requirements.md)
