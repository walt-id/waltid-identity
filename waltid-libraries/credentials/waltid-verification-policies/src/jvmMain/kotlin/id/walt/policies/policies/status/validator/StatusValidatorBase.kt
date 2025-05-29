package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.CredentialStatusPolicyArguments
import id.walt.policies.policies.status.reader.StatusValueReader
import io.github.oshai.kotlinlogging.KotlinLogging

abstract class StatusValidatorBase<K, T : CredentialStatusPolicyArguments>(
    private val fetcher: CredentialFetcher,
    private val reader: StatusValueReader<K>,
) : StatusValidator<K, T> {
    protected val logger = KotlinLogging.logger {}

//    protected suspend fun processStatus(url: String, index: ULong): Result<K> {
//        // process status
//        logger.debug { "Status list index: $index" }
//        logger.debug { "Credential URL: $url" }
//        // download status credential
//        val response = fetcher.fetch(url).getOrThrow()
//        // parse status list, response is a jwt
//        return reader.read(response)
//    }
}