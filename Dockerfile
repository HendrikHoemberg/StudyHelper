FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:resolve
RUN ./mvnw dependency:resolve-plugins
COPY src/ src/
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user to run the app
RUN addgroup -S spring && adduser -S spring -G spring

# Create uploads directory and set permissions
RUN mkdir -p /app/uploads && chown spring:spring /app/uploads

USER spring:spring

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar
VOLUME /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
