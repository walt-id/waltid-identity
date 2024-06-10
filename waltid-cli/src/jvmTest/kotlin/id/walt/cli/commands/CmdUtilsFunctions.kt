package id.walt.cli.commands


fun getDIDFromDidCreateCmdOutput(output: String) : String {
    val outputLines = output.lines()
    return outputLines[outputLines.lastIndex-1]
}