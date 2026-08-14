> [📚 문서 목록](../README.md) › [📑 요구사항 정의서](./index.html) › §8

# 🔒 제약 사항

| ID | 제약 | 출처 |
|---|---|---|
| `CST-01` | Java 17 · Spring Boot 4.1.0 · Gradle Kotlin DSL · Corretto 21.0.12 · Jar. 3개 서비스가 동일하게 맞춘다 | 팀 합의 |
| `CST-02` | 다른 서비스 소유 테이블(`trips`, `payments`, `ledger_accounts`, `ledger_entries`) 직접 접근 금지. **DB가 분리돼 물리적으로도 불가능하다** — 결합점은 API 계약으로 옮겨갔다 | `CTR §0`, `ARCH §2` |
| `CST-03` | 서버 간 호출 주소는 환경변수로 주입 (`PAYMENT_SERVICE_URL`, `LEDGER_SERVICE_URL`). 하드코딩 금지 | `ARCH §1` |
| `CST-04` | 🔒 접속 문자열·API 키·비밀번호를 코드·문서에 적지 않는다. `application.properties`는 `${...}` 참조만. `.env`는 `.gitignore` | 팀 합의 |
| `CST-05` | 패키지 루트 `com.example.driversettlementsystem`. 클래스 `PascalCase`, 메서드·변수 `camelCase`, 상수 `UPPER_SNAKE_CASE` | 팀 합의 |
| `CST-06` | 중괄호 Allman 스타일, 들여쓰기 스페이스 4칸, 한 줄 최대 120자, import 와일드카드 금지 | 팀 합의 |
| `CST-07` | API·스키마 변경은 **문서·이슈가 코드보다 먼저**. `contract`/`schema` 라벨 이슈는 문서 갱신까지 끝나야 close | `CTR §3` |
| `CST-08` | PostgreSQL 인스턴스를 **시스템마다 1개씩** 둔다. 정산 서버는 정산 DB에만 접속한다 (🚧 제품은 AWS Aurora / Supabase 중 미정) | `ARCH §1` |
| `CST-09` | 서버 3개는 **AWS EC2 1대의 Docker 컨테이너 3개**로 배포한다. 클라이언트는 Vercel | `ARCH §5` |

---

**이전** → [에러 코드](./06-error-codes.md) · **다음** → [추적성 매트릭스](./08-traceability-matrix.md)
