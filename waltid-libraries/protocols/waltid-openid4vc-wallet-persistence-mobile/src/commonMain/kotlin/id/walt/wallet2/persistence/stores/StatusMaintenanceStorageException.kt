package id.walt.wallet2.persistence.stores

/** SQLite rejected a restricted maintenance read or write. No wallet or SQL text is retained. */
public class StatusMaintenanceStorageException internal constructor(code: Int) :
    RuntimeException("Wallet maintenance storage unavailable (SQLite $code)")
