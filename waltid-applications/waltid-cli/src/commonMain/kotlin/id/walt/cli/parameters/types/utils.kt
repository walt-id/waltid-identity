import com.github.ajalt.clikt.core.Context
import id.walt.cli.io.FileSystem
import id.walt.cli.io.Files
import id.walt.cli.io.Path

internal fun convertToPath(
    path: String,
    mustExist: Boolean,
    canBeFile: Boolean,
    canBeFolder: Boolean,
    mustBeWritable: Boolean,
    mustBeReadable: Boolean,
    canBeSymlink: Boolean,
    fileSystem: FileSystem,
    context: Context,
    fail: (String) -> Unit,
): Path {
    val name = pathType(context, canBeFile, canBeFolder)
    return with(context.localization) {
        fileSystem.getPath(path).also {
            if (mustExist && !Files.exists(it)) fail(pathDoesNotExist(name, it.toString()))
            if (!canBeFile && Files.isRegularFile(it)) fail(pathIsFile(name, it.toString()))
            if (!canBeFolder && Files.isDirectory(it)) fail(pathIsDirectory(name, it.toString()))
            if (mustBeWritable && !Files.isWritable(it)) fail(
                pathIsNotWritable(
                    name,
                    it.toString()
                )
            )
            if (mustBeReadable && !Files.isReadable(it)) fail(
                pathIsNotReadable(
                    name,
                    it.toString()
                )
            )
            if (!canBeSymlink && Files.isSymbolicLink(it)) fail(pathIsSymlink(name, it.toString()))
        }
    }
}

private fun pathType(context: Context, fileOkay: Boolean, folderOkay: Boolean): String = when {
    fileOkay && !folderOkay -> context.localization.pathTypeFile()
    !fileOkay && folderOkay -> context.localization.pathTypeDirectory()
    else -> context.localization.pathTypeOther()
}

