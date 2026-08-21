# 🚀 정산 서버 배포

Railway에 정산 서버를 올리는 절차와, 올리기 전에 알아야 할 것들.

> 🔒 **접속 정보·비밀번호는 이 문서에도 레포에도 적지 않는다.** 값은 Railway 대시보드에만 넣는다. private 레포여도 커밋된 비밀정보는 이력에 영구히 남는다.

> Railway·Supabase는 **AWS 계정 지급이 지연돼 개발을 멈추지 않으려고 택한 잠정 선택**이다. 그래서 플랫폼 전용 설정 파일(`railway.json` 등)을 레포에 두지 않았다. **옮길 때 고칠 곳이 환경변수 값과 대시보드 설정뿐이어야 한다.** `Dockerfile`은 Render·Fly·ECS 어디서든 그대로 쓸 수 있다.

---

## 1. 환경변수

Railway 대시보드 → Variables 에 넣는다. **하나라도 빠지면 앱이 뜨지 않는다** — `application.properties`가 기본값 없이 `${...}`로만 참조하기 때문이다. 조용히 잘못된 값으로 도는 것보다 낫다.

| 변수 | 값 | 없으면 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | 정산 DB (Supabase) JDBC URL | 부팅 실패 |
| `SPRING_DATASOURCE_USERNAME` | 정산 DB 계정 | 부팅 실패 |
| `SPRING_DATASOURCE_PASSWORD` | 정산 DB 비밀번호 | 부팅 실패 |
| `AUTH_DATASOURCE_URL` | 로그인 DB JDBC URL | 부팅 실패 |
| `AUTH_DATASOURCE_USERNAME` | `settlement_ro` (읽기 전용) | 부팅 실패 |
| `AUTH_DATASOURCE_PASSWORD` | 로그인 DB 비밀번호 | 부팅 실패 |
| `PAYMENT_API_BASE_URL` | 결제 서버 주소 (`https://...`, 끝에 `/` 없이) | 부팅 실패 |
| `LEDGER_API_BASE_URL` | 원장 서버 주소 | 부팅 실패 |
| `SETTLEMENT_BATCH_CRON` | 선택. 비우면 매일 새벽 3시 (`0 0 3 * * *`) | 기본값 사용 |
| `PORT` | **넣지 않는다.** Railway가 직접 주입한다 | — |

> ⚠️ **변수 이름이 이슈 #40 본문과 다르다.** 이슈에는 `PAYMENT_SERVICE_URL`·`LOGIN_DATASOURCE_URL`로 적혀 있는데, 실제 코드가 읽는 이름은 위 표가 맞다. 대시보드에 이슈의 이름으로 넣으면 **부팅이 실패한다.**

### 로컬에서는

`.env.example`을 `.env`로 복사해 값을 채운다. `.env`는 `.gitignore`에 있다.

---

## 2. 배포 설정

| 항목 | 값 |
|---|---|
| Builder | **Dockerfile** (레포 루트) |
| Health Check Path | `/actuator/health/liveness` |
| Health Check Timeout | 60초 이상 |
| Restart Policy | `ON_FAILURE`, 최대 3회 |

### 헬스체크 경로를 `/actuator/health`가 아니라 `liveness`로 두는 이유

두 엔드포인트의 쓰임이 다르다.

| 경로 | 보는 것 | 쓰임 |
|---|---|---|
| `/actuator/health` | 앱 + **DB 연결** | 사람이 "제대로 붙었나" 확인할 때 |
| `/actuator/health/liveness` | 앱이 살아 있는지만 | 플랫폼의 재시작 판단 |

`/actuator/health`를 플랫폼 헬스체크로 걸면, **로그인 DB가 잠깐 죽었을 때 정산 서버 전체가 재시작 루프에 빠진다.** 로그인 DB는 기사 이름·계좌번호를 읽는 용도라 그게 없어도 배치와 조회는 정상 동작하는데, 그것까지 함께 멈춘다.

**배포 직후에는 사람이 `/actuator/health`를 한 번 열어 `UP`을 확인한다.** 그게 DB 연결까지 확인하는 유일한 방법이다.

---

## 3. 배포 절차

1. Railway 프로젝트 생성 → 이 레포 연결 → 브랜치 `main`
2. Variables에 위 표의 값을 넣는다 (`PORT` 제외)
3. Settings → Health Check Path에 `/actuator/health/liveness`
4. 배포가 끝나면 **`https://<도메인>/actuator/health`를 열어 `{"status":"UP"}` 확인**
5. 공개 주소를 팀에 공유한다

이후 `main`에 push하면 자동으로 재배포된다.

### 첫 배포에서 확인할 것

```bash
# 1. 떴는가 (DB 연결 포함)
curl https://<도메인>/actuator/health

# 2. 스키마가 만들어졌는가 — 배치를 돌려보면 안다
curl -X POST "https://<도메인>/api/settlements/batch?targetDate=2026-08-20"

# 3. 조회가 되는가
curl "https://<도메인>/api/settlements?date=2026-08-20"
```

2번이 `LEDGER_SERVICE_UNAVAILABLE`로 실패하면 **정산 서버는 정상이고 원장 주소가 틀렸거나 원장이 잠들어 있는 것**이다. 정산 서버 자체 문제라면 500 `INTERNAL_ERROR`가 나온다.

### 스키마

Flyway가 앱 시작 시 `src/main/resources/db/migration`을 적용한다. 별도 작업이 없다.

**적용된 마이그레이션 파일은 절대 수정하지 않는다.** 체크섬이 달라져 다음 배포가 부팅 실패한다. 고칠 것이 생기면 `V2`를 새로 만든다.

---

## 4. ⚠️ Railway에서 조심할 것

### 4.1 유휴 인스턴스가 잠든다

Railway는 트래픽이 없으면 인스턴스를 재운다. 첫 호출이 그만큼 느려진다.

서버 간 타임아웃을 **연결 5초 / 응답 10초**로 잡아 두었지만, 콜드스타트가 그보다 길면 **정상 코드가 타임아웃으로 죽는다.**

> **📋 시연 직전 체크리스트**
>
> 발표 당일 첫 호출이 실패하는 것이 이 프로젝트에서 가장 흔한 사고다. 발표 **10분 전에** 세 서버를 한 번씩 깨워둔다.
>
> ```bash
> curl https://<정산 도메인>/actuator/health
> curl https://<원장 도메인>/actuator/health
> curl https://<결제 도메인>/actuator/health
> ```
>
> 그리고 **시연에 스케줄 배치를 쓰지 않는다.** 자정을 기다릴 수도 없고, 잠든 인스턴스에서 스케줄이 돌았는지 확인할 방법도 없다. `POST /api/settlements/batch`로 보여준다.

### 4.2 스케줄러는 잠든 인스턴스에서 안 돈다

`@Scheduled`는 앱이 살아 있어야 동작하고, **놓친 실행을 나중에 따라잡지 않는다.** 그날 배치는 그냥 건너뛴다.

메우는 방법은 날짜를 지정해 수동 실행하는 것뿐이다.

```bash
curl -X POST "https://<도메인>/api/settlements/batch?targetDate=2026-08-20"
```

### 4.3 🔴 Job 실행 이력이 재시작하면 사라진다

**Spring Boot 4는 JDBC JobRepository를 자동 설정하지 않는다.** Spring Batch 6의 기본값이 `ResourcelessJobRepository`, 즉 **메모리에만 남는 저장소**다. 실제로 컨테이너를 띄워 확인했다 — `BATCH_JOB_INSTANCE` 같은 메타 테이블이 DB에 생기지 않는다.

Railway는 인스턴스를 자주 재우므로 **이 초기화가 자주 일어난다.**

| | 재시작 후에도 유지되나 |
|---|---|
| **확정된 날짜 재실행 거부** (`DuplicateBatchGuard` + 부분 UNIQUE 인덱스) | ✅ **유지된다** — DB가 지킨다 |
| 같은 날짜 두 번째 실행 거부 (`JobInstanceAlreadyCompleteException`) | ❌ 초기화된다 |
| 실패 지점부터 재시작 (`FR-B-07`) | ❌ 애초에 성립하지 않는다 |

**"중복 없이 한 번만"이라는 핵심 보장은 깨지지 않는다.** 그건 애플리케이션 검사와 DB 부분 UNIQUE 인덱스 두 겹이 지키고, 둘 다 DB에 있다.

깨지는 것은 **확정 전 날짜를 재시작 후에 다시 돌리면 배치 레코드가 하나 더 생기는 것**이다. 조회 API는 가장 최근 배치만 보므로 결과는 맞지만, 이력이 지저분해진다.

→ 고치려면 `@EnableJdbcJobRepository`와 Spring Batch 6 메타 테이블 마이그레이션(`V2`)이 필요하다. **스키마 변경이라 별도 이슈로 뺐다.**

---

## 5. Supabase에서 조심할 것

### 5.1 연결 방식

Supabase는 접속 경로가 세 가지다.

| 경로 | 포트 | 프리페어드 스테이트먼트 |
|---|---|---|
| Direct connection | 5432 | ✅ 문제없음 |
| Supavisor **session** 모드 | 5432 | ✅ 문제없음 |
| Supavisor **transaction** 모드 | 6543 | ❌ **깨진다** |

**transaction 모드를 쓰면 Hibernate가 조용히 실패한다.** 커넥션이 요청마다 바뀌는데 프리페어드 스테이트먼트는 커넥션에 묶여 있기 때문이다. 증상이 "가끔 쿼리가 실패한다"로 나타나 원인을 찾기 어렵다.

**session 모드(5432)를 쓴다.** 꼭 transaction 모드를 써야 한다면 JDBC URL에 `prepareThreshold=0`을 붙인다.

### 5.2 커넥션 수

Supabase 무료 플랜은 동시 연결 수가 넉넉하지 않다. **세 서버 + 대시보드가 같은 인스턴스를 볼 수 있다.**

정산 서버는 로그인 DB 풀을 3으로 묶어 두었다(`auth.datasource.hikari.maximum-pool-size=3`). 기사 정보 조회는 빈도가 낮아서다. 정산 DB 풀은 기본값(10)이다.

---

## 6. 로컬에서 컨테이너로 확인하기

배포 전에 같은 이미지로 한 번 돌려보면 대부분의 사고를 미리 잡는다.

```bash
docker build -t settlement .

docker run --rm -p 8080:8080 \
  -e PORT=8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://<host>:5432/<db>" \
  -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... \
  -e AUTH_DATASOURCE_URL="jdbc:postgresql://<host>:5432/<db>" \
  -e AUTH_DATASOURCE_USERNAME=... \
  -e AUTH_DATASOURCE_PASSWORD=... \
  -e PAYMENT_API_BASE_URL="https://..." \
  -e LEDGER_API_BASE_URL="https://..." \
  settlement

curl http://localhost:8080/actuator/health
```

**확인된 값** (로컬 컨테이너 + PostgreSQL 18 기준)

| 항목 | 값 |
|---|---|
| 이미지 크기 | 564MB |
| 기동 시간 | 약 4초 |
| Flyway 마이그레이션 | 자동 적용됨 (`batches`·`settlements`) |

기동 4초는 **앱이 뜨는 시간**이고, Railway 콜드스타트는 여기에 컨테이너를 깨우는 시간이 더해진다. 시연 전 예열이 필요한 이유다.

---

## 7. 문제가 생기면

| 증상 | 먼저 볼 것 |
|---|---|
| 502 / 앱은 떴는데 접속 불가 | `server.port=${PORT:8080}`가 있는지. 없으면 8080에서 듣는데 프록시는 다른 포트를 두드린다 |
| 부팅 즉시 실패 | 환경변수 이름 오타. `PAYMENT_SERVICE_URL`이 아니라 **`PAYMENT_API_BASE_URL`**이다 |
| `Flyway ... checksum mismatch` | 이미 적용된 마이그레이션 파일을 고쳤다. 되돌리고 `V2`를 새로 만든다 |
| 배치가 항상 0건 | 원장에 분개가 없는 것이다. 결제 서버가 원장에 쓰고 있는지 확인한다 |
| 대사가 항상 `SKIPPED` | 결제 `GET /api/payments?date=`가 아직 없다. 설계된 동작이며 배치는 정상이다 |
| 쿼리가 간헐적으로 실패 | Supabase transaction 모드(6543)를 쓰고 있지 않은지 (§5.1) |
