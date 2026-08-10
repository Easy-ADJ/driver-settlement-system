> [📚 문서 목록](../README.md) › [🧩 클래스 다이어그램](./index.html) › §9

# 🔎 관찰 메모

구현 전 확인이 필요한 사실이다. **변경하지 않고 기록만 한다.**

| # | 사실 | 판단 |
|---|---|---|
| 1 | `build.gradle.kts`에 `spring-boot-starter-data-redis`가 있으나 이 설계에서 Redis를 쓰지 않는다 | 프로젝트 초기 생성 시 포함된 것으로 보인다. 용도가 없다면 정리 대상이나, 이 문서 작업 범위가 아니다 |
| 2 | `build.gradle.kts`에 `mysql-connector-j`와 `postgresql` 드라이버가 함께 있다 | DB는 Supabase PostgreSQL로 확정됐다 (`ARCH §5`). MySQL 드라이버는 불필요해 보인다 |
| 3 | `src/main/resources/application.properties`에 `spring.application.name` 한 줄만 있다 | DataSource·Batch·가상 스레드 설정이 아직 없다. 🔒 값은 환경변수로 주입하고 파일에는 `${...}` 참조만 둔다 (`CST-04`) |
| 4 | `src/test/java`에 `TestcontainersConfiguration`이 생성돼 있다 | 로컬 테스트용 DB 분리 방식(🚧 미확정)의 후보로 쓸 수 있다 |

---

**이전** → [🚧 미확정이 클래스 구조에 미치는 영향](./08-open-issues-impact.md)
