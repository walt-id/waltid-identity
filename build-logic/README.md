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

- `waltid.android.app.gradle.kts` - You are building an Android app
- `waltid.android.library.gradle.kts` - You are building an Android library
- (`waltid.android.base.gradle.kts`) - Android Base

#### Dependency analysis

- `waltid.licensereport.gradle.kts` - Generates NOTICE / THIRD-PARTY-NOTICE attribution reports (opt-in for identity via `-PenableLicenseReport=true`; always on for enterprise-api). Does not fail the build on disallowed licenses.
- `waltid.licensee.gradle.kts` - Licensee policy enforcement (`app.cash.licensee`). Attached to `check`. Run `./gradlew licensee` to execute every module's check. Product policy is selected by project path, or overridden with `-Pwaltid.licensee.policy=apache|binary|saas`:
  - `apache` — identity libraries and OSS services (Apache-2.0 product). Permissive licenses plus weak copyleft (EPL, CDDL, MPL, GPL+CE).
  - `binary` — shipped Enterprise binaries. Apache set plus LGPL.
  - `saas` — license server. Binary set plus GPL. AGPL is never allowed.

#### Misc

- `waltid.base.gradle.kts` - Base for all types
- `waltid.mokkery.gradle.kts` - Use mocking
