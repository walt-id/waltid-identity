<div align="center">
 <h1>Walt.id Service Commons</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
    <p>Common configurations and codes shared between walt.id services</p>
<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

# What it provides

- Feature system for all services
- Advanced logging system
- Service initialization & phase management
- Debug endpoints & health checks


# How to use it

Add the waltid-service-commons library as a dependency to your Kotlin or Java project.

### walt.id Repository

Add the Maven repository which hosts the walt.id libraries to your build.gradle file.

```kotlin
repositories {
    maven { url = uri("https://maven.waltid.dev/releases") }
} 
```

### Library Dependency

Adding the waltid-service-commons library as dependency. Specify the version that coincides with the latest or required
snapshot for your project. [Latest releases](https://github.com/walt-id/waltid-identity/releases).

```kotlin
dependencies {
  implementation("id.walt:waltid-service-commons:<version>")
}
```


# Feature system

Many users have encountered various issues with the old configuration system, including:

- Missing configuration files (especially in Docker Compose), causing service disruptions.
- The need to configure unused features.
- High mental complexity due to a large number of configurations.
- Misconfigurations affecting production services, often unnoticed due to lazy-loading.


To address these challenges, we have introduced a new **feature system**. This system operates on several key principles and concepts:

1. **Applicability**: The feature system is relevant to "Services" (runnable REST APIs) as part of the waltid-service-commons.
2. [**Service Initialization**](https://github.com/walt-id/waltid-identity/blob/main/waltid-services/waltid-service-commons/src/main/kotlin/id/walt/commons/ServiceCommons.kt): Every service is initialized using the commons service initializer.
3. [**Feature Catalogs**](https://github.com/walt-id/waltid-identity/blob/main/waltid-services/waltid-service-commons/src/main/kotlin/id/walt/commons/featureflag/CommonsFeatureCatalog.kt): The initializer is provided with one or multiple Feature Catalogs.
4. **Base and Optional Features**:
    - [**Base Features**](https://github.com/walt-id/waltid-identity/blob/main/waltid-services/waltid-service-commons/src/main/kotlin/id/walt/commons/featureflag/BaseFeature.kt): Generally always enabled, except in niche use cases.
    - [**Optional Features**](https://github.com/walt-id/waltid-identity/blob/main/waltid-services/waltid-service-commons/src/main/kotlin/id/walt/commons/featureflag/OptionalFeature.kt): Can be enabled or disabled. They are categorized as:
        - **Enabled by Default**: Features that require no configuration or have a useful default configuration (e.g., health checks).
        - **Disabled by Default**: Features that require configuration to function (e.g., Entra or OCI integration, which need access credentials, API URLs, etc.).

### Benefits of the Feature System

The refactoring introducing the feature system resolves the aforementioned issues by implementing the following improvements:

- **Explicit Feature Management**: The service initializer determines which features are explicitly enabled, explicitly disabled, and which default to enabled or disabled. This ensures that:
    - Only the configurations for enabled features (base or optional) are loaded.
    - Unused features do not require configuration, reducing unnecessary setup (e.g., no need for Entra or OCI configurations if not used).
    - Configurations, especially for base features and optionally enabled features, come with defaults to minimize initial setup effort.
    - By parsing all required configurations during the initialization steps, invalid configurations are detected early, preventing issues in production due to lazy-loading.

### Advantages for Developers

- **Reduced Configuration Overhead**: With fewer configurations needed, the mental complexity is significantly reduced, making it easier to manage and understand.
- **Streamlined Setup**: Only essential configurations are required, allowing developers to focus on what’s necessary.
- **Improved Error Detection**: Early detection of configuration errors ensures more robust and reliable services in production.

This new module aims to streamline the configuration process, enhance usability, and ensure more reliable service deployments.



# Advanced Service Logging

## Key Features and Improvements

### Multiplatform Logging

- **Multiplatform Libraries**: These libraries utilize the existing multiplatform logging library, which targets the platform's underlying common logging system:
    - `android.util.Log` on Android
    - Darwin log system on macOS/iOS
    - SLF4J on Java/JVM
    - Console logger on JavaScript

### Advanced Logging for Services

The services now benefit from a more advanced logging system based on the service commons module. Key features include:

1. [**Dynamic Reconfiguration**](https://github.com/walt-id/waltid-identity/blob/main/waltid-services/waltid-service-commons/src/main/kotlin/id/walt/commons/logging/LoggingManager.kt): Allows for real-time changes to logging settings.
2. **Multiple Log Outputs**: Supports pushing logs to various log aggregator systems, including:
    - stdout/stderr
    - Seq log aggregator
    - Graylog log aggregator
    - Logstash (part of the ELK stack)
    - Splunk

3. [**Multiple Log Output Formats**](https://github.com/walt-id/waltid-identity/blob/main/waltid-services/waltid-service-commons/src/main/kotlin/id/walt/commons/logging/LogStrings.kt): Supports various formats for log output, including:
    - CLEF compact JSON format (e.g. Serilog) (https://clef-json.org/)
    - Elastic Common Schema (ECS) (can be sent directly to ELK stack) (https://www.elastic.co/guide/en/ecs/current/index.html)
    - ECS for .NET (https://github.com/elastic/ecs-dotnet/blob/main/examples/aspnetcore-with-serilog/)
    - GELF JSON format (for e.g. Graylog)
    - JSON format for Splunk HTTP Event Collector
    - Simple & ISO 8601 & ANSI rendering
    - "Standard" JSON format (e.g. Datadog, New Relic, etc.)

4. **Custom Logging Configurations**: Users can provide custom logging configurations without needing to recompile the module.
5. **Structured and Context Logging**:
    - **Structured Logging**: Uses structured parcels of data instead of primitive message strings.
    - **Context Logging**: Similar to Mapped Diagnostic Context (MDC), allowing for more detailed and contextual log entries.

### Configuration Options

Configuration of the logging system can be done in two ways:

1. **Flexible Configuration File**: Users can provide a complex logging configuration file in HOCON or JSON format.
2. **Pre-defined Logging Setups**: Users can choose from simplified pre-defined logging setups curated by walt.id by setting a command line argument.

If no user-provided logging configuration is detected, a sensible default logging setup is used.

## Advantages for Developers

- **Enhanced Logging Capabilities**: The advanced logging system provides more flexibility and control over log management.
- **Improved Debugging and Monitoring**: With multiple output formats and log aggregators, developers can better monitor and debug services.
- **User-friendly Configuration**: The ability to choose between complex configurations or simplified setups allows for ease of use without sacrificing control.

This new logging concept aims to provide robust and flexible logging solutions, enhancing the overall service reliability and developer experience.



# Debug Endpoints & Health Checks

## Overview

The service-commons module now provides all services with a unified set of debugging and healthcheck endpoints. These endpoints enhance the ability to monitor and debug running instances, ensuring improved reliability and performance.

## Key Features and Improvements

### [Debugging Endpoints](https://github.com/walt-id/waltid-identity/blob/main/waltid-services/waltid-service-commons/src/main/kotlin/id/walt/commons/web/modules/ServiceHealthchecksDebugModule.kt)

The debugging endpoints offer a standardized way to retrieve crucial information from a running instance. This feature is particularly useful for debugging on customer systems. Key capabilities include:

- **System Properties**: Access various system properties.
- **JVM Arguments**: Retrieve Java Virtual Machine (JVM) arguments.
- **Version Information**: Obtain version information of the running service.
- **OS Details**: Get the operating system name and version.
- **Thread & Heap Dumps**: Create thread and heap dumps in the standard JVM format.

### Health Checks

The health checks provided by service-commons are compatible with Kubernetes and support various types of checks to ensure the service's proper functioning:

1. **Liveness Checks**: Ensure the service is running and not stuck.
2. **Readiness Checks**: Confirm the service is ready to handle requests.
3. **Startup Checks**: Verify the service has started correctly.

Additional health checks include:

- **HTTP Server Checks**: Monitor the health of the HTTP server.
- **Database Checks**: Ensure database connections and operations are functioning correctly.

## Advantages for Developers

- **Unified Debugging Tools**: Standardized debugging endpoints make it easier to retrieve essential information for troubleshooting.
- **Enhanced Monitoring**: Comprehensive health checks provide a robust mechanism to monitor service health and readiness.
- **Kubernetes Compatibility**: Seamless integration with Kubernetes health check mechanisms ensures smoother deployments and operations.

These enhancements aim to improve the overall reliability, maintainability, and performance of services by providing powerful debugging tools and comprehensive health checks.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>




