FROM docker.io/amazoncorretto:23.0.1-alpine AS builder
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM docker.io/amazoncorretto:23.0.1-alpine
WORKDIR /workspace
COPY --from=builder spring-boot-loader /workspace
COPY --from=builder dependencies /workspace
COPY --from=builder snapshot-dependencies /workspace
COPY --from=builder application /workspace
COPY ./root/ /root/
ENV JAVA_OPTS=--enable-preview
ENTRYPOINT java "$JAVA_OPTS" org.springframework.boot.loader.launch.JarLauncher