package id.walt.statuslist.compression

/** Expansion stopped before allocating more than the configured output budget. */
class StatusListSizeLimitException : IllegalArgumentException("Status list exceeds the expanded size limit")
