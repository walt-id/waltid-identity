<div align="center">
<h1>walt.id Library Commons</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Internal multiplatform library for shared utilities and common code across walt.id libraries</p>

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

`waltid-library-commons` is an internal multiplatform library infrastructure for shared utilities and common code used across walt.id libraries. It provides a foundation for code that needs to be shared between multiple walt.id libraries.

## Main Purpose

This library serves as:

- **Shared Code Infrastructure**: Common utilities and helpers used across walt.id libraries
- **Multiplatform Foundation**: Kotlin Multiplatform setup for JVM, JavaScript, and iOS
- **Build Infrastructure**: Common build configuration and dependencies

## Assumptions and Dependencies

### Platform Support

- **JVM**: Full support
- **JavaScript**: Browser and Node.js support
- **iOS**: Support via Kotlin Multiplatform (requires `enableIosBuild=true`)

### Dependencies

- **Kotlin Multiplatform**: Core multiplatform framework
- **Kotlinx Coroutines**: For testing (test dependencies)
- **Suspend Transform Plugin**: For coroutine support across platforms

## Usage

This is primarily an internal library used by other walt.id libraries. If you're building a walt.id library and need to share code with other libraries, you can add this as a dependency.

```kotlin
dependencies {
    implementation(project(":waltid-libraries:waltid-library-commons"))
}
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

