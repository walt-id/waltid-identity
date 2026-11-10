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

#### ABI validation with optional iOS targets

`waltid.optional-ios-abi` keeps a single complete ABI baseline in `api/` for modules whose iOS
targets depend on `enableIosBuild`. With iOS disabled, it prepares a temporary reference without
the iOS targets; all other declarations remain checked by Kotlin's ABI validator. With iOS enabled,
validation uses the complete baseline. Run `updateKotlinAbi -PenableIosBuild=true` on macOS to update
these baselines; updates with iOS disabled are rejected to preserve the iOS declarations.

#### Publishing

- `waltid.publish.maven` - The module shall be published to a Maven repository (no matter if JVM,
  Multiplatform, etc...; configured automagically)
- `waltid.publish.npm` - The module shall be published to a NPM repository

#### Android

- `waltid.mobile.library` - Android/iOS libraries
- `waltid.full.library` - Libraries targeting JVM, Android, JavaScript and iOS

These conventions enable their Android targets through `enableAndroidBuild`. Android apps configure `com.android.application` directly; see the [Compose demo host](../waltid-applications/waltid-wallet-demo-compose/androidApp/build.gradle.kts).

#### Dependency analysis

- `waltid.licensereport.gradle.kts` - License report

#### Misc

- `waltid.base.gradle.kts` - Base for all types
- `waltid.licensereport.gradle.kts` - Use mocking

## Kotlin compiler warnings

The Identity and Unified Build roots apply `waltid.kotlin-warnings` once for all projects.
The convention makes missing experimental opt-ins errors in production and test
compilations, including enabled JVM, JS, Android, and native targets. A module that
applies Kotlin directly receives the same policy as a convention-based module,
without declaring another plugin. Keep explicit plugin versions in the root's
`plugins` block when those plugins are also dependencies of build logic.

Use a local `@OptIn` when an implementation intentionally uses an experimental API.
Applying the experimental marker itself makes the declaration experimental for its
callers. Avoid module-wide opt-ins: they also hide accidental experimental API use
in consumer tests such as `ClientMetadataTest`.

Once a module's remaining warnings are resolved, enable the stricter policy in its
build script:

```kotlin
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}
```

This also rejects new deprecated API usages. Migrate legacy APIs deliberately;
compatibility tests and adapters may need to keep exercising deprecated entry
points. Any necessary suppression should be local and explain the compatibility
contract it preserves.

Compiler options are described in the [Kotlin warning documentation](https://kotlinlang.org/docs/compiler-reference.html#warning-management).
