package id.walt.core.crypto.keys

interface TSEKeyCreator {

    suspend fun generate(type: KeyType, metadata: TSEKeyMetadata): TSEKey

}
