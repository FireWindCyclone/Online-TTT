FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

COPY pom.xml mvnw* ./
COPY .mvn .mvn
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

ENV JAVA_OPTS="\
    -Xms128m \
    -Xmx256m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+UseStringDeduplication \
    -XX:MaxMetaspaceSize=128m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
