FROM docker.io/gradle:jdk17 as buildstage

COPY gradle/ /work/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew /work/

COPY waltid-libraries/waltid-verifiable-credentials/build.gradle.kts /work/waltid-libraries/waltid-verifiable-credentials/
COPY waltid-libraries/waltid-crypto/build.gradle.kts /work/waltid-libraries/waltid-crypto/
COPY waltid-libraries/waltid-did/build.gradle.kts /work/waltid-libraries/waltid-did/
COPY waltid-libraries/waltid-openid4vc/build.gradle.kts /work/waltid-libraries/waltid-openid4vc/
COPY waltid-libraries/waltid-sdjwt/build.gradle.kts /work/waltid-libraries/waltid-sdjwt/
COPY waltid-libraries/waltid-mdoc-credentials/build.gradle.kts /work/waltid-libraries/waltid-mdoc-credentials/

COPY waltid-services/waltid-service-commons/build.gradle.kts /work/waltid-services/waltid-service-commons/
COPY waltid-services/waltid-issuer-api/build.gradle.kts /work/waltid-services/waltid-issuer-api/

WORKDIR /work/waltid-services/waltid-issuer-api/
RUN gradle build || return 0

COPY waltid-libraries/waltid-verifiable-credentials/. /work/waltid-libraries/waltid-verifiable-credentials
COPY waltid-libraries/waltid-crypto/. /work/waltid-libraries/waltid-crypto
COPY waltid-libraries/waltid-did/. /work/waltid-libraries/waltid-did
COPY waltid-libraries/waltid-openid4vc/. /work/waltid-libraries/waltid-openid4vc
COPY waltid-libraries/waltid-sdjwt/. /work/waltid-libraries/waltid-sdjwt
COPY waltid-libraries/waltid-mdoc-credentials/. /work/waltid-libraries/waltid-mdoc-credentials

COPY waltid-services/waltid-service-commons/. /work/waltid-services/waltid-service-commons
COPY waltid-services/waltid-issuer-api/. /work/waltid-services/waltid-issuer-api

RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

# Non-root user
RUN useradd --create-home waltid

COPY --from=buildstage /work/waltid-services/waltid-issuer-api/build/install/ /
#RUN chown -R waltid:waltid /waltid-issuer-api

WORKDIR /waltid-issuer-api

RUN mkdir /waltid-issuer-api/config && chown waltid:waltid /waltid-issuer-api/config
USER waltid

EXPOSE 7002
ENTRYPOINT ["/waltid-issuer-api/bin/waltid-issuer-api"]
