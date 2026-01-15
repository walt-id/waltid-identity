package id.walt.webdatafetching

data class DataFetchingException(override val message: String, override val cause: Throwable) : IllegalArgumentException(message, cause)
