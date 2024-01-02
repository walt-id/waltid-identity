FROM docker.io/gradle:jdk17 as buildstage

COPY waltid-wallet-api/src/ /work/src
COPY waltid-wallet-api/gradle/ /work/gradle
COPY waltid-wallet-api/build.gradle.kts waltid-wallet-api/settings.gradle.kts waltid-wallet-api/gradle.properties waltid-wallet-api/gradlew /work/

WORKDIR /work
RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

COPY --from=buildstage /work/build/install/ /
WORKDIR /waltid-wallet-api

EXPOSE 4545
ENTRYPOINT ["/waltid-wallet-api/bin/waltid-wallet-api"]
