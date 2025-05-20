package id.walt.cli.net

expect class URLEncoder {
    companion object {
        fun encode(url: String, enc: String): String
    }
}
