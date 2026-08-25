# Build com contexto na propria pasta bff/ (nao depende mais de automation/ -
# a automacao agora e um servico HTTP proprio, chamado via REST, ver
# AutomationProperties.java / HttpReportJobRunner.java):
#   docker build -t consolidador-backend .

# ---- Stage 1: build do jar (Maven) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime (so JRE) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

ENV SERVER_PORT=3210
EXPOSE 3210

ENTRYPOINT ["java", "-jar", "app.jar"]
