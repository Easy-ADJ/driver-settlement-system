> [📚 문서 목록](../README.md) › [📄 요구사항 기술서](./index.html) › §8

# 🔒 제약 사항

## 가. 기술 스택 (팀 합의로 고정)

| 항목 | 값 |
|---|---|
| 언어 / 프레임워크 | Java 17 · Spring Boot 4.1.0 |
| 빌드 | Gradle (Kotlin DSL) |
| JDK | Amazon Corretto 21.0.12 (컴파일 타깃 Java 17) |
| DB | Supabase PostgreSQL — 인스턴스 1개를 3개 서비스 공유 |
| 배치 | Spring Batch (정산 서버 내부) |
| 패키징 | Jar |

3개 서비스가 이 값을 **동일하게** 맞춘다. 정산 서버 혼자 버전을 올리지 않는다.

## 나. 코드·명명 제약

- 패키지 루트 `com.example.driversettlementsystem` — 아티팩트명(레포명)에서 하이픈 제거
- 클래스 `PascalCase` / 메서드·변수 `camelCase` / 상수 `UPPER_SNAKE_CASE` / 패키지 전부 소문자
- 중괄호 **Allman 스타일**, 들여쓰기 스페이스 4칸, 한 줄 최대 120자
- import 와일드카드 금지. 클래스·메서드는 Javadoc, 로직 설명은 `//`

## 다. 운영·보안 제약

- **다른 서비스 소유 테이블 직접 접근 금지** ([§4.3 경계 규칙](./03-scope.md))
- 서버 간 호출 주소는 **환경변수로 주입**한다. 하드코딩 금지 — 예: `PAYMENT_SERVICE_URL`, `LEDGER_SERVICE_URL`
- 🔒 **접속 문자열·API 키·비밀번호를 코드나 문서에 적지 않는다.** private 레포여도 마찬가지다. 커밋된 비밀정보는 이력에 영구히 남는다.<br>`application.properties`에는 `${SPRING_DATASOURCE_URL}` 형태의 참조만 두고 값은 환경변수로 주입하며, `.env`는 반드시 `.gitignore`에 넣는다
- API 시그니처·필드·에러 코드·DB 스키마 변경은 **코드보다 문서와 이슈가 먼저다** (`service-contracts.md` §3)

## 라. 일정 제약

| 날짜 | 내용 |
|---|---|
| 08.11(화) | 중간 회의 — 🚧 항목 결정 |
| 🚧 미정 | 서버 간 통합 테스트 — 일정표에 자리가 비어 있다 |
| 08.16~17 | 중간 발표 자료 제작 |
| 08.18(화) | 팀밋업 (중간 점검) |
| 08.25(화) | 성과교류회 (최종 발표) |

3개 서버를 병렬 개발하므로 **각자 완성해도 합쳐지지 않으면 데모가 불가능하다.**

---

**이전** → [비기능 요구 서술](./06-non-functional-requirements.md) · **다음** → [가정 및 의존](./08-assumptions.md)
