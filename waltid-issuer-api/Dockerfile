FROM docker.io/gradle:jdk17 as buildstage

COPY gradle/ /work/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew /work/
COPY waltid-verifiable-credentials/build.gradle.kts /work/waltid-verifiable-credentials/
COPY waltid-crypto/build.gradle.kts /work/waltid-crypto/
COPY waltid-did/build.gradle.kts /work/waltid-did/
COPY waltid-openid4vc/build.gradle.kts /work/waltid-openid4vc/
COPY waltid-issuer-api/build.gradle.kts /work/waltid-issuer-api/
COPY waltid-sdjwt/build.gradle.kts /work/waltid-sdjwt/
COPY waltid-mdoc-credentials/build.gradle.kts /work/waltid-mdoc-credentials/

WORKDIR /work/waltid-issuer-api/
RUN gradle build || return 0

COPY waltid-verifiable-credentials/. /work/waltid-verifiable-credentials
COPY waltid-crypto/. /work/waltid-crypto
COPY waltid-did/. /work/waltid-did
COPY waltid-openid4vc/. /work/waltid-openid4vc
COPY waltid-issuer-api/. /work/waltid-issuer-api
COPY waltid-sdjwt/. /work/waltid-sdjwt
COPY waltid-mdoc-credentials/. /work/waltid-mdoc-credentials

RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

# Non-root user
RUN useradd --create-home waltid

COPY --from=buildstage /work/waltid-issuer-api/build/install/ /
#RUN chown -R waltid:waltid /waltid-issuer-api

WORKDIR /waltid-issuer-api

RUN mkdir /waltid-issuer-api/config && chown waltid:waltid /waltid-issuer-api/config
USER waltid

EXPOSE 7002
ENTRYPOINT ["/waltid-issuer-api/bin/waltid-issuer-api"]
