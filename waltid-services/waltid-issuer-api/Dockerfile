FROM docker.io/gradle:jdk21 AS buildstage

COPY gradle/ /work/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew /work/

COPY waltid-libraries/credentials/waltid-w3c-credentials/build.gradle.kts /work/waltid-libraries/credentials/waltid-w3c-credentials/
COPY waltid-libraries/credentials/waltid-verification-policies/build.gradle.kts /work/waltid-libraries/credentials/waltid-verification-policies/
COPY waltid-libraries/credentials/waltid-dif-definitions-parser/build.gradle.kts /work/waltid-libraries/credentials/waltid-dif-definitions-parser/
COPY waltid-libraries/crypto/waltid-crypto/build.gradle.kts /work/waltid-libraries/crypto/waltid-crypto/
COPY waltid-libraries/crypto/waltid-crypto-aws/build.gradle.kts /work/waltid-libraries/crypto/waltid-crypto-aws/
COPY waltid-libraries/waltid-did/build.gradle.kts /work/waltid-libraries/waltid-did/
COPY waltid-libraries/protocols/waltid-openid4vc/build.gradle.kts /work/waltid-libraries/protocols/waltid-openid4vc/
COPY waltid-libraries/sdjwt/waltid-sdjwt/build.gradle.kts /work/waltid-libraries/sdjwt/waltid-sdjwt/
COPY waltid-libraries/credentials/waltid-mdoc-credentials/build.gradle.kts /work/waltid-libraries/credentials/waltid-mdoc-credentials/
COPY waltid-libraries/waltid-library-commons/build.gradle.kts /work/waltid-libraries/waltid-library-commons/

COPY waltid-services/waltid-service-commons/build.gradle.kts /work/waltid-services/waltid-service-commons/
COPY waltid-services/waltid-issuer-api/build.gradle.kts /work/waltid-services/waltid-issuer-api/

WORKDIR /work/waltid-services/waltid-issuer-api/
RUN gradle build || return 0

COPY waltid-libraries/credentials/waltid-w3c-credentials/. /work/waltid-libraries/credentials/waltid-w3c-credentials
COPY waltid-libraries/credentials/waltid-verification-policies/. /work/waltid-libraries/credentials/waltid-verification-policies
COPY waltid-libraries/credentials/waltid-dif-definitions-parser/. /work/waltid-libraries/credentials/waltid-dif-definitions-parser
COPY waltid-libraries/crypto/waltid-crypto/. /work/waltid-libraries/crypto/waltid-crypto
COPY waltid-libraries/crypto/waltid-crypto-aws/. /work/waltid-libraries/crypto/waltid-crypto-aws
COPY waltid-libraries/waltid-did/. /work/waltid-libraries/waltid-did
COPY waltid-libraries/protocols/waltid-openid4vc/. /work/waltid-libraries/protocols/waltid-openid4vc
COPY waltid-libraries/sdjwt/waltid-sdjwt/. /work/waltid-libraries/sdjwt/waltid-sdjwt
COPY waltid-libraries/credentials/waltid-mdoc-credentials/. /work/waltid-libraries/credentials/waltid-mdoc-credentials
COPY waltid-libraries/waltid-library-commons/. /work/waltid-libraries/waltid-library-commons


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
