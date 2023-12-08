FROM docker.io/gradle:jdk17 as buildstage

COPY src/ /work/src
COPY gradle/ /work/gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew /work/

WORKDIR /work
RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

COPY --from=buildstage /work/build/install/ /
WORKDIR /waltid-web-wallet

EXPOSE 4545
ENTRYPOINT ["/waltid-web-wallet/bin/waltid-web-wallet"]
