package id.walt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.clikt.core.terminal
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
    } catch (e: InvalidFileFormat) {
        val ctx = cmd.currentContext
        val msg = e.formatMessage(cmd.currentContext.localization,
            object : ParameterFormatter {
                override fun formatOption(name: String): String {
                    return ctx.terminal.theme.style("info")(name)
                }

                override fun formatArgument(name: String): String {
                    return ctx.terminal.theme.style("info")("<${name.lowercase()}>")
                }

                override fun formatSubcommand(name: String): String {
                    return ctx.terminal.theme.style("info")(name)
                }
            })

        printErrorAndExit(cmd, InvalidFileFormat("", msg))
    } catch (e: CliktError) {
        printErrorAndExit(cmd, e)
    }
}

fun printErrorAndExit(cmd: CliktCommand, e: CliktError) {
    println("\n")
    cmd.terminal.println(
        Panel(
            content = Text(TextColors.brightRed(e.toString()), whitespace = Whitespace.NORMAL),
            title = Text(TextColors.red("ERROR"))
        )
    )
    println("\n")

    cmd.terminal.println(cmd.getFormattedHelp())
    exitProcess(e.statusCode)
}
