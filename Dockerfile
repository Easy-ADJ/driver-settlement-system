# 빌드와 실행을 나눈다. 실행 이미지에 Gradle과 소스가 남으면 이미지가 몇 배로 커지고,
# 콜드스타트가 느려지는 만큼 시연 첫 호출이 위태로워진다.
FROM amazoncorretto:17 AS build

WORKDIR /app

# Corretto 이미지에는 findutils(xargs)가 없다. Gradle 래퍼 스크립트가 xargs를 쓰므로
# 없으면 "xargs is not available"로 빌드가 바로 멈춘다.
RUN yum install -y findutils && yum clean all

# 래퍼와 의존성 선언을 먼저 복사한다. 소스만 바뀐 재배포에서 의존성 다운로드 레이어가
# 그대로 재사용되므로 빌드가 훨씬 빨라진다.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src ./src

# 테스트는 여기서 돌리지 않는다. Testcontainers가 Docker를 요구하는데
# 이미지 빌드 안에는 Docker 데몬이 없다. 테스트는 로컬과 CI에서 돌린다.
RUN ./gradlew bootJar --no-daemon -x test

# 실행 이미지는 alpine으로 줄인다. 빌드 도구가 빠지면서 800MB대가 300MB대가 된다 —
# 콜드스타트에서 이미지를 끌어와야 할 때 그 차이가 그대로 대기 시간이 된다.
FROM amazoncorretto:17-alpine

WORKDIR /app

# alpine에는 시간대 데이터가 없다. 없으면 TZ가 무시되고 로그가 UTC로 찍힌다.
RUN apk add --no-cache tzdata

COPY --from=build /app/build/libs/*.jar app.jar

# 컨테이너에 실제로 할당된 메모리를 기준으로 힙을 잡는다. 이게 없으면 JVM이 호스트 전체
# 메모리를 보고 힙을 크게 잡았다가 컨테이너 한도에서 OOM으로 죽는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# 시각 표기를 서울로 맞춘다. 배치의 날짜 계산은 코드에서 Asia/Seoul을 못박고 있으므로
# 여기에 의존하지 않지만, 로그 타임스탬프가 UTC면 사고 시각을 맞춰보기 번거롭다.
ENV TZ="Asia/Seoul"

# PORT는 플랫폼이 주입한다. EXPOSE는 문서 역할이며 실제 바인딩은 server.port가 정한다.
EXPOSE 8080

# exec 형태로 띄워 JVM이 PID 1이 되게 한다. 그래야 플랫폼이 보내는 SIGTERM을 직접 받아
# 배치 도중 재배포되어도 커넥션을 정리하고 내려간다.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
