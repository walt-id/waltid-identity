FROM docker.io/gradle:jdk17 as buildstage

COPY gradle/ /work/gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties gradlew /work/
COPY waltid-openid4vc/build.gradle.kts /work/waltid-openid4vc/
COPY waltid-sdjwt/build.gradle.kts /work/waltid-sdjwt/
COPY waltid-crypto/build.gradle.kts /work/waltid-crypto/
COPY waltid-crypto-oci/build.gradle.kts /work/waltid-crypto-oci/
COPY waltid-did/build.gradle.kts /work/waltid-did/
COPY waltid-mdoc-credentials/build.gradle.kts /work/waltid-mdoc-credentials/

WORKDIR /work/waltid-wallet-api/
RUN gradle build || return 0

COPY waltid-openid4vc/. /work/waltid-openid4vc
COPY waltid-sdjwt/. /work/waltid-sdjwt
COPY waltid-crypto/. /work/waltid-crypto
COPY waltid-crypto-oci/. /work/waltid-crypto-oci
COPY waltid-did/. /work/waltid-did
COPY waltid-mdoc-credentials/. /work/waltid-mdoc-credentials

COPY waltid-wallet-api/src/ /work/waltid-wallet-api/src
COPY waltid-wallet-api/build.gradle.kts waltid-wallet-api/gradle.properties waltid-wallet-api/gradlew /work/waltid-wallet-api/

RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

# Non-root user
RUN useradd --create-home waltid

COPY --from=buildstage /work/waltid-wallet-api/build/install/ /

WORKDIR /waltid-wallet-api

RUN mkdir /waltid-wallet-api/config && mkdir /waltid-wallet-api/data && chown waltid:waltid /waltid-wallet-api/config /waltid-wallet-api/data
USER waltid

EXPOSE 7001
ENTRYPOINT ["/waltid-wallet-api/bin/waltid-wallet-api"]
