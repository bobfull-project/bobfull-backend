ARG EXTRACT_IMAGE=eclipse-temurin:17-jdk
ARG RUNTIME_IMAGE=gcr.io/distroless/java17-debian12

FROM ${EXTRACT_IMAGE} AS extractor

WORKDIR /workspace

COPY build/libs/*.jar app.jar

RUN ["java", "-Djarmode=tools", "-jar", "app.jar", "extract", "--launcher", "--destination", "extracted", "--layers", "dependencies,spring-boot-loader,snapshot-dependencies,application"]

FROM ${RUNTIME_IMAGE}

WORKDIR /app

COPY --from=extractor /workspace/extracted/dependencies/ ./
COPY --from=extractor /workspace/extracted/spring-boot-loader/ ./
COPY --from=extractor /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extractor /workspace/extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
