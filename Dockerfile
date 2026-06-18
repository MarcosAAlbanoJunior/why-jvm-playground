# Estagio de build: JDK 25 + Maven Wrapper (baixa o Maven 3.9 sozinho).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Primeiro so o necessario para resolver dependencias: aproveita o cache de camadas
# do Docker quando muda apenas o codigo-fonte.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B dependency:go-offline

# Agora o codigo e o empacotamento. Testes sao pulados aqui: o teste de contexto
# do Spring exige um Postgres, que so existe em runtime via docker-compose.
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# Estagio de runtime padrao: a aplicacao sozinha, sem agente (roda standalone).
FROM eclipse-temurin:25-jdk AS runtime
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Estagio com o agente why-jvm anexado, em MODO LOG (sem forward.url: o laudo do
# incidente sai no log da propria aplicacao, sem precisar do why-jvm-mcp).
#
# Os dois jars vem de RELEASES publicadas no GitHub — nada de codigo local:
#   - o agente OpenTelemetry (repo do OTel)
#   - a extensao why-jvm (repo why-jvm-mcp; exige a release publicada)
#
# Build deste estagio: docker compose -f docker-compose.yml -f docker-compose.whyjvm.yml up --build
FROM runtime AS runtime-whyjvm
ARG OTEL_AGENT_VERSION=2.28.1
ARG WHYJVM_RELEASE=v0.1.0-rc6
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /agent/opentelemetry-javaagent.jar
ADD https://github.com/MarcosAAlbanoJunior/why-jvm/releases/download/${WHYJVM_RELEASE}/why-jvm-otel-extension-all.jar /agent/why-jvm-extension.jar
# Fontes da app na imagem: habilitam o code-aware. O resolver busca pela
# arvore de pacotes (ex.: io/whyjvm/playground/.../CheckoutService.java) sob esta
# raiz, recorta a janela ao redor da linha culpada e o laudo mostra o codigo.
COPY --from=build /build/src/main/java /app/sources
# O JVM aplica JAVA_TOOL_OPTIONS automaticamente: o ENTRYPOINT continua "java -jar".
ENV JAVA_TOOL_OPTIONS="-javaagent:/agent/opentelemetry-javaagent.jar -Dotel.javaagent.extensions=/agent/why-jvm-extension.jar -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none -Dwhyjvm.app.packages=io.whyjvm.playground -Dwhyjvm.source.dirs=/app/sources -Dwhyjvm.forward.url=http://why-jvm-mcp:9090"
