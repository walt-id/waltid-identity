package id.walt.cli.util

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.widgets.Panel
import com.github.ajalt.mordant.widgets.Text

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

    fun red(m: String, linebreak: Boolean = true) {
        cmd.echo(TextColors.red(m), linebreak)
    }

    fun plain(m: String, linebreak: Boolean = true) {
        cmd.echo(m, linebreak)
    }

    fun italic(m: String, linebreak: Boolean = true) {
        cmd.echo(TextStyles.italic(m), linebreak)
    }


    fun panel(header: String, m: String) {
        cmd.terminal.println(
            Panel(
                content = Text(m, whitespace = Whitespace.NORMAL, overflowWrap = OverflowWrap.BREAK_WORD, width = 80),
                title = Text(header)
            )
        )
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
