package id.walt.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles

class PrettyPrinter(val cmd: CliktCommand) {

    fun dim(m: String, linebreak: Boolean = true) {
        cmd.echo(TextStyles.dim(m), linebreak)
    }

    fun green(m: String, linebreak: Boolean = true) {
        cmd.echo(TextColors.green(m), linebreak)
    }

    fun greenb(m: String, linebreak: Boolean = true) {
        cmd.echo(TextColors.brightGreen(m), linebreak)
    }

    fun plain(m: String, linebreak: Boolean = true) {
        cmd.echo(m, linebreak)
    }

    fun box(m: String) {
        cmd.terminal.println(
            Markdown(
                """
                |```json
                |$m
                |```
            """.trimMargin()
            )
        )
    }
}
