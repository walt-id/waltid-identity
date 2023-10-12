package id.walt.crypto.keys

interface TSEKeyCreator {

    suspend fun generate(type: KeyType, metadata: TSEKeyMetadata): TSEKey

}
