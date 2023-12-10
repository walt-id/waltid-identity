FROM docker.io/gradle:jdk17 as buildstage

COPY ../gradle/ /work/gradle
COPY ../settings.gradle.kts ../build.gradle.kts ../gradle.properties ../gradlew /work/
COPY ../waltid-verifiable-credentials/build.gradle.kts /work/waltid-verifiable-credentials/
COPY ../waltid-crypto/build.gradle.kts /work/waltid-crypto/
COPY ../waltid-did/build.gradle.kts /work/waltid-did/
COPY ../waltid-openid4vc/build.gradle.kts /work/waltid-openid4vc/
COPY ../waltid-verifier/build.gradle.kts /work/waltid-verifier/

WORKDIR /work/waltid-verifier/
#RUN pwd && ls && pwd && ls -la *
RUN gradle build || return 0

COPY ../waltid-verifiable-credentials/. /work/waltid-verifiable-credentials
COPY ../waltid-crypto/. /work/waltid-crypto
COPY ../waltid-did/. /work/waltid-did
COPY ../waltid-openid4vc/. /work/waltid-openid4vc
COPY ../waltid-verifier/. /work/waltid-verifier

#RUN pwd && ls /work/ && pwd && ls -la /work/*
RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

COPY --from=buildstage /work/waltid-verifier/build/install/ /
WORKDIR /waltid-verifier

EXPOSE 7001

ENTRYPOINT ["/waltid-verifier/bin/waltid-verifier"]
