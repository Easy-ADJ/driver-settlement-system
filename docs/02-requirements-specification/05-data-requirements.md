> [📚 문서 목록](../README.md) › [📑 요구사항 정의서](./index.html) › §6

# 🗄️ 데이터 요구사항

정산 서버가 **소유하는** 테이블이다 (`ERD §2`). 스키마 변경은 정산 담당자가 결정하되, `schema` 라벨 이슈로 나머지 2명을 멘션한다 — DB가 하나라 한 명의 `ALTER TABLE`이 둘의 로컬을 동시에 바꾼다.

## 가. `settlement_batches` — 배치 실행 이력

| 컬럼 | 타입 | 제약 | 설명 | 출처 |
|---|---|---|---|---|
| `id` | bigint | PK | | `ERD §2` |
| `target_date` | date | NOT NULL | 정산 대상 일자. 중복 실행 판정 키 | `ERD §2` |
| `status` | varchar | NOT NULL | `RUNNING` / `CONFIRMED` / `PAID` / `FAILED` | `ERD §2` |
| `executed_at` | timestamptz | NOT NULL | 실행 시각 | `ERD §2` |
| `total_payout_amount` | numeric | 🚧 제안 | 배치 전체 지급액 합계. 대사 비교 대상을 매번 SUM하지 않기 위함 | 제안 |
| `reconciliation_status` | varchar | 🚧 제안 | `MATCHED` / `MISMATCHED` / `SKIPPED` | 제안 |
| `confirmed_at` | timestamptz | 🚧 제안 | `CONFIRMED` 전이 시각 | 제안 |

**제약**: `target_date` 단위로 **중복 `CONFIRMED` 방지** (`ERD §4`). 부분 UNIQUE 인덱스(`WHERE status = 'CONFIRMED'`)로 DB에서 강제할지, 애플리케이션 검증만 둘지 🚧.

> DB 제약으로 강제하는 편을 권한다. `FR-B-06`의 애플리케이션 사전검사만으로는 두 프로세스가 동시에 검사를 통과하는 경합을 막지 못한다. 결제의 `idempotency_key` UNIQUE가 같은 역할을 하는 것과 동일한 논리다. **단, 이는 제안이며 회의 확정 대상이다.**

## 나. `settlement_items` — 정산 상세

| 컬럼 | 타입 | 제약 | 설명 | 출처 |
|---|---|---|---|---|
| `id` | bigint | PK | | `ERD §2` |
| `settlement_batch_id` | bigint | FK → `settlement_batches` | | `ERD §2` |
| `driver_id` | varchar | NOT NULL | 기사 ID | `ERD §2` |
| `trip_ids` | 🚧 | NOT NULL | 포함된 운행 ID 목록. `FR-B-08` 추적성의 핵심 필드 | `ERD §2` |
| `amount` | numeric | NOT NULL | 지급액 | `ERD §2` |
| `payout_status` | varchar | NOT NULL | `CONFIRMED` / `PAID` | `ERD §2` |
| `fare_total` | numeric | 🚧 제안 | 운임 합계 (수수료 차감 전) | 제안 |
| `fee_amount` | numeric | 🚧 제안 | 차감된 수수료 | 제안 |

> `fare_total`·`fee_amount`를 제안하는 이유는 `FR-B-08`이다. `amount`만 남기면 "16,000원"은 알아도 "운임 20,000원에서 수수료 4,000원을 뗐다"를 응답 시점에 재계산해야 한다. 수수료율이 나중에 바뀌면 과거 정산을 설명할 수 없게 된다.

**`trip_ids` 저장 방식 🚧**

| | (A) PostgreSQL 배열 / JSONB | (B) 별도 매핑 테이블 | (C) 콤마 구분 문자열 |
|---|---|---|---|
| 조회 | 항목 1건으로 완결 | JOIN 필요 | 항목 1건으로 완결 |
| 개별 운행 검색 | 인덱스 필요 | 쉬움 | 사실상 불가 |
| 구현 부담 | 중 | 상 (테이블 1개 추가) | 하 |

**서비스 경계를 넘는 FK 🚧 (U10)**: `trip_ids`가 `trips`를 FK로 참조하면 정산이 결제 테이블에 물리적으로 묶인다. `CTR §0`의 경계 규칙과 충돌하므로 **참조 없이 ID만 보관**하는 편을 권하나, 회의 확정 대상이다.

## 다. Spring Batch 메타 테이블

`BATCH_JOB_INSTANCE` 등은 Spring Batch가 자동 생성한다. **정산 서비스 소유로 본다** (`ERD §2`). `FR-B-07`의 실패 재시작이 이 테이블에 의존한다.

---

**이전** → [외부 인터페이스 요구사항](./04-external-interfaces.md) · **다음** → [에러 코드](./06-error-codes.md)
