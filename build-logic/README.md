# walt.id convention plugins

## What build logic convention plugin to choose for what?

**Choose ... - if ...**

### Module types

#### Backends

- `waltid.ktorbackend` - You have a Ktor backend
    - \+ `waltid.ktordocker` - The ktor backend shall provide a Docker container
- `waltid.backend` - You have a non-ktor backend
    - (unfinished - as does not appear to exist anywhere within our modules)
- (`waltid.backend.base`)

#### Libraries

- `waltid.jvm.library` - You have a JVM library
- `waltid.jvm.servicelib` - You have a module that acts as a service library (library managing
  services), requires at minimum service JVM
- `waltid.jvm.library.base` - JVM library base
- `waltid.multiplatform.library` - You have a multiplatform library
- `waltid.multiplatform.library.jvm` - You have a multiplatform library, but for now it exclusively
  provides JVM sources
- (`waltid.multiplatform.library.common.gradle.kts`) - Common base for all multiplatform libraries
- (`waltid.jvm.library.base.gradle.kts`) - JVM library base
- (`waltid.base.library.gradle.kts`) - Library base
  

### Module traits

#### Publishing

- `waltid.publish.maven` - The module shall be published to a Maven repository (no matter if JVM,
  Multiplatform, etc...; configured automagically)
- `waltid.publish.npm` - The module shall be published to a NPM repository

#### Android

- `waltid.android.app.gradle.kts` - You are building an Android app
- `waltid.android.library.gradle.kts` - You are building an Android library
- (`waltid.android.base.gradle.kts`) - Android Base

#### Dependency analysis

- `waltid.licensereport.gradle.kts` - License report

#### Misc

- `waltid.base.gradle.kts` - Base for all types
- `waltid.licensereport.gradle.kts` - Use mocking
