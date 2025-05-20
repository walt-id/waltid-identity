package id.walt.cli.util

expect fun getResourcePath(obj: Any, filename: String): String

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

