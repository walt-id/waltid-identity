FROM docker.io/gradle:jdk21 AS buildstage

COPY . /work/.
WORKDIR /work/waltid-services/waltid-verifier-api/
RUN gradle build || return 0
RUN gradle clean installDist

FROM docker.io/eclipse-temurin:17

# Non-root user
RUN useradd --create-home waltid

COPY --from=buildstage /work/waltid-services/waltid-verifier-api/build/install/ /

WORKDIR /waltid-verifier-api

RUN mkdir /waltid-verifier-api/config && chown waltid:waltid /waltid-verifier-api/config
USER waltid

EXPOSE 7003
ENTRYPOINT ["/waltid-verifier-api/bin/waltid-verifier-api"]
