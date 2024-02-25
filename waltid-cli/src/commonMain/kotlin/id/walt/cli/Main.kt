package id.walt.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cmd = WaltIdCmd()
    try {
        cmd.parse(args)
    } catch (e: PrintHelpMessage) {
        cmd.echoFormattedHelp(e)
        exitProcess(e.statusCode)
    } catch (e: InvalidFileFormat) {
        printError(cmd, e)
        printUsage(cmd, e)
        exitProcess(e.statusCode)
    } catch (e: CliktError) {
        printError(cmd, e)
        printUsage(cmd, e)
        exitProcess(e.statusCode)
    }
}

fun printError(cmd: CliktCommand, e: CliktError? = null, msg: String? = null) {
    println("\n")
    val msgToPrint = msg ?: e?.let { it.localizedMessage }
    cmd.terminal.println(
        Panel(
            content = Text(TextColors.brightRed(msgToPrint!!), whitespace = Whitespace.NORMAL, width = 70),
            title = Text(TextColors.red("ERROR"))
        )
    )
    println("\n")
}

fun printUsage(cmd: CliktCommand, e: CliktError) {
    val ctx = (e as ContextCliktError).context
    cmd.echoFormattedHelp(PrintHelpMessage(ctx))
}
