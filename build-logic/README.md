# walt.id convention plugins

## What build logic convention plugin to choose for what?

**Choose ... - if ...**

### Module types

#### Backends
- `waltid.ktorbackend` - You have a Ktor backend
  - \+ `waltid.ktordocker` - The ktor backend shall provide a Docker container
- `waltid.backend` - You have a non-ktor backend
  - (unfinished - as does not appear to exist anywhere within our modules)

#### Libraries
- `waltid.jvm.library` - You have a JVM library
- `waltid.multiplatform.library` - You have a multiplatform library
- `waltid.multiplatform.library.jvm` - You have a multiplatform library, but for now it exclusively
  provides JVM sources

### Module traits
- `waltid.publish.maven` - The module shall be published to a Maven repository (no matter if JVM,
  Multiplatform, etc...; configured automagically)
- `waltid.publish.npm` - The module shall be published to a NPM repository
- `waltid.android` - You require Android functionality
