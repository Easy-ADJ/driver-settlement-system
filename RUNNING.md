# 🏃 로컬에서 실행하기

정산 서버를 혼자 띄워서 **배치 → 확정 → 지급 → 조회** 한 바퀴를 눌러보는 방법.

**결제·원장 서버가 없어도 된다.** 원장은 아래 가짜 서버로 대신하고, 결제는 대사에만 쓰여서 없으면 `SKIPPED`가 나온다 — 오류가 아니라 설계된 동작이다.

> 배포는 [`DEPLOYMENT.md`](./DEPLOYMENT.md)에 따로 있다.

---

## 0. 먼저 알아둘 것 3가지

이 셋을 모르면 원인을 엉뚱한 데서 찾게 된다.

### `.env`는 자동으로 읽히지 않는다

`.env.example`이 있어서 읽힐 것 같지만, `application.properties`에 `spring.config.import` 설정이 없다. **환경변수를 직접 넣어야 한다.**

안 넣으면 부팅이 그 자리에서 실패한다. 기본값을 두지 않은 것은 의도된 것이다 — **조용히 잘못된 DB에 붙어 도는 것보다 안 뜨는 편이 낫다.**

### 5432 포트가 이미 쓰이고 있을 수 있다

PostgreSQL을 로컬에 설치해 두었다면 Docker 컨테이너와 포트가 겹친다. 그러면 앱이 **엉뚱한 DB에 붙어** 이렇게 뜬다.

```
FATAL: 사용자 "postgres"의 password 인증이 실패했습니다
```

비밀번호 문제로 보이지만 아니다. 아래 절차는 **55432**를 쓴다.

확인하려면:

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen
```

### PowerShell에서 `curl`은 `curl.exe`로 쓴다

`curl`은 PowerShell에서 `Invoke-WebRequest` 별칭이라 `-X POST` 같은 옵션이 다르게 해석된다.

---

## 1. PostgreSQL 띄우기

```powershell
docker run -d --name local-db -p 55432:5432 `
  -e POSTGRES_PASSWORD=localonly -e POSTGRES_DB=settlement `
  postgres:latest
```

스키마는 앱이 뜰 때 Flyway가 만든다. 따로 할 일이 없다.

> 🔒 `localonly`는 **로컬 전용 값**이다. 배포 환경 비밀번호를 여기에 쓰지 않는다.

---

## 2. 가짜 원장 서버 띄우기

원장이 없으면 배치가 `LEDGER_SERVICE_UNAVAILABLE`로 끝난다. 아래를 `fake-ledger.py`로 저장한다 (레포 밖 아무 데나).

```python
"""원장 서버 흉내. 정산 서버를 혼자 돌려보기 위한 도구다.

이중기입 검증 같은 진짜 원장 로직은 없다. 정산이 기대하는 응답 형태만 돌려준다.

    python fake-ledger.py [포트]     기본 9090
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs

# 3333원은 일부러 넣었다 — 20%가 666.6원이라 버림 규칙이 눈에 보인다.
DRIVERS = {
    1: {"unpaid": "20000", "payments": [(100, "12000"), (101, "8000")]},
    2: {"unpaid": "3333", "payments": [(102, "3333")]},
    3: {"unpaid": "0", "payments": []},
}

APPROVED_AT = "2026-08-20T14:30:00Z"
ledger_id_seq = 900


class FakeLedger(BaseHTTPRequestHandler):

    def do_GET(self):
        url = urlparse(self.path)
        query = parse_qs(url.query)

        if url.path == "/api/ledger/unpaid":
            date = query.get("date", ["2026-08-20"])[0]
            self._json(200, {
                "targetDate": date,
                "data": [
                    {"driverId": did,
                     "totalUnpaidAmount": info["unpaid"],
                     "lastApprovedAt": date + "T14:30:00Z"}
                    for did, info in DRIVERS.items()
                ],
            })
            return

        if url.path == "/api/ledger":
            did = int(query.get("driver_id", ["1"])[0])
            info = DRIVERS.get(did, {"unpaid": "0", "payments": []})
            self._json(200, {
                "driverId": did,
                "totalUnpaidAmount": info["unpaid"],
                "paymentDetails": [
                    {"paymentId": pid, "amount": amount, "approvedAt": APPROVED_AT}
                    for pid, amount in info["payments"]
                ],
            })
            return

        self._json(404, {"code": "NOT_FOUND", "message": url.path})

    def do_POST(self):
        global ledger_id_seq
        if urlparse(self.path).path == "/api/ledger/entries":
            length = int(self.headers.get("Content-Length", 0))
            body = json.loads(self.rfile.read(length) or b"{}")

            # 정산이 무엇을 보냈는지 눈으로 본다. ownerType이 특히 중요하다 —
            # 차변만 DRIVER여야 기사 미지급금이 줄어든다.
            print("  <- 상쇄 분개:", json.dumps(body, ensure_ascii=False))

            ledger_id_seq += 1
            self._json(201, {"ledgerId": ledger_id_seq})
            return

        self._json(404, {"code": "NOT_FOUND", "message": self.path})

    def _json(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("  ->", fmt % args)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9090
    print(f"가짜 원장 서버 http://localhost:{port}")
    HTTPServer(("0.0.0.0", port), FakeLedger).serve_forever()
```

**새 창에서** 띄운다. 계속 떠 있어야 한다.

```powershell
python fake-ledger.py
```

기사 3명을 준다 — 미지급 **20,000** / **3,333** / **0원**. 0원인 기사는 정산 항목이 생기지 않고, 3,333원은 수수료가 666.6원이라 **버림이 적용되는 게 보인다.**

---

## 3. 서버 띄우기

**또 다른 새 창에서:**

```powershell
cd "D:\대외 활동 자료\부트캠프 관련 자료\SW Pilot 4th\Easy-ADJ\driver-settlement-system"

$env:JAVA_HOME = "C:\Users\User\.jdks\corretto-17.0.20"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:55432/settlement"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "localonly"
$env:AUTH_DATASOURCE_URL = "jdbc:postgresql://localhost:55432/settlement"
$env:AUTH_DATASOURCE_USERNAME = "postgres"
$env:AUTH_DATASOURCE_PASSWORD = "localonly"
$env:LEDGER_API_BASE_URL = "http://localhost:9090"
$env:PAYMENT_API_BASE_URL = "http://localhost:9091"

.\gradlew bootRun
```

`PAYMENT_API_BASE_URL`은 **아무 값이나 넣는다.** 결제 서버는 대사에만 쓰이고, 없으면 `reconciliationStatus`가 `SKIPPED`로 나온다.

환경변수는 그 창에서만 유효하다. 창을 닫으면 다시 넣어야 한다.

떴는지 확인:

```powershell
curl.exe "http://localhost:8080/actuator/health"
# {"groups":["liveness","readiness"],"status":"UP"}
```

---

## 4. 한 바퀴 돌려보기

```powershell
# 1. 배치 실행
curl.exe -X POST "http://localhost:8080/api/settlements/batch?targetDate=2026-08-20"

# 2. 결과 조회
curl.exe "http://localhost:8080/api/settlements?date=2026-08-20"

# 3. 확정 — 원장에 상쇄 분개가 나간다 (가짜 원장 창에 찍힌다)
curl.exe -X POST "http://localhost:8080/api/settlements/1/confirm"

# 4. 지급 완료 표시
curl.exe -X POST "http://localhost:8080/api/settlements/1/pay"

# 5. 기사 지정 조회 — 결제 건별 근거까지
curl.exe "http://localhost:8080/api/settlements?date=2026-08-20&driverId=1"
```

### 나오는 값

| 단계 | 응답 |
|---|---|
| 1 | `{"batchId":1,"targetDate":"2026-08-20","batchStatus":"RUNNING","settlementCount":2}` |
| 2 | 기사 1: 운임 20,000 → 수수료 4,000 → 지급 **16,000**<br/>기사 2: 운임 3,333 → 수수료 **666**(버림) → 지급 **2,667** |
| 3 | `batchStatus`가 `CONFIRMED`로 |
| 4 | `batchStatus`·`payoutStatus` 둘 다 `PAID`로 |
| 5 | `payments`에 결제 건별 `paymentId`·금액·승인 시각 |

**여기서 확인할 것 3가지**

- **기사 3명 중 2건만 생긴다** — 미지급 0원인 기사는 정산 대상이 아니다
- **666원이지 667원이 아니다** — 팀 규약이 버림(`FLOOR`)이다. 반올림이면 원장과 1원이 어긋나 대사가 매일 실패한다
- **`reconciliationStatus`가 `SKIPPED`다** — 결제 서버가 없어서다. 배치는 정상이고, 이 상태에서는 자동 확정이 되지 않아 3번을 손으로 눌러야 한다

### 거부되는 것도 눌러보면 좋다

```powershell
curl.exe -X POST "http://localhost:8080/api/settlements/batch?targetDate=2026-08-20"  # 409 그날은 이미 돌았다
curl.exe -X POST "http://localhost:8080/api/settlements/1/pay"                        # 409 이미 PAID
curl.exe "http://localhost:8080/api/settlements?date=2026-01-01"                      # 404 그날 배치가 없다
curl.exe "http://localhost:8080/api/settlements"                                      # 400 date 누락
```

### DB에서 직접 보기

```powershell
docker exec local-db psql -U postgres -d settlement -c "select * from batches"
docker exec local-db psql -U postgres -d settlement -c "select * from settlements"
```

`settlements.ledger_id`에 값이 있으면 **원장에 상쇄 분개가 기록됐다는 뜻**이다. 비어 있으면 다음날 같은 금액이 또 정산된다.

---

## 5. 테스트 돌리기

```powershell
$env:JAVA_HOME = "C:\Users\User\.jdks\corretto-17.0.20"
.\gradlew test
```

**Docker Desktop이 떠 있어야 한다.** Testcontainers가 진짜 PostgreSQL을 띄워서 검증한다. 위 1번 컨테이너와는 무관하며 테스트가 알아서 만들고 지운다.

---

## 6. 정리

```powershell
docker rm -f local-db
```

가짜 원장과 서버는 각 창에서 `Ctrl+C`.

---

## 안 될 때

| 증상 | 원인 |
|---|---|
| `password 인증이 실패했습니다` | 5432에 다른 PostgreSQL이 있다. 포트가 **55432**인지 확인 |
| `Could not resolve placeholder 'SPRING_DATASOURCE_URL'` | 환경변수를 안 넣었다. `.env`는 자동으로 안 읽힌다 |
| 배치가 500 `LEDGER_SERVICE_UNAVAILABLE` | 가짜 원장이 안 떠 있거나 `LEDGER_API_BASE_URL`이 틀렸다 |
| `curl : 매개 변수를 찾을 수 없습니다` | `curl`이 아니라 **`curl.exe`** |
| 조회가 404인데 배치는 돌았다 | `date` 값이 배치의 `targetDate`와 다르다 |
| 포트 8080이 이미 사용 중 | 앞서 띄운 서버가 남아 있다. `$env:PORT = "8081"` 로 바꿔 띄운다 |
