FROM docker.io/gradle:jdk21 AS buildstage

COPY . /work/.
WORKDIR /work/waltid-services/waltid-wallet-api/
RUN gradle build || return 0
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
