# Driver-Settlement-System
"모빌리티 정산 서비스"의 "운전 기사 정산 시스템" 레포지토리입니다!

| | |
|---|---|
| 요구사항·설계 문서 | [`docs/`](./docs/README.md) |
| 배포 절차와 주의사항 | [`DEPLOYMENT.md`](./DEPLOYMENT.md) |
| 로컬 환경변수 | [`.env.example`](./.env.example)을 `.env`로 복사해 채운다 |

## 실행

```bash
./gradlew bootRun     # .env 의 값이 필요하다
./gradlew test        # Testcontainers 를 쓰므로 Docker 가 떠 있어야 한다
```
