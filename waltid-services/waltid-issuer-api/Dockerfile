FROM docker.io/gradle:jdk21 AS buildstage

COPY . /work/.
WORKDIR /work/waltid-services/waltid-issuer-api/
RUN gradle build || return 0
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
