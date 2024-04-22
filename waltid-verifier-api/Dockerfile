FROM docker.io/gradle:jdk17 as buildstage

COPY gradle/ /work/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew /work/
COPY waltid-verifiable-credentials/build.gradle.kts /work/waltid-verifiable-credentials/
COPY waltid-crypto/build.gradle.kts /work/waltid-crypto/
COPY waltid-did/build.gradle.kts /work/waltid-did/
COPY waltid-openid4vc/build.gradle.kts /work/waltid-openid4vc/
COPY waltid-verifier-api/build.gradle.kts /work/waltid-verifier-api/
COPY waltid-sdjwt/build.gradle.kts /work/waltid-sdjwt/
COPY waltid-mdoc-credentials/build.gradle.kts /work/waltid-mdoc-credentials/

WORKDIR /work/waltid-verifier-api/
RUN gradle build || return 0

COPY waltid-verifiable-credentials/. /work/waltid-verifiable-credentials
COPY waltid-crypto/. /work/waltid-crypto
COPY waltid-did/. /work/waltid-did
COPY waltid-openid4vc/. /work/waltid-openid4vc
COPY waltid-verifier-api/. /work/waltid-verifier-api
COPY waltid-sdjwt/. /work/waltid-sdjwt
COPY waltid-mdoc-credentials/. /work/waltid-mdoc-credentials

RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

# Non-root user
RUN useradd --create-home waltid

COPY --from=buildstage /work/waltid-verifier-api/build/install/ /

WORKDIR /waltid-verifier-api

RUN mkdir /waltid-verifier-api/config && chown waltid:waltid /waltid-verifier-api/config
USER waltid

EXPOSE 7003
ENTRYPOINT ["/waltid-verifier-api/bin/waltid-verifier-api"]
