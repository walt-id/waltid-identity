package id.walt.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.output.ParameterFormatter
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
    } catch (e: MultiUsageError) {
        var msgs = "Invalid command. Please, review the usage instructions bellow and try again."
        // for (error in e.errors) {
        //     if (msgs.length == 0) {
        //         // msgs = error.formatMessage(error.context!!.localization, parameterFormatter(error.context!!))
        //         msgs = "${error.localizedMessage} - ${error.message} "
        //     } else {
        //         msgs = """${msgs} ${error.toString() ?: ""}"""
        //     }
        // }
        printError(cmd, e, msgs)
        printUsage(cmd, e)

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

fun parameterFormatter(context: Context): ParameterFormatter {
    return object : ParameterFormatter {
        override fun formatOption(name: String): String {
            // return styleOptionName(name)
            return context.theme.style("info")(name)
        }

        override fun formatArgument(name: String): String {
            // return styleArgumentName(normalizeParameter(name))
            return context.theme.style("info")("<${name.lowercase()}>")
        }

        override fun formatSubcommand(name: String): String {
            // return styleSubcommandName(name)
            return context.theme.style("info")(name)
        }

    }
}
