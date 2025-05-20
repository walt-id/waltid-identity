package id.walt.cli.util

import platform.Foundation.NSBundle

actual fun getResourcePath(obj: Any, filename: String): String {
    val bundle = NSBundle.mainBundle
    val resourcePath = bundle.pathForResource(name = filename, ofType = null)
        ?: throw Error("$filename not found in resources")

    return resourcePath
}


//    // If Windows path, escape backslash
//    val winPathRegex = """"^[a-zA-Z]:\\\\.*"""".toRegex()
//    if (winPathRegex.matches(path!!)) {
//        return path.replace("\\", "\\\\")
//    }
//
//    if (Paths.get(path).exists()) {
//        return path
//    } else {
//        throw FileNotFoundException(path)
//    }

