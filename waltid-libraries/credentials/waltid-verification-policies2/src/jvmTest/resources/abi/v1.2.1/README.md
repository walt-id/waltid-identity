# Released JVM consumer fixture

`released-consumer.jar` is compiled from `ReleasedConsumer.kt` against the genuine published v1.2.1 JVM jars, not the implementation under test. The regression loads this jar unchanged with the current libraries and exercises the old suspend entry point, explicit constructors, default-argument constructors and the zero-argument resolver constructor.

Compile with Kotlin/JVM 2.4.21, target 17. Put these release jars before the remaining test runtime dependencies on the compiler classpath:

- https://maven.waltid.dev/releases/id/walt/did/waltid-did-jvm/1.2.1/waltid-did-jvm-1.2.1.jar
- https://maven.waltid.dev/releases/id/walt/credentials/waltid-credential-key-resolver-jvm/1.2.1/waltid-credential-key-resolver-jvm-1.2.1.jar

```sh
kotlinc -jvm-target 17 -classpath "$RELEASE_JARS:$TEST_RUNTIME_CLASSPATH" ReleasedConsumer.kt -d released-consumer.jar
```

Release jar SHA-256 checksums:

- did: `c947912b99b07d620e8abf204543769edc937f690e7b7356b77e1609fb51f9fa`
- resolver: `0d1b4782e910e039aae9a1ffafaa41ec987deb02afb3add630d2ffb7a62ce2e7`
