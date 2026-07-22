package id.walt.crypto2.examples

typealias ExampleOutput = (String) -> Unit

class ExampleCommand(
    val name: String,
    val description: String,
    val includeInAll: Boolean = true,
    val execute: suspend (ExampleOutput) -> Unit,
)

val portableExampleCommands = listOf(
    ExampleCommand("software-sign", "Generate a P-256 software key, then sign and verify", execute = SoftwareSignExample::run),
    ExampleCommand("jws", "Create and verify a compact ES256 JWS", execute = CompactJwsExample::run),
    ExampleCommand("stored-key", "Persist a key with Json and explicitly restore it", execute = StoredKeyRestartExample::run),
    ExampleCommand("pem", "Export and import strict SPKI and PKCS8 PEM", execute = PemImportExportExample::run),
    ExampleCommand("rsa-oaep", "Encrypt and decrypt with RSA-OAEP-256", execute = RsaOaepExample::run),
    ExampleCommand("x25519", "Derive matching X25519 shared secrets", execute = X25519KeyAgreementExample::run),
)

suspend fun runExampleCommand(
    command: String,
    commands: List<ExampleCommand>,
    output: ExampleOutput = ::println,
) {
    require(commands.map(ExampleCommand::name).distinct().size == commands.size) { "Example command names must be unique" }
    when (command) {
        "list" -> printExampleList(commands, output)
        "all" -> commands.filter(ExampleCommand::includeInAll).forEach { runOneExample(it, output) }
        else -> runOneExample(
            command = commands.firstOrNull { it.name == command }
                ?: throw IllegalArgumentException("Unknown example '$command'. Run 'list' for supported examples."),
            output = output,
        )
    }
}

private suspend fun runOneExample(command: ExampleCommand, output: ExampleOutput) {
    command.execute(output)
    output("Completed: ${command.name}")
}

private fun printExampleList(commands: List<ExampleCommand>, output: ExampleOutput) {
    output("Supported examples on this target:")
    commands.forEach { output("  ${it.name} - ${it.description}") }
    output("  all - Run every self-contained supported example")
    if (commands.any { !it.includeInAll }) output("Configuration-dependent examples must be selected explicitly.")
}
