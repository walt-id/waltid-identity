FROM docker.io/gradle:jdk21 as buildstage

COPY waltid-web-wallet/src/ /work/src
COPY waltid-web-wallet/gradle/ /work/gradle
COPY waltid-web-wallet/build.gradle.kts waltid-web-wallet/settings.gradle.kts waltid-web-wallet/gradle.properties waltid-web-wallet/gradlew /work/

WORKDIR /work
RUN gradle clean installDist

FROM docker.io/eclipse-temurin:21

COPY --from=buildstage /work/build/install/ /
WORKDIR /waltid-web-wallet

EXPOSE 4545
ENTRYPOINT ["/waltid-web-wallet/bin/waltid-web-wallet"]
