package id.walt.oid4vc.interfaces

interface ISessionCache<T> {
    /**
     * Returns the session with the given [id] from the session cache, or `null` if it is not present in the cache.
     */
    fun getSession(id: String): T?

    /**
     * Puts the specified [session] with the specified [id] in the session cache.
     * @return the previous value associated with the [id], or `null` if the key was not present in the cache.
     */
    fun putSession(id: String, session: T): T?

    fun getSessionByIdTokenRequestState(idTokenRequestState: String): T?
    /**
     * Removes the specified session with the specified [id] from the session cache.
     * @return the previous value associated with the [id], or `null` if it was not present in the cache.
     */
    fun removeSession(id: String): T?
}
