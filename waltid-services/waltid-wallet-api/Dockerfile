FROM gradle:latest AS pre-cache
COPY . /home/src
COPY gradle.properties settings.gradle.kts /home/app/
COPY gradle /home/app/gradle
RUN cd /home/src && \
    find . -name "build.gradle.kts" -exec sh -c 'mkdir -p /home/app/$(dirname "$1"); cp "$1" /home/app/$1' _ {} \;

# Stage 1: Cache Gradle dependencies
FROM gradle:latest AS cache
RUN mkdir -p /home/gradle/cache_home
ENV GRADLE_USER_HOME=/home/gradle/cache_home
COPY --from=pre-cache /home/app/ /home/gradle/app/
WORKDIR /home/gradle/app/waltid-services/waltid-wallet-api
RUN gradle dependencies --no-daemon

# Stage 2: Build Application
FROM gradle:latest AS build
COPY --from=cache /home/gradle/cache_home /home/gradle/.gradle
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src/waltid-services/waltid-wallet-api
# Build the fat JAR, Gradle also supports shadow
# and boot JAR by default.
RUN gradle buildFatJar --no-daemon

# Stage 3: Create the Runtime Image
FROM amazoncorretto:25.0.1 AS runtime
WORKDIR /app

# Create an unprivileged user and group
RUN groupadd -r app && useradd -r -g app -u 10001 app

COPY --chown=app:app --from=build /home/gradle/src/waltid-services/waltid-wallet-api/build/libs/*.jar /app/wallet-api.jar

USER app
EXPOSE 7001
ENTRYPOINT ["java","-jar","/app/wallet-api.jar"]
