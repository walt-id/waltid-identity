package id.walt.cli.util

import java.io.ByteArrayOutputStream
import java.io.PrintStream

actual fun tapSystemOut(block: () -> Unit): String {
    val stdoutCaptureStream = ByteArrayOutputStream()

    val originalOut = System.out
    System.setOut(PrintStream(stdoutCaptureStream))

    try {
        block.invoke()

        return stdoutCaptureStream.toString()
    } finally {
        System.setOut(originalOut)
    }
}