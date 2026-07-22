# waltid-crypto2 examples

Presentation-ready crypto2 examples with shared portable logic and target-specific command registries. The module is runnable but is intentionally not published. It contains no external KMS credentials, embedded secrets, or crypto1 imports.

Run all Gradle commands from the `waltid-unified-build` root.

## Commands

Portable commands:

- `software-sign` - generate a P-256 software key, sign, and verify
- `jws` - create and verify a compact ES256 JWS
- `stored-key` - serialize a `SoftwareKey` with `Json`, print its private record with a warning, decode a non-operational handle, explicitly restore, sign, and verify
- `pem` - strictly export and import SPKI public and PKCS8 private PEM
- `rsa-oaep` - encrypt and decrypt with RSA-OAEP-256
- `x25519` - derive matching X25519 shared secrets

Target-specific commands:

- `did` and `cose` - JVM only
- `es256k` - JVM with Bouncy Castle, Linux x64 and Windows x64 with OpenSSL 3
- `pkcs11-softhsm` - JVM only, using an isolated SoftHSM token
- `list` - list commands supported by that launcher
- `all` - run every self-contained command supported by that launcher; configuration-dependent PKCS11 is explicit-only

## JVM

```shell
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runJvm --args="list"
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runJvm --args="jws"
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runJvm --args="all"
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:jvmTest
```

## PKCS11 SoftHSM

Install SoftHSM 2, then run one command:

```shell
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runSoftHsmExample
```

The task finds `softhsm2-util` and `libsofthsm2`, creates an isolated token under this module's `build/softhsm-example` directory, initializes it once, supplies the example environment internally, and runs the complete lifecycle. Re-running the task reuses the disposable token and creates a fresh key alias.

**Demo PIN warning:** the task uses user PIN `123456` and security-officer PIN `12345678`. These are intentionally weak disposable demo values. Never use them for production tokens.

Install locations detected automatically:

Linux (Debian/Ubuntu):

```shell
sudo apt-get install softhsm2
```

Common Linux libraries are `/usr/lib/softhsm/libsofthsm2.so`, `/usr/lib/pkcs11/libsofthsm2.so`, and `/usr/lib64/pkcs11/libsofthsm2.so`.

macOS with Homebrew:

```shell
brew install softhsm
```

Homebrew libraries are detected under `/opt/homebrew/lib/softhsm` and `/usr/local/lib/softhsm`, with `.so` and `.dylib` variants.

Expected safety and lifecycle output:

```text
Serialization safety: pinReferencePresent=true, pinValuePresent=false
Initial sign/verify: signatureEncoding=P1363, verified=true
New runtime restore + sign/verify: ... verified=true
Delete token key: deleted=true
Restore after deletion: rejected=true
```

### Advanced custom token

For an already initialized token, manual runs normally need only the SoftHSM configuration and user PIN:

```shell
export SOFTHSM2_CONF="/path/to/softhsm2.conf"
export WALTID_SOFTHSM2_PIN="your-token-pin"
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runJvm --args="pkcs11-softhsm"
```

Optional advanced overrides:

- `WALTID_SOFTHSM2_LIBRARY` - nonstandard PKCS11 library path; common Linux and Homebrew paths are auto-detected
- `WALTID_SOFTHSM2_SLOT_INDEX` - slot-list index, default `0`
- `WALTID_SOFTHSM2_PIN_REFERENCE` - persisted secret reference label, default `env:WALTID_SOFTHSM2_PIN`; never place the PIN itself here
- `WALTID_SOFTHSM2_PIN` - required for manual/custom runs; the zero-configuration task supplies its disposable demo PIN internally

## JavaScript

The Gradle Node and browser launchers run all portable examples. Build the Node executable first to select an individual command directly.

```shell
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:jsNodeDevelopmentRun
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:jsDevelopmentExecutableCompileSync
node "waltid-identity/waltid-libraries/crypto/waltid-crypto2-examples/build/compileSync/js/main/developmentExecutable/kotlin/waltid-crypto2-examples-node.js" list
node "waltid-identity/waltid-libraries/crypto/waltid-crypto2-examples/build/compileSync/js/main/developmentExecutable/kotlin/waltid-crypto2-examples-node.js" jws
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:jsNodeTest
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:jsBrowserTest
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:jsBrowserDevelopmentRun
```

`jsBrowserDevelopmentRun` starts a development server and writes narration to the browser console.

## WASM

WASM uses the real cryptography-kotlin WebCrypto provider. Node and browser launchers run all portable examples.

```shell
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:wasmJsNodeDevelopmentRun
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:wasmJsNodeTest
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:wasmJsBrowserTest
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:wasmJsBrowserDevelopmentRun
```

`wasmJsBrowserDevelopmentRun` starts a development server and writes the same narrated examples to the browser console. Browser WebCrypto requires a secure context; localhost development servers qualify.

## Linux x64

The Gradle launcher runs all commands. The linked executable accepts `list`, an individual command, or `all`.

```shell
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runDebugExecutableLinuxX64
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:linkDebugExecutableLinuxX64
./waltid-identity/waltid-libraries/crypto/waltid-crypto2-examples/build/bin/linuxX64/debugExecutable/waltid-crypto2-examples.kexe jws
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:linuxX64Test
```

## Windows x64

Run these commands on Windows. Cross-platform compilation can be checked from any supported Kotlin/Native host.

```shell
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runDebugExecutableMingwX64
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:mingwX64Test
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:compileKotlinMingwX64
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:compileTestKotlinMingwX64
```

## macOS

Use the command matching the host architecture. macOS exposes portable examples only because the Apple providers do not opt in to secp256k1.

```shell
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runDebugExecutableMacosX64
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:macosX64Test
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:runDebugExecutableMacosArm64
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:macosArm64Test
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:compileKotlinMacosX64
./gradlew :waltid-libraries:crypto:waltid-crypto2-examples:compileKotlinMacosArm64
```

## Output

Each example uses the same presentation shape:

```text
=== Example title ===
1. Create provider=... and generate key spec/usages
2. Perform the cryptographic operation
   Safe result metadata: byte counts or public identifiers
3. Verification result: verified=true
Completed: command-name
```

The `stored-key` command is the sole exception to safe result-only output. It intentionally prints the complete
serialized private `StoredKey` JSON so its exact persistence shape is visible, immediately preceded by this warning:

```text
WARNING: The next line contains private key material. Never emit it to application logs.
```

Run that example only with disposable demonstration keys. Its JSON must not be copied into application logs. All other
examples avoid printing private JWK, PKCS8, PEM, shared-secret, and decrypted key material.

## Support matrix

| Target | Portable commands | DID/COSE | ES256K | PKCS11 SoftHSM | Provider | Verification in this workspace |
| --- | --- | --- | --- | --- | --- | --- |
| JVM | Yes | Yes | Yes (Bouncy Castle) | Yes | JDK optimal + Bouncy Castle | Executed |
| JavaScript Node | Yes | No | No | No | WebCrypto | Executed |
| JavaScript browser | Yes | No | No | No | WebCrypto | Chrome test executed |
| WASM Node | Yes | No | No | No | WebCrypto WASM | Executed |
| WASM browser | Yes | No | No | No | WebCrypto WASM | Chrome test executed |
| Linux x64 | Yes | No | Yes (OpenSSL 3) | No | OpenSSL 3 prebuilt | Executed |
| Windows x64 | Yes | No | Yes (OpenSSL 3) | No | OpenSSL 3 prebuilt | Cross-compiled |
| macOS x64/arm64 | Yes | No | No | No | Apple optimal provider | Cross-compiled |

## Limitations

- Browser WebCrypto, including WASM, requires a secure context outside local development. Localhost development servers qualify. Node requires a current runtime with WebCrypto and X25519 support.
- The current DID and COSE JS dependency graph imports Node's `crypto` module through crypto1. Those commands remain JVM-only so the single supported JS target works in both Node and browsers.
- WASM browser tests use `CHROME_BIN` when set, otherwise they discover a local Chrome or Chromium executable. They are disabled when no supported browser is installed.
- Windows and macOS were cross-compiled on Linux; their run and test tasks must execute on matching hosts.
- Strict PEM accepts one unencrypted SPKI `PUBLIC KEY` or PKCS8 `PRIVATE KEY` document with canonical base64.
- RSA-2048 OAEP with SHA-256 accepts at most 190 plaintext bytes.
- `Json.encodeToString(softwareKey)` writes the versioned `StoredKey` shape and can contain private material. Protect it at rest. The `stored-key` command intentionally reveals a disposable private record for demonstration and must not be used as a production logging pattern.
