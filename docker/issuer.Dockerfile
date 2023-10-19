FROM docker.io/gradle:jdk17 as buildstage

COPY gradle/ /work/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew /work/
COPY waltid-credentials/build.gradle.kts /work/waltid-credentials/
COPY waltid-crypto/build.gradle.kts /work/waltid-crypto/
COPY waltid-did/build.gradle.kts /work/waltid-did/
COPY waltid-openid4vc/build.gradle.kts /work/waltid-openid4vc/
COPY waltid-issuer/build.gradle.kts /work/waltid-issuer/

WORKDIR /work/waltid-issuer/
#RUN pwd && ls && pwd && ls -la *
RUN gradle build || return 0

COPY waltid-credentials/. /work/waltid-credentials
COPY waltid-crypto/. /work/waltid-crypto
COPY waltid-did/. /work/waltid-did
COPY waltid-openid4vc/. /work/waltid-openid4vc
COPY waltid-issuer/. /work/waltid-issuer

#RUN pwd && ls /work/ && pwd && ls -la /work/*
RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

COPY --from=buildstage /work/waltid-issuer/build/install/ /
WORKDIR /waltid-issuer

EXPOSE 7000

ENTRYPOINT ["/waltid-issuer/bin/waltid-issuer"]
