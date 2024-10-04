package id.walt.crypto.keys.aws

import id.walt.crypto.keys.KeyType

interface AWSKeyCreator {


    suspend fun generate(metadata: AWSKeyMetadata, type: KeyType): AWSKey
}