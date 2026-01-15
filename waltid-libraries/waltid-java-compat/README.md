<div align="center">
<h1>walt.id Java Compatibility</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Java compatibility helpers for using Kotlin Result types from Java code</p>

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

## What This Library Contains

`waltid-java-compat` is a small JVM-only helper library that provides Java-friendly wrappers for Kotlin types, primarily `JavaResult<T>` which wraps Kotlin's `Result<T>` to make it easier to use from Java code.

## Main Purpose

This library enables:

- **Java Interoperability**: Use Kotlin `Result<T>` types from Java code
- **Type Safety**: Maintain type safety when working with results from Java
- **Error Handling**: Simplified error handling patterns for Java developers

## Key Concepts

### JavaResult<T>

A wrapper around Kotlin's `Result<T>` that provides Java-friendly APIs:

- **Success/Failure Checking**: `isSuccess` and `isFailure` properties
- **Value Access**: `getOrNull()` to safely get the value
- **Exception Access**: `exceptionOrNull()` to get the exception if failed
- **Factory Methods**: `JavaResult.success(value)` and `JavaResult.failure(throwable)`

## Assumptions and Dependencies

### Platform Support

- **JVM Only**: This is a JVM-only library
- **Java 21+**: Requires Java 21 or later (JVM toolchain)

### Dependencies

- **Kotlin Standard Library**: Core Kotlin types

## Usage

### From Java

```java
import id.walt.JavaResult;

// Create a successful result
JavaResult<String> success = JavaResult.success("Hello, World!");

// Create a failed result
JavaResult<String> failure = JavaResult.failure(new RuntimeException("Error"));

// Check result
if (success.isSuccess()) {
    String value = success.getOrNull();
    // Use value
} else {
    Throwable error = success.exceptionOrNull();
    // Handle error
}
```

### From Kotlin

While you can use `JavaResult` from Kotlin, it's recommended to use Kotlin's native `Result<T>` type directly:

```kotlin
// Prefer Kotlin's Result
val result: Result<String> = runCatching { "value" }
result.getOrNull()

// JavaResult is mainly for Java interop
val javaResult = JavaResult(result)
```

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>

