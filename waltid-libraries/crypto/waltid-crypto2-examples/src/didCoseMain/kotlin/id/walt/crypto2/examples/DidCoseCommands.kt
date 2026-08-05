package id.walt.crypto2.examples

val didCoseExampleCommands = listOf(
    ExampleCommand("did", "Register did:key and did:jwk from a crypto2 key", execute = DidRegistrationExample::run),
    ExampleCommand("cose", "Create, encode, and verify COSE Sign1", execute = CoseSign1Example::run),
)
