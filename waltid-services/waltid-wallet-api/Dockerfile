FROM docker.io/gradle:jdk21 AS buildstage

COPY gradle/ /work/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew /work/

COPY waltid-libraries/protocols/waltid-openid4vc/build.gradle.kts /work/waltid-libraries/protocols/waltid-openid4vc/
COPY waltid-libraries/sdjwt/waltid-sdjwt/build.gradle.kts /work/waltid-libraries/sdjwt/waltid-sdjwt/
COPY waltid-libraries/crypto/waltid-crypto/build.gradle.kts /work/waltid-libraries/crypto/waltid-crypto/
COPY waltid-libraries/crypto/waltid-crypto-oci/build.gradle.kts /work/waltid-libraries/crypto/waltid-crypto-oci/
COPY waltid-libraries/crypto/waltid-crypto-aws/build.gradle.kts /work/waltid-libraries/crypto/waltid-crypto-aws/
COPY waltid-libraries/waltid-did/build.gradle.kts /work/waltid-libraries/waltid-did/
COPY waltid-libraries/credentials/waltid-mdoc-credentials/build.gradle.kts /work/waltid-libraries/credentials/waltid-mdoc-credentials/
COPY waltid-libraries/credentials/waltid-w3c-credentials/build.gradle.kts /work/waltid-libraries/credentials/waltid-w3c-credentials/
COPY waltid-libraries/credentials/waltid-verification-policies/build.gradle.kts /work/waltid-libraries/credentials/waltid-verification-policies/
COPY waltid-libraries/credentials/waltid-dif-definitions-parser/build.gradle.kts /work/waltid-libraries/credentials/waltid-dif-definitions-parser/
COPY waltid-libraries/auth/waltid-ktor-authnz/build.gradle.kts /work/waltid-libraries/auth/waltid-ktor-authnz/
COPY waltid-libraries/auth/waltid-permissions/build.gradle.kts /work/waltid-libraries/auth/waltid-permissions/
COPY waltid-libraries/waltid-library-commons/build.gradle.kts /work/waltid-libraries/waltid-library-commons/
COPY waltid-libraries/waltid-core-wallet/build.gradle.kts /work/waltid-libraries/waltid-core-wallet/

COPY waltid-services/waltid-service-commons/build.gradle.kts /work/waltid-services/waltid-service-commons/
COPY waltid-services/waltid-wallet-api/build.gradle.kts /work/waltid-services/waltid-wallet-api/

WORKDIR /work/waltid-services/waltid-wallet-api/
RUN gradle build || return 0

COPY waltid-libraries/protocols/waltid-openid4vc/. /work/waltid-libraries/protocols/waltid-openid4vc
COPY waltid-libraries/sdjwt/waltid-sdjwt/. /work/waltid-libraries/sdjwt/waltid-sdjwt
COPY waltid-libraries/crypto/waltid-crypto/. /work/waltid-libraries/crypto/waltid-crypto
COPY waltid-libraries/crypto/waltid-crypto-oci/. /work/waltid-libraries/crypto/waltid-crypto-oci
COPY waltid-libraries/crypto/waltid-crypto-aws/. /work/waltid-libraries/crypto/waltid-crypto-aws
COPY waltid-libraries/waltid-did/. /work/waltid-libraries/waltid-did
COPY waltid-libraries/credentials/waltid-mdoc-credentials/. /work/waltid-libraries/credentials/waltid-mdoc-credentials
COPY waltid-libraries/credentials/waltid-w3c-credentials/. /work/waltid-libraries/credentials/waltid-w3c-credentials
COPY waltid-libraries/credentials/waltid-verification-policies/. /work/waltid-libraries/credentials/waltid-verification-policies
COPY waltid-libraries/credentials/waltid-dif-definitions-parser/. /work/waltid-libraries/credentials/waltid-dif-definitions-parser
COPY waltid-libraries/auth/waltid-ktor-authnz/. /work/waltid-libraries/auth/waltid-ktor-authnz
COPY waltid-libraries/auth/waltid-permissions/. /work/waltid-libraries/auth/waltid-permissions
COPY waltid-libraries/waltid-library-commons/. /work/waltid-libraries/waltid-library-commons
COPY waltid-libraries/waltid-core-wallet/. /work/waltid-libraries/waltid-core-wallet

COPY waltid-services/waltid-service-commons/. /work/waltid-services/waltid-service-commons
COPY waltid-services/waltid-wallet-api/src/ /work/waltid-services/waltid-wallet-api/src
COPY waltid-services/waltid-wallet-api/build.gradle.kts waltid-services/waltid-wallet-api/gradlew /work/waltid-services/waltid-wallet-api/

RUN gradle clean installDist

FROM docker.io/eclipse-temurin:21

# Non-root user
RUN useradd --create-home waltid

COPY --from=buildstage /work/waltid-services/waltid-wallet-api/build/install/ /

WORKDIR /waltid-wallet-api

RUN mkdir /waltid-wallet-api/config && mkdir /waltid-wallet-api/data && chown waltid:waltid /waltid-wallet-api/config /waltid-wallet-api/data
USER waltid

EXPOSE 7001
ENTRYPOINT ["/waltid-wallet-api/bin/waltid-wallet-api"]
