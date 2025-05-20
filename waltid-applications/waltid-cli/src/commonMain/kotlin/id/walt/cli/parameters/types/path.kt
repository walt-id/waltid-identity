package id.walt.cli.parameters.types

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.parameters.options.NullableOption
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parameters.options.convert
import convertToPath
import id.walt.cli.io.FileSystem
import id.walt.cli.io.FileSystems
import id.walt.cli.io.Path

///**
// * Convert the argument to a [Path].
// *
// * @param mustExist If true, fail if the given path does not exist
// * @param canBeFile If false, fail if the given path is a file
// * @param canBeDir If false, fail if the given path is a directory
// * @param mustBeWritable If true, fail if the given path is not writable
// * @param mustBeReadable If true, fail if the given path is not readable
// * @param fileSystem The [FileSystem] with which to resolve paths
// * @param canBeSymlink If false, fail if the given path is a symlink
// */
//fun RawArgument.path(
//    mustExist: Boolean = false,
//    canBeFile: Boolean = true,
//    canBeDir: Boolean = true,
//    mustBeWritable: Boolean = false,
//    mustBeReadable: Boolean = false,
//    canBeSymlink: Boolean = true,
//    fileSystem: FileSystem = FileSystems.getDefault(),
//): ProcessedArgument<Path, Path> {
//    return convert(completionCandidates = CompletionCandidates.Path) { str ->
//        convertToPath(
//            str,
//            mustExist,
//            canBeFile,
//            canBeDir,
//            mustBeWritable,
//            mustBeReadable,
//            canBeSymlink,
//            fileSystem,
//            context
//        ) { fail(it) }
//    }
//}

/**
 * Convert the option to a [Path].
 *
 * @param mustExist If true, fail if the given path does not exist
 * @param canBeFile If false, fail if the given path is a file
 * @param canBeDir If false, fail if the given path is a directory
 * @param mustBeWritable If true, fail if the given path is not writable
 * @param mustBeReadable If true, fail if the given path is not readable
 * @param fileSystem The [FileSystem] with which to resolve paths.
 * @param canBeSymlink If false, fail if the given path is a symlink
 */
fun RawOption.path(
    mustExist: Boolean = false,
    canBeFile: Boolean = true,
    canBeDir: Boolean = true,
    mustBeWritable: Boolean = false,
    mustBeReadable: Boolean = false,
    canBeSymlink: Boolean = true,
    fileSystem: FileSystem = FileSystems.getDefault(),
): NullableOption<Path, Path> {
    return convert({ localization.pathMetavar() }, CompletionCandidates.Path) { str ->
        convertToPath(
            str,
            mustExist,
            canBeFile,
            canBeDir,
            mustBeWritable,
            mustBeReadable,
            canBeSymlink,
            fileSystem,
            context
        ) { fail(it) }
    }
}
