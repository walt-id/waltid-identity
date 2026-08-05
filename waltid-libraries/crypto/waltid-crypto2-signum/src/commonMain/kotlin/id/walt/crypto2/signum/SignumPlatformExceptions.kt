package id.walt.crypto2.signum

/** The native platform key alias is not present in the platform key store. */
public class SignumKeyNotFoundException(
    public val alias: String,
    cause: Throwable? = null,
) : IllegalStateException("Signum key alias does not exist: $alias", cause)

/** The native platform key can no longer be used with its persisted policy. */
public class SignumKeyInvalidatedException(
    public val alias: String,
    cause: Throwable? = null,
) : IllegalStateException("Signum key alias is no longer valid: $alias", cause)

/** An interactive operation was requested without a usable platform interaction host. */
public class SignumInteractionContextUnavailableException(
    message: String = "A resumed interaction context is required for this Signum operation",
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
