package id.walt.webwallet.web.controllers.exchange.openapi.examples

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.w3c.utils.VCFormat
import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import id.walt.webwallet.service.exchange.ProofOfPossessionParameters
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.SubmitOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.*
import io.github.smiley4.ktoropenapi.config.ValueExampleDescriptorConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val HTTP_LOCALHOST_PORT = "http://localhost:22222"

object ExchangeExternalSignaturesExamples {

    //OID4VP Examples

    private val prepareOid4vpRequestW3CVCExample = PrepareOID4VPRequest(
        did = "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiWWlkYnltc2loVThaUFEwZ1B3cE1YWmNYM2RIaGxkaldwaEl6clRQaHh4RSIsIngiOiJ6cnVMdHprT2NjYWhNTk5MZjFiTkJVN3AxM254a19CQjU5d1VXelFWVG1jIiwieSI6Inp4UUV1NFF4bmVlQnNGYXNTYkptVjNoUUhJYXVGb3FsNWR6Z1VyTzJ0UjgifQ",
        presentationRequest = "openid4vp://authorize?response_type=vp_token&client_id=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fverify&response_mode=direct_post&state=RVgQIBRCUy0J&presentation_definition_uri=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fpd%2FRVgQIBRCUy0J&client_id_scheme=redirect_uri&client_metadata=%7B%22authorization_encrypted_response_alg%22%3A%22ECDH-ES%22%2C%22authorization_encrypted_response_enc%22%3A%22A256GCM%22%7D&nonce=15c72cd0-8c84-45dc-aae2-a310f6075a8f&response_uri=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fverify%2FRVgQIBRCUy0J",
        selectedCredentialIdList = listOf(
            "urn:uuid:a072cdef-4912-4cdf-99cb-63c0ad7eeed1",
        ),
        disclosures = null,
    )

    private val prepareOid4vpRequestW3CSDJWTVCExample = PrepareOID4VPRequest(
        did = "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoib05ZMkFBVFQ4S1BQeG5Jb3V5MXY5SkE3M0NFQnNwbVJEWjNNdDM4Y0dDVSIsIngiOiJmN2lSZllxdFJVSm5ZWFJhY0VLUFFvMExyUVpmaTcxZ0JZRGZhMWVSelYwIiwieSI6InQ0emZwZXhpaHJBZHlYTU1YSUtkRjNJb0FZUlNqWUJWOEo0QVF4Ym5OQlEifQ",
        presentationRequest = "openid4vp://authorize?response_type=vp_token&client_id=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fverify&response_mode=direct_post&state=kbXoWmRv3kqp&presentation_definition_uri=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fpd%2FkbXoWmRv3kqp&client_id_scheme=redirect_uri&client_metadata=%7B%22authorization_encrypted_response_alg%22%3A%22ECDH-ES%22%2C%22authorization_encrypted_response_enc%22%3A%22A256GCM%22%7D&nonce=09670caf-e69c-4130-909e-ecf4d15c5337&response_uri=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fverify%2FkbXoWmRv3kqp",
        selectedCredentialIdList = listOf(
            "urn:uuid:b0bd6fc9-e3db-4d23-ba0e-0705f6524f71",
        ),
        disclosures = mapOf(
            "urn:uuid:b0bd6fc9-e3db-4d23-ba0e-0705f6524f71" to listOf("WyJvV2tvcnV3NEcxam5BMHFsUjVVSElnPT0iLCJuYW1lIiwiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSJd"),
        ),
    )

    private val prepareOid4vpRequestIETFSDJWTVCExample = PrepareOID4VPRequest(
        did = "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiS2V1bzRITDUwTV8wdFU5U2xYdnhQbUgwQmNHYkFBRVBKVEZCaGItYVhObyIsIngiOiJpZHhJWVFmT1ltRDFQTzB3WFBwQWtTQ0lJOExqbFhkWXpWbG83d0tZZFU0IiwieSI6ImNGZVJacHdQRHc1aTY4czNCajAwb01VVWRLcjhqMlg3UjZrWUxueFlaOFUifQ",
        presentationRequest = "openid4vp://?response_type=vp_token&client_id=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fverify&response_mode=direct_post&state=3yydz2ROSuwl&presentation_definition_uri=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fpd%2F3yydz2ROSuwl&client_id_scheme=redirect_uri&client_metadata=%7B%22authorization_encrypted_response_alg%22%3A%22ECDH-ES%22%2C%22authorization_encrypted_response_enc%22%3A%22A256GCM%22%7D&nonce=67871ae6-3c15-47d8-bad9-5661453a5b60&response_uri=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2Fverify%2F3yydz2ROSuwl",
        selectedCredentialIdList = listOf(
            "4a55eb7a-6fdb-4ed6-8f3d-161994ada9d0",
        ),
        disclosures = mapOf(
            "4a55eb7a-6fdb-4ed6-8f3d-161994ada9d0" to listOf("WyIxYi1aNzVhOVgwRTVZRU9VOUY3dXFBPT0iLCJiaXJ0aGRhdGUiLCIxOTQwLTAxLTAxIl0"),
        ),
    )

    fun prepareOid4vpRequestW3CVCExample(): ValueExampleDescriptorConfig<PrepareOID4VPRequest>.() -> Unit = {
        value = prepareOid4vpRequestW3CVCExample
    }

    fun prepareOid4vpRequestW3CSDJWTVCExample(): ValueExampleDescriptorConfig<PrepareOID4VPRequest>.() -> Unit = {
        value = prepareOid4vpRequestW3CSDJWTVCExample
    }

    fun prepareOid4vpRequestIETFSDJWTVCExample(): ValueExampleDescriptorConfig<PrepareOID4VPRequest>.() -> Unit = {
        value = prepareOid4vpRequestIETFSDJWTVCExample
    }

    private val prepareOid4vpResponseW3CVCExample = PrepareOID4VPResponse.build(
        request = prepareOid4vpRequestW3CVCExample,
        presentationSubmission = PresentationSubmission(
            id = "urn:uuid:c85e079e-d194-44cd-8268-85f4ee819fef",
            definitionId = "urn:uuid:c85e079e-d194-44cd-8268-85f4ee819fef",
            descriptorMap = listOf(
                DescriptorMapping(
                    id = "OpenBadgeCredential",
                    format = VCFormat.jwt_vp,
                    path = "$",
                    pathNested = DescriptorMapping(
                        id = "OpenBadgeCredential",
                        format = VCFormat.jwt_vc_json,
                        path = "$.verifiableCredential[0]",
                    )
                )
            ),
        ),
        w3CJwtVpProofParameters = W3cJwtVpProofParameters(
            header = Json.decodeFromString<Map<String, JsonElement>>(
                """{
            "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiWWlkYnltc2loVThaUFEwZ1B3cE1YWmNYM2RIaGxkaldwaEl6clRQaHh4RSIsIngiOiJ6cnVMdHprT2NjYWhNTk5MZjFiTkJVN3AxM254a19CQjU5d1VXelFWVG1jIiwieSI6Inp4UUV1NFF4bmVlQnNGYXNTYkptVjNoUUhJYXVGb3FsNWR6Z1VyTzJ0UjgifQ#0",
            "typ": "JWT"
        }"""
            ),
            payload = Json.decodeFromString<Map<String, JsonElement>>(
                """{
            "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiWWlkYnltc2loVThaUFEwZ1B3cE1YWmNYM2RIaGxkaldwaEl6clRQaHh4RSIsIngiOiJ6cnVMdHprT2NjYWhNTk5MZjFiTkJVN3AxM254a19CQjU5d1VXelFWVG1jIiwieSI6Inp4UUV1NFF4bmVlQnNGYXNTYkptVjNoUUhJYXVGb3FsNWR6Z1VyTzJ0UjgifQ",
            "nbf": 1727428853,
            "iat": 1727428913,
            "jti": "urn:uuid:c85e079e-d194-44cd-8268-85f4ee819fef",
            "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiWWlkYnltc2loVThaUFEwZ1B3cE1YWmNYM2RIaGxkaldwaEl6clRQaHh4RSIsIngiOiJ6cnVMdHprT2NjYWhNTk5MZjFiTkJVN3AxM254a19CQjU5d1VXelFWVG1jIiwieSI6Inp4UUV1NFF4bmVlQnNGYXNTYkptVjNoUUhJYXVGb3FsNWR6Z1VyTzJ0UjgifQ",
            "aud": "http://localhost:22222/openid4vc/verify",
            "nonce": "15c72cd0-8c84-45dc-aae2-a310f6075a8f",
            "vp": {
                "@context": [
                    "https://www.w3.org/2018/credentials/v1"
                ],
                "type": [
                    "VerifiablePresentation"
                ],
                "id": "urn:uuid:c85e079e-d194-44cd-8268-85f4ee819fef",
                "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiWWlkYnltc2loVThaUFEwZ1B3cE1YWmNYM2RIaGxkaldwaEl6clRQaHh4RSIsIngiOiJ6cnVMdHprT2NjYWhNTk5MZjFiTkJVN3AxM254a19CQjU5d1VXelFWVG1jIiwieSI6Inp4UUV1NFF4bmVlQnNGYXNTYkptVjNoUUhJYXVGb3FsNWR6Z1VyTzJ0UjgifQ",
                "verifiableCredential": [
                    "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInR5cCI6IkpXVCIsImFsZyI6IkVkRFNBIn0.eyJpc3MiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInN1YiI6ImRpZDpqd2s6ZXlKcmRIa2lPaUpGUXlJc0ltTnlkaUk2SWxBdE1qVTJJaXdpYTJsa0lqb2lXV2xrWW5sdGMybG9WVGhhVUZFd1oxQjNjRTFZV21OWU0yUklhR3hrYWxkd2FFbDZjbFJRYUhoNFJTSXNJbmdpT2lKNmNuVk1kSHByVDJOallXaE5UazVNWmpGaVRrSlZOM0F4TTI1NGExOUNRalU1ZDFWWGVsRldWRzFqSWl3aWVTSTZJbnA0VVVWMU5GRjRibVZsUW5OR1lYTlRZa3B0VmpOb1VVaEpZWFZHYjNGc05XUjZaMVZ5VHpKMFVqZ2lmUSIsInZjIjp7IkBjb250ZXh0IjpbImh0dHBzOi8vd3d3LnczLm9yZy8yMDE4L2NyZWRlbnRpYWxzL3YxIiwiaHR0cHM6Ly9wdXJsLmltc2dsb2JhbC5vcmcvc3BlYy9vYi92M3AwL2NvbnRleHQuanNvbiJdLCJpZCI6InVybjp1dWlkOmEwNzJjZGVmLTQ5MTItNGNkZi05OWNiLTYzYzBhZDdlZWVkMSIsInR5cGUiOlsiVmVyaWZpYWJsZUNyZWRlbnRpYWwiLCJPcGVuQmFkZ2VDcmVkZW50aWFsIl0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiaXNzdWVyIjp7InR5cGUiOlsiUHJvZmlsZSJdLCJpZCI6ImRpZDprZXk6ejZNa2pvUmhxMWpTTkpkTGlydVNYckZGeGFncXJ6dFphWEhxSEdVVEtKYmNOeXdwIiwibmFtZSI6IkpvYnMgZm9yIHRoZSBGdXR1cmUgKEpGRikiLCJ1cmwiOiJodHRwczovL3d3dy5qZmYub3JnLyIsImltYWdlIjoiaHR0cHM6Ly93M2MtY2NnLmdpdGh1Yi5pby92Yy1lZC9wbHVnZmVzdC0xLTIwMjIvaW1hZ2VzL0pGRl9Mb2dvTG9ja3VwLnBuZyJ9LCJpc3N1YW5jZURhdGUiOiIyMDI0LTA5LTI3VDA5OjIxOjUzLjE0NTQxNTQzM1oiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjUtMDktMjdUMDk6MjE6NTMuMTQ1NTMwMDcyWiIsImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImlkIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVdXbGtZbmx0YzJsb1ZUaGFVRkV3WjFCM2NFMVlXbU5ZTTJSSWFHeGthbGR3YUVsNmNsUlFhSGg0UlNJc0luZ2lPaUo2Y25WTWRIcHJUMk5qWVdoTlRrNU1aakZpVGtKVk4zQXhNMjU0YTE5Q1FqVTVkMVZYZWxGV1ZHMWpJaXdpZVNJNklucDRVVVYxTkZGNGJtVmxRbk5HWVhOVFlrcHRWak5vVVVoSllYVkdiM0ZzTldSNloxVnlUekowVWpnaWZRIiwidHlwZSI6WyJBY2hpZXZlbWVudFN1YmplY3QiXSwiYWNoaWV2ZW1lbnQiOnsiaWQiOiJ1cm46dXVpZDphYzI1NGJkNS04ZmFkLTRiYjEtOWQyOS1lZmQ5Mzg1MzY5MjYiLCJ0eXBlIjpbIkFjaGlldmVtZW50Il0sIm5hbWUiOiJKRkYgeCB2Yy1lZHUgUGx1Z0Zlc3QgMyBJbnRlcm9wZXJhYmlsaXR5IiwiZGVzY3JpcHRpb24iOiJUaGlzIHdhbGxldCBzdXBwb3J0cyB0aGUgdXNlIG9mIFczQyBWZXJpZmlhYmxlIENyZWRlbnRpYWxzIGFuZCBoYXMgZGVtb25zdHJhdGVkIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdyBkdXJpbmcgSkZGIHggVkMtRURVIFBsdWdGZXN0IDMuIiwiY3JpdGVyaWEiOnsidHlwZSI6IkNyaXRlcmlhIiwibmFycmF0aXZlIjoiV2FsbGV0IHNvbHV0aW9ucyBwcm92aWRlcnMgZWFybmVkIHRoaXMgYmFkZ2UgYnkgZGVtb25zdHJhdGluZyBpbnRlcm9wZXJhYmlsaXR5IGR1cmluZyB0aGUgcHJlc2VudGF0aW9uIHJlcXVlc3Qgd29ya2Zsb3cuIFRoaXMgaW5jbHVkZXMgc3VjY2Vzc2Z1bGx5IHJlY2VpdmluZyBhIHByZXNlbnRhdGlvbiByZXF1ZXN0LCBhbGxvd2luZyB0aGUgaG9sZGVyIHRvIHNlbGVjdCBhdCBsZWFzdCB0d28gdHlwZXMgb2YgdmVyaWZpYWJsZSBjcmVkZW50aWFscyB0byBjcmVhdGUgYSB2ZXJpZmlhYmxlIHByZXNlbnRhdGlvbiwgcmV0dXJuaW5nIHRoZSBwcmVzZW50YXRpb24gdG8gdGhlIHJlcXVlc3RvciwgYW5kIHBhc3NpbmcgdmVyaWZpY2F0aW9uIG9mIHRoZSBwcmVzZW50YXRpb24gYW5kIHRoZSBpbmNsdWRlZCBjcmVkZW50aWFscy4ifSwiaW1hZ2UiOnsiaWQiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTMtMjAyMy9pbWFnZXMvSkZGLVZDLUVEVS1QTFVHRkVTVDMtYmFkZ2UtaW1hZ2UucG5nIiwidHlwZSI6IkltYWdlIn19fX0sImp0aSI6InVybjp1dWlkOmEwNzJjZGVmLTQ5MTItNGNkZi05OWNiLTYzYzBhZDdlZWVkMSIsImV4cCI6MTc1ODk2NDkxMywiaWF0IjoxNzI3NDI4OTEzLCJuYmYiOjE3Mjc0Mjg5MTN9.KHRO2M2EewjvmEN8Y3HyhJtLQUEay2HOg4Axfu-ON-dYGFlpX635dvQj1pW0iuv9uy-XCbcVWH9MbDxfoKu3Bg"
                ]
            }
        }"""
            ),
        ),
        ietfSdJwtVpProofParameters = null,
    )

    private val prepareOid4vpResponseW3CSDJWTVCExample = PrepareOID4VPResponse.build(
        request = prepareOid4vpRequestW3CSDJWTVCExample,
        presentationSubmission = PresentationSubmission(
            id = "urn:uuid:a54fc636-a132-4416-ac4b-74a8e316b4f8",
            definitionId = "urn:uuid:a54fc636-a132-4416-ac4b-74a8e316b4f8",
            descriptorMap = listOf(
                DescriptorMapping(
                    format = VCFormat.jwt_vp,
                    path = "$",
                    pathNested = DescriptorMapping(
                        format = VCFormat.jwt_vc_json,
                        path = "$.verifiableCredential[0]",
                    )
                )
            ),
        ),
        w3CJwtVpProofParameters = W3cJwtVpProofParameters(
            header = Json.decodeFromString<Map<String, JsonElement>>(
                """{
            "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoib05ZMkFBVFQ4S1BQeG5Jb3V5MXY5SkE3M0NFQnNwbVJEWjNNdDM4Y0dDVSIsIngiOiJmN2lSZllxdFJVSm5ZWFJhY0VLUFFvMExyUVpmaTcxZ0JZRGZhMWVSelYwIiwieSI6InQ0emZwZXhpaHJBZHlYTU1YSUtkRjNJb0FZUlNqWUJWOEo0QVF4Ym5OQlEifQ#0",
            "typ": "JWT"
        }"""
            ),
            payload = Json.decodeFromString<Map<String, JsonElement>>(
                """{
            "sub": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoib05ZMkFBVFQ4S1BQeG5Jb3V5MXY5SkE3M0NFQnNwbVJEWjNNdDM4Y0dDVSIsIngiOiJmN2lSZllxdFJVSm5ZWFJhY0VLUFFvMExyUVpmaTcxZ0JZRGZhMWVSelYwIiwieSI6InQ0emZwZXhpaHJBZHlYTU1YSUtkRjNJb0FZUlNqWUJWOEo0QVF4Ym5OQlEifQ",
            "nbf": 1727429586,
            "iat": 1727429646,
            "jti": "urn:uuid:a54fc636-a132-4416-ac4b-74a8e316b4f8",
            "iss": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoib05ZMkFBVFQ4S1BQeG5Jb3V5MXY5SkE3M0NFQnNwbVJEWjNNdDM4Y0dDVSIsIngiOiJmN2lSZllxdFJVSm5ZWFJhY0VLUFFvMExyUVpmaTcxZ0JZRGZhMWVSelYwIiwieSI6InQ0emZwZXhpaHJBZHlYTU1YSUtkRjNJb0FZUlNqWUJWOEo0QVF4Ym5OQlEifQ",
            "aud": "http://localhost:22222/openid4vc/verify",
            "nonce": "09670caf-e69c-4130-909e-ecf4d15c5337",
            "vp": {
                "@context": [
                    "https://www.w3.org/2018/credentials/v1"
                ],
                "type": [
                    "VerifiablePresentation"
                ],
                "id": "urn:uuid:a54fc636-a132-4416-ac4b-74a8e316b4f8",
                "holder": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoib05ZMkFBVFQ4S1BQeG5Jb3V5MXY5SkE3M0NFQnNwbVJEWjNNdDM4Y0dDVSIsIngiOiJmN2lSZllxdFJVSm5ZWFJhY0VLUFFvMExyUVpmaTcxZ0JZRGZhMWVSelYwIiwieSI6InQ0emZwZXhpaHJBZHlYTU1YSUtkRjNJb0FZUlNqWUJWOEo0QVF4Ym5OQlEifQ",
                "verifiableCredential": [
                    "eyJraWQiOiJkaWQ6a2V5Ono2TWtqb1JocTFqU05KZExpcnVTWHJGRnhhZ3FyenRaYVhIcUhHVVRLSmJjTnl3cCIsInR5cCI6Imp3dF92Y19qc29uIiwiYWxnIjoiRWREU0EifQ.eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSIsImh0dHBzOi8vcHVybC5pbXNnbG9iYWwub3JnL3NwZWMvb2IvdjNwMC9jb250ZXh0Lmpzb24iXSwiaWQiOiJ1cm46dXVpZDpiMGJkNmZjOS1lM2RiLTRkMjMtYmEwZS0wNzA1ZjY1MjRmNzEiLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiT3BlbkJhZGdlQ3JlZGVudGlhbCJdLCJpc3N1ZXIiOnsidHlwZSI6WyJQcm9maWxlIl0sImlkIjoiZGlkOmtleTp6Nk1ram9SaHExalNOSmRMaXJ1U1hyRkZ4YWdxcnp0WmFYSHFIR1VUS0piY055d3AiLCJuYW1lIjoiSm9icyBmb3IgdGhlIEZ1dHVyZSAoSkZGKSIsInVybCI6Imh0dHBzOi8vd3d3LmpmZi5vcmcvIiwiaW1hZ2UiOiJodHRwczovL3czYy1jY2cuZ2l0aHViLmlvL3ZjLWVkL3BsdWdmZXN0LTEtMjAyMi9pbWFnZXMvSkZGX0xvZ29Mb2NrdXAucG5nIn0sImlzc3VhbmNlRGF0ZSI6IjIwMjQtMDktMjdUMDk6MzQ6MDUuOTk0MDE5MjI4WiIsImV4cGlyYXRpb25EYXRlIjoiMjAyNS0wOS0yN1QwOTozNDowNS45OTQxMzY0NjBaIiwiY3JlZGVudGlhbFN1YmplY3QiOnsiaWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYjA1Wk1rRkJWRlE0UzFCUWVHNUpiM1Y1TVhZNVNrRTNNME5GUW5Od2JWSkVXak5OZERNNFkwZERWU0lzSW5naU9pSm1OMmxTWmxseGRGSlZTbTVaV0ZKaFkwVkxVRkZ2TUV4eVVWcG1hVGN4WjBKWlJHWmhNV1ZTZWxZd0lpd2llU0k2SW5RMGVtWndaWGhwYUhKQlpIbFlUVTFZU1V0a1JqTkpiMEZaVWxOcVdVSldPRW8wUVZGNFltNU9RbEVpZlEiLCJ0eXBlIjpbIkFjaGlldmVtZW50U3ViamVjdCJdLCJhY2hpZXZlbWVudCI6eyJpZCI6InVybjp1dWlkOmFjMjU0YmQ1LThmYWQtNGJiMS05ZDI5LWVmZDkzODUzNjkyNiIsInR5cGUiOlsiQWNoaWV2ZW1lbnQiXSwibmFtZSI6IkpGRiB4IHZjLWVkdSBQbHVnRmVzdCAzIEludGVyb3BlcmFiaWxpdHkiLCJkZXNjcmlwdGlvbiI6IlRoaXMgd2FsbGV0IHN1cHBvcnRzIHRoZSB1c2Ugb2YgVzNDIFZlcmlmaWFibGUgQ3JlZGVudGlhbHMgYW5kIGhhcyBkZW1vbnN0cmF0ZWQgaW50ZXJvcGVyYWJpbGl0eSBkdXJpbmcgdGhlIHByZXNlbnRhdGlvbiByZXF1ZXN0IHdvcmtmbG93IGR1cmluZyBKRkYgeCBWQy1FRFUgUGx1Z0Zlc3QgMy4iLCJjcml0ZXJpYSI6eyJ0eXBlIjoiQ3JpdGVyaWEiLCJuYXJyYXRpdmUiOiJXYWxsZXQgc29sdXRpb25zIHByb3ZpZGVycyBlYXJuZWQgdGhpcyBiYWRnZSBieSBkZW1vbnN0cmF0aW5nIGludGVyb3BlcmFiaWxpdHkgZHVyaW5nIHRoZSBwcmVzZW50YXRpb24gcmVxdWVzdCB3b3JrZmxvdy4gVGhpcyBpbmNsdWRlcyBzdWNjZXNzZnVsbHkgcmVjZWl2aW5nIGEgcHJlc2VudGF0aW9uIHJlcXVlc3QsIGFsbG93aW5nIHRoZSBob2xkZXIgdG8gc2VsZWN0IGF0IGxlYXN0IHR3byB0eXBlcyBvZiB2ZXJpZmlhYmxlIGNyZWRlbnRpYWxzIHRvIGNyZWF0ZSBhIHZlcmlmaWFibGUgcHJlc2VudGF0aW9uLCByZXR1cm5pbmcgdGhlIHByZXNlbnRhdGlvbiB0byB0aGUgcmVxdWVzdG9yLCBhbmQgcGFzc2luZyB2ZXJpZmljYXRpb24gb2YgdGhlIHByZXNlbnRhdGlvbiBhbmQgdGhlIGluY2x1ZGVkIGNyZWRlbnRpYWxzLiJ9LCJpbWFnZSI6eyJpZCI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtZWQvcGx1Z2Zlc3QtMy0yMDIzL2ltYWdlcy9KRkYtVkMtRURVLVBMVUdGRVNUMy1iYWRnZS1pbWFnZS5wbmciLCJ0eXBlIjoiSW1hZ2UifX19LCJqdGkiOiJ1cm46dXVpZDpiMGJkNmZjOS1lM2RiLTRkMjMtYmEwZS0wNzA1ZjY1MjRmNzEiLCJleHAiOjE3NTg5NjU2NDUsImlhdCI6MTcyNzQyOTY0NSwibmJmIjoxNzI3NDI5NjQ1LCJfc2QiOlsiZlkzOXFvR3dETWt1STlLWV9OUWdkbXVLSkhLNGhkU2hCczVzTUNtUk8yVSJdfQ.TumoxEnpugt9uRfi98KJCjEUi2XKvkyryPPZ5zBNyIBbuEZkuGOYy8R6s8YjJnv7dWsEzWbO4MFeLFLD3f9oBQ~WyJvV2tvcnV3NEcxam5BMHFsUjVVSElnPT0iLCJuYW1lIiwiSkZGIHggdmMtZWR1IFBsdWdGZXN0IDMgSW50ZXJvcGVyYWJpbGl0eSJd"
                ]
            }
        }"""
            ),
        ),
        ietfSdJwtVpProofParameters = null,
    )

    private val prepareOid4vpResponseIETFSDJWTVCExample = PrepareOID4VPResponse.build(
        request = prepareOid4vpRequestIETFSDJWTVCExample,
        presentationSubmission = PresentationSubmission(
            id = "urn:uuid:43825a14-909a-4100-b1bf-20a5369f5993",
            definitionId = "urn:uuid:43825a14-909a-4100-b1bf-20a5369f5993",
            descriptorMap = listOf(
                DescriptorMapping(
                    id = "av4SBACIXkuA",
                    format = VCFormat.sd_jwt_vc,
                    path = "$",
                ),
            ),
        ),
        w3CJwtVpProofParameters = null,
        ietfSdJwtVpProofParameters = listOf(
            IETFSdJwtVpProofParameters(
                credentialId = "4a55eb7a-6fdb-4ed6-8f3d-161994ada9d0",
                sdJwtVc = "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWVDSTZJa2N3VWtsT1FtbEdMVzlSVlVRelpEVkVSMjVsWjFGMVdHVnVTVEk1U2tSaFRVZHZUWFpwYjB0U1FrMGlMQ0o1SWpvaVpXUXpaVVpIY3pKd1JYUnljRGQyUVZvM1FreGpZbkpWZEhCTGExbFhRVlF5U2xCVlVVczBiRTQwUlNKOSIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJmYW1pbHlfbmFtZSI6IkRvZSIsImdpdmVuX25hbWUiOiJKb2huIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2llQ0k2SWtjd1VrbE9RbWxHTFc5UlZVUXpaRFZFUjI1bFoxRjFXR1Z1U1RJNVNrUmhUVWR2VFhacGIwdFNRazBpTENKNUlqb2laV1F6WlVaSGN6SndSWFJ5Y0RkMlFWbzNRa3hqWW5KVmRIQkxhMWxYUVZReVNsQlZVVXMwYkU0MFJTSjkiLCJjbmYiOnsiandrIjp7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYiLCJraWQiOiJLZXVvNEhMNTBNXzB0VTlTbFh2eFBtSDBCY0diQUFFUEpURkJoYi1hWE5vIiwieCI6ImlkeElZUWZPWW1EMVBPMHdYUHBBa1NDSUk4TGpsWGRZelZsbzd3S1lkVTQiLCJ5IjoiY0ZlUlpwd1BEdzVpNjhzM0JqMDBvTVVVZEtyOGoyWDdSNmtZTG54WVo4VSJ9fSwidmN0IjoiaHR0cDovL2xvY2FsaG9zdDoyMjIyMi9pZGVudGl0eV9jcmVkZW50aWFsIiwic3ViIjoiS2V1bzRITDUwTV8wdFU5U2xYdnhQbUgwQmNHYkFBRVBKVEZCaGItYVhObyIsIl9zZCI6WyJtWU1nTUtEWnZ4ZDBDeVZHbTZpa2Z5VkxzTTVyZC0wLWhTdW5sT1Nfc29JIl19.Nr9dAmu62Mg5G8B-C-aubE7GvbVgGT6Mq91f0bdRF4XW1H72JqN0hxt38v1tPozd7tKqWlm1EJlP_u-gavHhUQ~WyIxYi1aNzVhOVgwRTVZRU9VOUY3dXFBPT0iLCJiaXJ0aGRhdGUiLCIxOTQwLTAxLTAxIl0~",
                header = Json.decodeFromString<Map<String, JsonElement>>(
                    """{
                "kid": "Keuo4HL50M_0tU9SlXvxPmH0BcGbAAEPJTFBhb-aXNo",
                "typ": "kb+jwt"
            }"""
                ),
                payload = Json.decodeFromString<Map<String, JsonElement>>(
                    """{
                "iat": 1727431472,
                "aud": "http://localhost:22222/openid4vc/verify",
                "nonce": "67871ae6-3c15-47d8-bad9-5661453a5b60",
                "sd_hash": "Qflclr7AF2MEpYQfJOU6oFGzYI9u8SxmOJ5bXMaSR6Y"
            }"""
                ),
            ),
        ),
    )

    fun prepareOid4vpResponseW3CVCExample(): ValueExampleDescriptorConfig<PrepareOID4VPResponse>.() -> Unit = {
        value = prepareOid4vpResponseW3CVCExample
    }

    fun prepareOid4vpResponseW3CSDJWTVCExample(): ValueExampleDescriptorConfig<PrepareOID4VPResponse>.() -> Unit = {
        value = prepareOid4vpResponseW3CSDJWTVCExample
    }

    fun prepareOid4vpResponseIETFSDJWTVCExample(): ValueExampleDescriptorConfig<PrepareOID4VPResponse>.() -> Unit = {
        value = prepareOid4vpResponseIETFSDJWTVCExample
    }

    private val submitOid4vpRequestW3CVCExample = SubmitOID4VPRequest.build(
        response = prepareOid4vpResponseW3CVCExample,
        disclosures = null,
        w3cJwtVpProof = "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pV1dsa1lubHRjMmxvVlRoYVVGRXdaMUIzY0UxWVdtTllNMlJJYUd4a2FsZHdhRWw2Y2xSUWFIaDRSU0lzSW5naU9pSjZjblZNZEhwclQyTmpZV2hOVGs1TVpqRmlUa0pWTjNBeE0yNTRhMTlDUWpVNWQxVlhlbEZXVkcxaklpd2llU0k2SW5wNFVVVjFORkY0Ym1WbFFuTkdZWE5UWWtwdFZqTm9VVWhKWVhWR2IzRnNOV1I2WjFWeVR6SjBVamdpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pV1dsa1lubHRjMmxvVlRoYVVGRXdaMUIzY0UxWVdtTllNMlJJYUd4a2FsZHdhRWw2Y2xSUWFIaDRSU0lzSW5naU9pSjZjblZNZEhwclQyTmpZV2hOVGs1TVpqRmlUa0pWTjNBeE0yNTRhMTlDUWpVNWQxVlhlbEZXVkcxaklpd2llU0k2SW5wNFVVVjFORkY0Ym1WbFFuTkdZWE5UWWtwdFZqTm9VVWhKWVhWR2IzRnNOV1I2WjFWeVR6SjBVamdpZlEiLCJuYmYiOjE3Mjc0Mjg4NTMsImlhdCI6MTcyNzQyODkxMywianRpIjoidXJuOnV1aWQ6Yzg1ZTA3OWUtZDE5NC00NGNkLTgyNjgtODVmNGVlODE5ZmVmIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaVdXbGtZbmx0YzJsb1ZUaGFVRkV3WjFCM2NFMVlXbU5ZTTJSSWFHeGthbGR3YUVsNmNsUlFhSGg0UlNJc0luZ2lPaUo2Y25WTWRIcHJUMk5qWVdoTlRrNU1aakZpVGtKVk4zQXhNMjU0YTE5Q1FqVTVkMVZYZWxGV1ZHMWpJaXdpZVNJNklucDRVVVYxTkZGNGJtVmxRbk5HWVhOVFlrcHRWak5vVVVoSllYVkdiM0ZzTldSNloxVnlUekowVWpnaWZRIiwiYXVkIjoiaHR0cDovL2xvY2FsaG9zdDoyMjIyMi9vcGVuaWQ0dmMvdmVyaWZ5Iiwibm9uY2UiOiIxNWM3MmNkMC04Yzg0LTQ1ZGMtYWFlMi1hMzEwZjYwNzVhOGYiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJ1cm46dXVpZDpjODVlMDc5ZS1kMTk0LTQ0Y2QtODI2OC04NWY0ZWU4MTlmZWYiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pV1dsa1lubHRjMmxvVlRoYVVGRXdaMUIzY0UxWVdtTllNMlJJYUd4a2FsZHdhRWw2Y2xSUWFIaDRSU0lzSW5naU9pSjZjblZNZEhwclQyTmpZV2hOVGs1TVpqRmlUa0pWTjNBeE0yNTRhMTlDUWpVNWQxVlhlbEZXVkcxaklpd2llU0k2SW5wNFVVVjFORkY0Ym1WbFFuTkdZWE5UWWtwdFZqTm9VVWhKWVhWR2IzRnNOV1I2WjFWeVR6SjBVamdpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ubzJUV3RxYjFKb2NURnFVMDVLWkV4cGNuVlRXSEpHUm5oaFozRnllblJhWVZoSWNVaEhWVlJMU21KalRubDNjQ0lzSW5SNWNDSTZJa3BYVkNJc0ltRnNaeUk2SWtWa1JGTkJJbjAuZXlKcGMzTWlPaUprYVdRNmEyVjVPbm8yVFd0cWIxSm9jVEZxVTA1S1pFeHBjblZUV0hKR1JuaGhaM0Z5ZW5SYVlWaEljVWhIVlZSTFNtSmpUbmwzY0NJc0luTjFZaUk2SW1ScFpEcHFkMnM2WlhsS2NtUklhMmxQYVVwR1VYbEpjMGx0VG5sa2FVazJTV3hCZEUxcVZUSkphWGRwWVRKc2EwbHFiMmxYVjJ4cldXNXNkR015Ykc5V1ZHaGhWVVpGZDFveFFqTmpSVEZaVjIxT1dVMHlVa2xoUjNocllXeGtkMkZGYkRaamJGSlJZVWhvTkZKVFNYTkpibWRwVDJsS05tTnVWazFrU0hCeVZESk9hbGxYYUU1VWF6Vk5XbXBHYVZSclNsWk9NMEY0VFRJMU5HRXhPVU5SYWxVMVpERldXR1ZzUmxkV1J6RnFTV2wzYVdWVFNUWkpibkEwVlZWV01VNUdSalJpYlZac1VXNU9SMWxZVGxSWmEzQjBWbXBPYjFWVmFFcFpXRlpIWWpOR2MwNVhValphTVZaNVZIcEtNRlZxWjJsbVVTSXNJblpqSWpwN0lrQmpiMjUwWlhoMElqcGJJbWgwZEhCek9pOHZkM2QzTG5jekxtOXlaeTh5TURFNEwyTnlaV1JsYm5ScFlXeHpMM1l4SWl3aWFIUjBjSE02THk5d2RYSnNMbWx0YzJkc2IySmhiQzV2Y21jdmMzQmxZeTl2WWk5Mk0zQXdMMk52Ym5SbGVIUXVhbk52YmlKZExDSnBaQ0k2SW5WeWJqcDFkV2xrT21Fd056SmpaR1ZtTFRRNU1USXROR05rWmkwNU9XTmlMVFl6WXpCaFpEZGxaV1ZrTVNJc0luUjVjR1VpT2xzaVZtVnlhV1pwWVdKc1pVTnlaV1JsYm5ScFlXd2lMQ0pQY0dWdVFtRmtaMlZEY21Wa1pXNTBhV0ZzSWwwc0ltNWhiV1VpT2lKS1JrWWdlQ0IyWXkxbFpIVWdVR3gxWjBabGMzUWdNeUJKYm5SbGNtOXdaWEpoWW1sc2FYUjVJaXdpYVhOemRXVnlJanA3SW5SNWNHVWlPbHNpVUhKdlptbHNaU0pkTENKcFpDSTZJbVJwWkRwclpYazZlalpOYTJwdlVtaHhNV3BUVGtwa1RHbHlkVk5ZY2taR2VHRm5jWEo2ZEZwaFdFaHhTRWRWVkV0S1ltTk9lWGR3SWl3aWJtRnRaU0k2SWtwdlluTWdabTl5SUhSb1pTQkdkWFIxY21VZ0tFcEdSaWtpTENKMWNtd2lPaUpvZEhSd2N6b3ZMM2QzZHk1cVptWXViM0puTHlJc0ltbHRZV2RsSWpvaWFIUjBjSE02THk5M00yTXRZMk5uTG1kcGRHaDFZaTVwYnk5Mll5MWxaQzl3YkhWblptVnpkQzB4TFRJd01qSXZhVzFoWjJWekwwcEdSbDlNYjJkdlRHOWphM1Z3TG5CdVp5SjlMQ0pwYzNOMVlXNWpaVVJoZEdVaU9pSXlNREkwTFRBNUxUSTNWREE1T2pJeE9qVXpMakUwTlRReE5UUXpNMW9pTENKbGVIQnBjbUYwYVc5dVJHRjBaU0k2SWpJd01qVXRNRGt0TWpkVU1EazZNakU2TlRNdU1UUTFOVE13TURjeVdpSXNJbU55WldSbGJuUnBZV3hUZFdKcVpXTjBJanA3SW1sa0lqb2laR2xrT21wM2F6cGxlVXB5WkVocmFVOXBTa1pSZVVselNXMU9lV1JwU1RaSmJFRjBUV3BWTWtscGQybGhNbXhyU1dwdmFWZFhiR3RaYm14MFl6SnNiMVpVYUdGVlJrVjNXakZDTTJORk1WbFhiVTVaVFRKU1NXRkhlR3RoYkdSM1lVVnNObU5zVWxGaFNHZzBVbE5KYzBsdVoybFBhVW8yWTI1V1RXUkljSEpVTWs1cVdWZG9UbFJyTlUxYWFrWnBWR3RLVms0elFYaE5NalUwWVRFNVExRnFWVFZrTVZaWVpXeEdWMVpITVdwSmFYZHBaVk5KTmtsdWNEUlZWVll4VGtaR05HSnRWbXhSYms1SFdWaE9WRmxyY0hSV2FrNXZWVlZvU2xsWVZrZGlNMFp6VGxkU05sb3hWbmxVZWtvd1ZXcG5hV1pSSWl3aWRIbHdaU0k2V3lKQlkyaHBaWFpsYldWdWRGTjFZbXBsWTNRaVhTd2lZV05vYVdWMlpXMWxiblFpT25zaWFXUWlPaUoxY200NmRYVnBaRHBoWXpJMU5HSmtOUzA0Wm1Ga0xUUmlZakV0T1dReU9TMWxabVE1TXpnMU16WTVNallpTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MElsMHNJbTVoYldVaU9pSktSa1lnZUNCMll5MWxaSFVnVUd4MVowWmxjM1FnTXlCSmJuUmxjbTl3WlhKaFltbHNhWFI1SWl3aVpHVnpZM0pwY0hScGIyNGlPaUpVYUdseklIZGhiR3hsZENCemRYQndiM0owY3lCMGFHVWdkWE5sSUc5bUlGY3pReUJXWlhKcFptbGhZbXhsSUVOeVpXUmxiblJwWVd4eklHRnVaQ0JvWVhNZ1pHVnRiMjV6ZEhKaGRHVmtJR2x1ZEdWeWIzQmxjbUZpYVd4cGRIa2daSFZ5YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2NtVnhkV1Z6ZENCM2IzSnJabXh2ZHlCa2RYSnBibWNnU2taR0lIZ2dWa010UlVSVklGQnNkV2RHWlhOMElETXVJaXdpWTNKcGRHVnlhV0VpT25zaWRIbHdaU0k2SWtOeWFYUmxjbWxoSWl3aWJtRnljbUYwYVhabElqb2lWMkZzYkdWMElITnZiSFYwYVc5dWN5QndjbTkyYVdSbGNuTWdaV0Z5Ym1Wa0lIUm9hWE1nWW1Ga1oyVWdZbmtnWkdWdGIyNXpkSEpoZEdsdVp5QnBiblJsY205d1pYSmhZbWxzYVhSNUlHUjFjbWx1WnlCMGFHVWdjSEpsYzJWdWRHRjBhVzl1SUhKbGNYVmxjM1FnZDI5eWEyWnNiM2N1SUZSb2FYTWdhVzVqYkhWa1pYTWdjM1ZqWTJWemMyWjFiR3g1SUhKbFkyVnBkbWx1WnlCaElIQnlaWE5sYm5SaGRHbHZiaUJ5WlhGMVpYTjBMQ0JoYkd4dmQybHVaeUIwYUdVZ2FHOXNaR1Z5SUhSdklITmxiR1ZqZENCaGRDQnNaV0Z6ZENCMGQyOGdkSGx3WlhNZ2IyWWdkbVZ5YVdacFlXSnNaU0JqY21Wa1pXNTBhV0ZzY3lCMGJ5QmpjbVZoZEdVZ1lTQjJaWEpwWm1saFlteGxJSEJ5WlhObGJuUmhkR2x2Yml3Z2NtVjBkWEp1YVc1bklIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z2RHOGdkR2hsSUhKbGNYVmxjM1J2Y2l3Z1lXNWtJSEJoYzNOcGJtY2dkbVZ5YVdacFkyRjBhVzl1SUc5bUlIUm9aU0J3Y21WelpXNTBZWFJwYjI0Z1lXNWtJSFJvWlNCcGJtTnNkV1JsWkNCamNtVmtaVzUwYVdGc2N5NGlmU3dpYVcxaFoyVWlPbnNpYVdRaU9pSm9kSFJ3Y3pvdkwzY3pZeTFqWTJjdVoybDBhSFZpTG1sdkwzWmpMV1ZrTDNCc2RXZG1aWE4wTFRNdE1qQXlNeTlwYldGblpYTXZTa1pHTFZaRExVVkVWUzFRVEZWSFJrVlRWRE10WW1Ga1oyVXRhVzFoWjJVdWNHNW5JaXdpZEhsd1pTSTZJa2x0WVdkbEluMTlmWDBzSW1wMGFTSTZJblZ5YmpwMWRXbGtPbUV3TnpKalpHVm1MVFE1TVRJdE5HTmtaaTA1T1dOaUxUWXpZekJoWkRkbFpXVmtNU0lzSW1WNGNDSTZNVGMxT0RrMk5Ea3hNeXdpYVdGMElqb3hOekkzTkRJNE9URXpMQ0p1WW1ZaU9qRTNNamMwTWpnNU1UTjkuS0hSTzJNMkVld2p2bUVOOFkzSHloSnRMUVVFYXkySE9nNEF4ZnUtT04tZFlHRmxwWDYzNWR2UWoxcFcwaXV2OXV5LVhDYmNWV0g5TWJEeGZvS3UzQmciXX19.pAQDUnGESJRhACTyok-ICREUbQ9fCuJvwIAVwyuBInhM2EogJK29Jp4eWwDZ1pRJMyB0NubyjcU3CzFrAzUvhw",
        ietfSdJwtVpProofs = null,
    )

    private val submitOid4vpRequestW3CSDJWTVCExample = SubmitOID4VPRequest.build(
        response = prepareOid4vpResponseW3CSDJWTVCExample,
        disclosures = null,
        w3cJwtVpProof = "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYjA1Wk1rRkJWRlE0UzFCUWVHNUpiM1Y1TVhZNVNrRTNNME5GUW5Od2JWSkVXak5OZERNNFkwZERWU0lzSW5naU9pSm1OMmxTWmxseGRGSlZTbTVaV0ZKaFkwVkxVRkZ2TUV4eVVWcG1hVGN4WjBKWlJHWmhNV1ZTZWxZd0lpd2llU0k2SW5RMGVtWndaWGhwYUhKQlpIbFlUVTFZU1V0a1JqTkpiMEZaVWxOcVdVSldPRW8wUVZGNFltNU9RbEVpZlEjMCIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2In0.eyJzdWIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYjA1Wk1rRkJWRlE0UzFCUWVHNUpiM1Y1TVhZNVNrRTNNME5GUW5Od2JWSkVXak5OZERNNFkwZERWU0lzSW5naU9pSm1OMmxTWmxseGRGSlZTbTVaV0ZKaFkwVkxVRkZ2TUV4eVVWcG1hVGN4WjBKWlJHWmhNV1ZTZWxZd0lpd2llU0k2SW5RMGVtWndaWGhwYUhKQlpIbFlUVTFZU1V0a1JqTkpiMEZaVWxOcVdVSldPRW8wUVZGNFltNU9RbEVpZlEiLCJuYmYiOjE3Mjc0Mjk1ODYsImlhdCI6MTcyNzQyOTY0NiwianRpIjoidXJuOnV1aWQ6YTU0ZmM2MzYtYTEzMi00NDE2LWFjNGItNzRhOGUzMTZiNGY4IiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2lhMmxrSWpvaWIwNVpNa0ZCVkZRNFMxQlFlRzVKYjNWNU1YWTVTa0UzTTBORlFuTndiVkpFV2pOTmRETTRZMGREVlNJc0luZ2lPaUptTjJsU1psbHhkRkpWU201WldGSmhZMFZMVUZGdk1FeHlVVnBtYVRjeFowSlpSR1poTVdWU2VsWXdJaXdpZVNJNkluUTBlbVp3WlhocGFISkJaSGxZVFUxWVNVdGtSak5KYjBGWlVsTnFXVUpXT0VvMFFWRjRZbTVPUWxFaWZRIiwiYXVkIjoiaHR0cDovL2xvY2FsaG9zdDoyMjIyMi9vcGVuaWQ0dmMvdmVyaWZ5Iiwibm9uY2UiOiIwOTY3MGNhZi1lNjljLTQxMzAtOTA5ZS1lY2Y0ZDE1YzUzMzciLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJ1cm46dXVpZDphNTRmYzYzNi1hMTMyLTQ0MTYtYWM0Yi03NGE4ZTMxNmI0ZjgiLCJob2xkZXIiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pYjA1Wk1rRkJWRlE0UzFCUWVHNUpiM1Y1TVhZNVNrRTNNME5GUW5Od2JWSkVXak5OZERNNFkwZERWU0lzSW5naU9pSm1OMmxTWmxseGRGSlZTbTVaV0ZKaFkwVkxVRkZ2TUV4eVVWcG1hVGN4WjBKWlJHWmhNV1ZTZWxZd0lpd2llU0k2SW5RMGVtWndaWGhwYUhKQlpIbFlUVTFZU1V0a1JqTkpiMEZaVWxOcVdVSldPRW8wUVZGNFltNU9RbEVpZlEiLCJ2ZXJpZmlhYmxlQ3JlZGVudGlhbCI6WyJleUpyYVdRaU9pSmthV1E2YTJWNU9ubzJUV3RxYjFKb2NURnFVMDVLWkV4cGNuVlRXSEpHUm5oaFozRnllblJhWVZoSWNVaEhWVlJMU21KalRubDNjQ0lzSW5SNWNDSTZJbXAzZEY5MlkxOXFjMjl1SWl3aVlXeG5Jam9pUldSRVUwRWlmUS5leUpBWTI5dWRHVjRkQ0k2V3lKb2RIUndjem92TDNkM2R5NTNNeTV2Y21jdk1qQXhPQzlqY21Wa1pXNTBhV0ZzY3k5Mk1TSXNJbWgwZEhCek9pOHZjSFZ5YkM1cGJYTm5iRzlpWVd3dWIzSm5MM053WldNdmIySXZkak53TUM5amIyNTBaWGgwTG1wemIyNGlYU3dpYVdRaU9pSjFjbTQ2ZFhWcFpEcGlNR0prTm1aak9TMWxNMlJpTFRSa01qTXRZbUV3WlMwd056QTFaalkxTWpSbU56RWlMQ0owZVhCbElqcGJJbFpsY21sbWFXRmliR1ZEY21Wa1pXNTBhV0ZzSWl3aVQzQmxia0poWkdkbFEzSmxaR1Z1ZEdsaGJDSmRMQ0pwYzNOMVpYSWlPbnNpZEhsd1pTSTZXeUpRY205bWFXeGxJbDBzSW1sa0lqb2laR2xrT210bGVUcDZOazFyYW05U2FIRXhhbE5PU21STWFYSjFVMWh5UmtaNFlXZHhjbnAwV21GWVNIRklSMVZVUzBwaVkwNTVkM0FpTENKdVlXMWxJam9pU205aWN5Qm1iM0lnZEdobElFWjFkSFZ5WlNBb1NrWkdLU0lzSW5WeWJDSTZJbWgwZEhCek9pOHZkM2QzTG1wbVppNXZjbWN2SWl3aWFXMWhaMlVpT2lKb2RIUndjem92TDNjell5MWpZMmN1WjJsMGFIVmlMbWx2TDNaakxXVmtMM0JzZFdkbVpYTjBMVEV0TWpBeU1pOXBiV0ZuWlhNdlNrWkdYMHh2WjI5TWIyTnJkWEF1Y0c1bkluMHNJbWx6YzNWaGJtTmxSR0YwWlNJNklqSXdNalF0TURrdE1qZFVNRGs2TXpRNk1EVXVPVGswTURFNU1qSTRXaUlzSW1WNGNHbHlZWFJwYjI1RVlYUmxJam9pTWpBeU5TMHdPUzB5TjFRd09Ub3pORG93TlM0NU9UUXhNelkwTmpCYUlpd2lZM0psWkdWdWRHbGhiRk4xWW1wbFkzUWlPbnNpYVdRaU9pSmthV1E2YW5kck9tVjVTbkprU0d0cFQybEtSbEY1U1hOSmJVNTVaR2xKTmtsc1FYUk5hbFV5U1dsM2FXRXliR3RKYW05cFlqQTFXazFyUmtKV1JsRTBVekZDVVdWSE5VcGlNMVkxVFZoWk5WTnJSVE5OTUU1R1VXNU9kMkpXU2tWWGFrNU9aRVJOTkZrd1pFUldVMGx6U1c1bmFVOXBTbTFPTW14VFdteHNlR1JHU2xaVGJUVmFWMFpLYUZrd1ZreFZSa1oyVFVWNGVWVldjRzFoVkdONFdqQktXbEpIV21oTlYxWlRaV3haZDBscGQybGxVMGsyU1c1Uk1HVnRXbmRhV0dod1lVaEtRbHBJYkZsVVZURlpVMVYwYTFKcVRrcGlNRVphVld4T2NWZFZTbGRQUlc4d1VWWkdORmx0TlU5UmJFVnBabEVpTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MFUzVmlhbVZqZENKZExDSmhZMmhwWlhabGJXVnVkQ0k2ZXlKcFpDSTZJblZ5YmpwMWRXbGtPbUZqTWpVMFltUTFMVGhtWVdRdE5HSmlNUzA1WkRJNUxXVm1aRGt6T0RVek5qa3lOaUlzSW5SNWNHVWlPbHNpUVdOb2FXVjJaVzFsYm5RaVhTd2libUZ0WlNJNklrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpTENKa1pYTmpjbWx3ZEdsdmJpSTZJbFJvYVhNZ2QyRnNiR1YwSUhOMWNIQnZjblJ6SUhSb1pTQjFjMlVnYjJZZ1Z6TkRJRlpsY21sbWFXRmliR1VnUTNKbFpHVnVkR2xoYkhNZ1lXNWtJR2hoY3lCa1pXMXZibk4wY21GMFpXUWdhVzUwWlhKdmNHVnlZV0pwYkdsMGVTQmtkWEpwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCeVpYRjFaWE4wSUhkdmNtdG1iRzkzSUdSMWNtbHVaeUJLUmtZZ2VDQldReTFGUkZVZ1VHeDFaMFpsYzNRZ015NGlMQ0pqY21sMFpYSnBZU0k2ZXlKMGVYQmxJam9pUTNKcGRHVnlhV0VpTENKdVlYSnlZWFJwZG1VaU9pSlhZV3hzWlhRZ2MyOXNkWFJwYjI1eklIQnliM1pwWkdWeWN5QmxZWEp1WldRZ2RHaHBjeUJpWVdSblpTQmllU0JrWlcxdmJuTjBjbUYwYVc1bklHbHVkR1Z5YjNCbGNtRmlhV3hwZEhrZ1pIVnlhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnY21WeGRXVnpkQ0IzYjNKclpteHZkeTRnVkdocGN5QnBibU5zZFdSbGN5QnpkV05qWlhOelpuVnNiSGtnY21WalpXbDJhVzVuSUdFZ2NISmxjMlZ1ZEdGMGFXOXVJSEpsY1hWbGMzUXNJR0ZzYkc5M2FXNW5JSFJvWlNCb2IyeGtaWElnZEc4Z2MyVnNaV04wSUdGMElHeGxZWE4wSUhSM2J5QjBlWEJsY3lCdlppQjJaWEpwWm1saFlteGxJR055WldSbGJuUnBZV3h6SUhSdklHTnlaV0YwWlNCaElIWmxjbWxtYVdGaWJHVWdjSEpsYzJWdWRHRjBhVzl1TENCeVpYUjFjbTVwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCMGJ5QjBhR1VnY21WeGRXVnpkRzl5TENCaGJtUWdjR0Z6YzJsdVp5QjJaWEpwWm1sallYUnBiMjRnYjJZZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCaGJtUWdkR2hsSUdsdVkyeDFaR1ZrSUdOeVpXUmxiblJwWVd4ekxpSjlMQ0pwYldGblpTSTZleUpwWkNJNkltaDBkSEJ6T2k4dmR6TmpMV05qWnk1bmFYUm9kV0l1YVc4dmRtTXRaV1F2Y0d4MVoyWmxjM1F0TXkweU1ESXpMMmx0WVdkbGN5OUtSa1l0VmtNdFJVUlZMVkJNVlVkR1JWTlVNeTFpWVdSblpTMXBiV0ZuWlM1d2JtY2lMQ0owZVhCbElqb2lTVzFoWjJVaWZYMTlMQ0pxZEdraU9pSjFjbTQ2ZFhWcFpEcGlNR0prTm1aak9TMWxNMlJpTFRSa01qTXRZbUV3WlMwd056QTFaalkxTWpSbU56RWlMQ0psZUhBaU9qRTNOVGc1TmpVMk5EVXNJbWxoZENJNk1UY3lOelF5T1RZME5Td2libUptSWpveE56STNOREk1TmpRMUxDSmZjMlFpT2xzaVpsa3pPWEZ2UjNkRVRXdDFTVGxMV1Y5T1VXZGtiWFZMU2toTE5HaGtVMmhDY3pWelRVTnRVazh5VlNKZGZRLlR1bW94RW5wdWd0OXVSZmk5OEtKQ2pFVWkyWEt2a3lyeVBQWjV6Qk55SUJidUVaa3VHT1l5OFI2czhZakpudjdkV3NFeldiTzRNRmVMRkxEM2Y5b0JRfld5SnZWMnR2Y25WM05FY3hhbTVCTUhGc1VqVlZTRWxuUFQwaUxDSnVZVzFsSWl3aVNrWkdJSGdnZG1NdFpXUjFJRkJzZFdkR1pYTjBJRE1nU1c1MFpYSnZjR1Z5WVdKcGJHbDBlU0pkIl19fQ.WxYlmga0lMAFEbHWXqJ0IKLU-hyhFgBtOTk1KKUsT9wfdC5im9BQfemrTE1sTvKOsiuZ9RBBLReeGaUalpTcgg",
        ietfSdJwtVpProofs = null,
    )

    private val submitOid4vpRequestIETFSDJWTVCExample = SubmitOID4VPRequest.build(
        response = prepareOid4vpResponseIETFSDJWTVCExample,
        disclosures = mapOf(
            "4a55eb7a-6fdb-4ed6-8f3d-161994ada9d0" to listOf("WyIxYi1aNzVhOVgwRTVZRU9VOUY3dXFBPT0iLCJiaXJ0aGRhdGUiLCIxOTQwLTAxLTAxIl0"),
        ),
        w3cJwtVpProof = null,
        ietfSdJwtVpProofs = listOf(
            IETFSdJwtVpTokenProof(
                credentialId = "4a55eb7a-6fdb-4ed6-8f3d-161994ada9d0",
                sdJwtVc = "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWVDSTZJa2N3VWtsT1FtbEdMVzlSVlVRelpEVkVSMjVsWjFGMVdHVnVTVEk1U2tSaFRVZHZUWFpwYjB0U1FrMGlMQ0o1SWpvaVpXUXpaVVpIY3pKd1JYUnljRGQyUVZvM1FreGpZbkpWZEhCTGExbFhRVlF5U2xCVlVVczBiRTQwUlNKOSIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJmYW1pbHlfbmFtZSI6IkRvZSIsImdpdmVuX25hbWUiOiJKb2huIiwiaXNzIjoiZGlkOmp3azpleUpyZEhraU9pSkZReUlzSW1OeWRpSTZJbEF0TWpVMklpd2llQ0k2SWtjd1VrbE9RbWxHTFc5UlZVUXpaRFZFUjI1bFoxRjFXR1Z1U1RJNVNrUmhUVWR2VFhacGIwdFNRazBpTENKNUlqb2laV1F6WlVaSGN6SndSWFJ5Y0RkMlFWbzNRa3hqWW5KVmRIQkxhMWxYUVZReVNsQlZVVXMwYkU0MFJTSjkiLCJjbmYiOnsiandrIjp7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYiLCJraWQiOiJLZXVvNEhMNTBNXzB0VTlTbFh2eFBtSDBCY0diQUFFUEpURkJoYi1hWE5vIiwieCI6ImlkeElZUWZPWW1EMVBPMHdYUHBBa1NDSUk4TGpsWGRZelZsbzd3S1lkVTQiLCJ5IjoiY0ZlUlpwd1BEdzVpNjhzM0JqMDBvTVVVZEtyOGoyWDdSNmtZTG54WVo4VSJ9fSwidmN0IjoiaHR0cDovL2xvY2FsaG9zdDoyMjIyMi9pZGVudGl0eV9jcmVkZW50aWFsIiwic3ViIjoiS2V1bzRITDUwTV8wdFU5U2xYdnhQbUgwQmNHYkFBRVBKVEZCaGItYVhObyIsIl9zZCI6WyJtWU1nTUtEWnZ4ZDBDeVZHbTZpa2Z5VkxzTTVyZC0wLWhTdW5sT1Nfc29JIl19.Nr9dAmu62Mg5G8B-C-aubE7GvbVgGT6Mq91f0bdRF4XW1H72JqN0hxt38v1tPozd7tKqWlm1EJlP_u-gavHhUQ~WyIxYi1aNzVhOVgwRTVZRU9VOUY3dXFBPT0iLCJiaXJ0aGRhdGUiLCIxOTQwLTAxLTAxIl0~",
                vpTokenProof = "eyJraWQiOiJLZXVvNEhMNTBNXzB0VTlTbFh2eFBtSDBCY0diQUFFUEpURkJoYi1hWE5vIiwidHlwIjoia2Irand0IiwiYWxnIjoiRVMyNTYifQ.eyJpYXQiOjE3Mjc0MzE0NzIsImF1ZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6MjIyMjIvb3BlbmlkNHZjL3ZlcmlmeSIsIm5vbmNlIjoiNjc4NzFhZTYtM2MxNS00N2Q4LWJhZDktNTY2MTQ1M2E1YjYwIiwic2RfaGFzaCI6IlFmbGNscjdBRjJNRXBZUWZKT1U2b0ZHellJOXU4U3htT0o1YlhNYVNSNlkifQ.Tiy7HiBZFOB2_txrqxve5gLajYqGdznY17qBJiOxVB5kb5RCLM313BsuAGReUnIiIdSy5yY1F2q102c9K6YN4Q",
            )
        ),
    )

    fun submitOid4vpRequestW3CVCExample(): ValueExampleDescriptorConfig<SubmitOID4VPRequest>.() -> Unit = {
        value = submitOid4vpRequestW3CVCExample
    }

    fun submitOid4vpRequestW3CSDJWTVCExample(): ValueExampleDescriptorConfig<SubmitOID4VPRequest>.() -> Unit = {
        value = submitOid4vpRequestW3CSDJWTVCExample
    }

    fun submitOid4vpRequestIETFSDJWTVCExample(): ValueExampleDescriptorConfig<SubmitOID4VPRequest>.() -> Unit = {
        value = submitOid4vpRequestIETFSDJWTVCExample
    }

    //OID4VCI Examples

    private val prepareOid4vciRequestDefaultExample = PrepareOID4VCIRequest(
        did = "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiQ3ZlUzFZdWdUNWV2VnoxOGswWHBjVGQtUVVsS1AxRzdCdTJJMkpoSjF2YyIsIngiOiJCeGN2d0lBVDhudzRtdjVtUDhSTzlrYmc3N25sbVZoOG5RcWZVUEtuRDVBIiwieSI6IkthU01seTBRQUhSQzZKMXFDRDloRkZnV0dXMXp1cExwU3ZtVGtHMDJmNHcifQ",
        offerURL = "openid-credential-offer://localhost:22222/?credential_offer_uri=http%3A%2F%2Flocalhost%3A22222%2Fopenid4vc%2FcredentialOffer%3Fid%3D8030eb87-fa89-4820-ad36-8e89cc9ccdfe",
    )

    fun prepareOid4vciRequestDefaultExample(): ValueExampleDescriptorConfig<PrepareOID4VCIRequest>.() -> Unit = {
        value = prepareOid4vciRequestDefaultExample
    }

    private val prepareOid4vciResponseW3CVCExample = PrepareOID4VCIResponse.build(
        request = prepareOid4vciRequestDefaultExample,
        offeredCredentialsProofRequests = listOf(
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters(
                offeredCredential = OfferedCredential(
                    format = CredentialFormat.jwt_vc_json,
                    credentialDefinition = CredentialDefinition(
                        type = listOf("VerifiableCredential", "OpenBadgeCredential"),
                    ),
                    cryptographicBindingMethodsSupported = setOf("did"),
                ),
                proofOfPossessionParameters = ProofOfPossessionParameters(
                    proofType = ProofType.jwt,
                    header = Json.parseToJsonElement(
                        """{
                    "typ": "openid4vci-proof+jwt",
                    "kid": "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiQ3ZlUzFZdWdUNWV2VnoxOGswWHBjVGQtUVVsS1AxRzdCdTJJMkpoSjF2YyIsIngiOiJCeGN2d0lBVDhudzRtdjVtUDhSTzlrYmc3N25sbVZoOG5RcWZVUEtuRDVBIiwieSI6IkthU01seTBRQUhSQzZKMXFDRDloRkZnV0dXMXp1cExwU3ZtVGtHMDJmNHcifQ#0"
                }"""
                    ),
                    payload = Json.parseToJsonElement(
                        """{
                    "aud": "http://localhost:22222",
                    "iat": 1727417971,
                    "nonce": "4d513194-fa61-4099-9bbc-3fb0808ca4f1"
                }"""
                    ),
                )
            )
        ),
        accessToken = "eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiI4MDMwZWI4Ny1mYTg5LTQ4MjAtYWQzNi04ZTg5Y2M5Y2NkZmUiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiYXVkIjoiQUNDRVNTIn0.GpB9QZ-ZgCSNxBzlnkf_RBKmfMI15wH5rR2gW4EfLa640gOo9taaMLgYKax8f8kOgd5byvEf6hmGhWyGt7xTCQ",
        credentialIssuer = "http://localhost:22222",
    )

    private val prepareOid4vciResponseIETFSDJWTVCExample = PrepareOID4VCIResponse.build(
        request = prepareOid4vciRequestDefaultExample,
        offeredCredentialsProofRequests = listOf(
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters(
                offeredCredential = OfferedCredential(
                    format = CredentialFormat.sd_jwt_vc,
                    vct = "http://localhost:22222/draft13/identity_credential",
                    cryptographicBindingMethodsSupported = setOf("jwk"),
                ),
                proofOfPossessionParameters = ProofOfPossessionParameters(
                    proofType = ProofType.jwt,
                    header = Json.parseToJsonElement(
                        """{
                    "typ": "openid4vci-proof+jwt",
                    "jwk": {
                        "kty": "EC",
                        "crv": "P-256",
                        "kid": "YCm5zh_RuMxsJI_mzmrm7wft0fxKpRuA0Dg-EVXBNxk",
                        "x": "uqUtdBaVFrRpwXY--D1TQYj0EXrGWJVih5bvHgb-UJ8",
                        "y": "th6kYpPmlMIGDIGz1QoF8zKjyuMkqqH1Phx_DDTsDbg"
                    }
                }"""
                    ),
                    payload = Json.parseToJsonElement(
                        """{
                    "aud": "http://localhost:22222",
                    "iat": 1727421749,
                    "nonce": "84dba81a-c0a2-41ba-abe7-1f75514ae52d"
                }"""
                    ),
                )
            )
        ),
        accessToken = "eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiI4MDMwZWI4Ny1mYTg5LTQ4MjAtYWQzNi04ZTg5Y2M5Y2NkZmUiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiYXVkIjoiQUNDRVNTIn0.GpB9QZ-ZgCSNxBzlnkf_RBKmfMI15wH5rR2gW4EfLa640gOo9taaMLgYKax8f8kOgd5byvEf6hmGhWyGt7xTCQ",
        credentialIssuer = "http://localhost:22222",
    )

    private val prepareOid4vciResponseMDocVCExample = PrepareOID4VCIResponse.build(
        request = prepareOid4vciRequestDefaultExample,
        offeredCredentialsProofRequests = listOf(
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters(
                offeredCredential = OfferedCredential(
                    format = CredentialFormat.mso_mdoc,
                    docType = "org.iso.18013.5.1.mDL",
                    cryptographicBindingMethodsSupported = setOf("cose_key"),
                    proofTypesSupported = mapOf(
                        ProofType.cwt to ProofTypeMetadata(setOf("ES256"))
                    )
                ),
                proofOfPossessionParameters = ProofOfPossessionParameters(
                    proofType = ProofType.cwt,
                    header = "ogN0b3BlbmlkNHZjaS1wcm9vZitjd3RoQ09TRV9LZXlYS6QBAiABIVggiSe+d9mpkjhymrOaV/FnjpZeKXzk8WfVVswkXzVvkqciWCDAaRYPpW3ieOpxiRpdZedMTjzRfh0oUWQSbGVQhW8m2A==".toJsonElement(),
                    payload = "owN2aHR0cDovL2xvY2FsaG9zdDoyMjIyMgYaZvZW7wpYJGY0MjJiNTI1LTMxYTAtNDdmMS1hNjJkLTU4N2UwNTUyMDQxNA==".toJsonElement(),
                )
            )
        ),
        accessToken = "eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiIwYWUyYThjZS00MThmLTQxYmQtOGYyMi0zZWIyNDUxMjdiZmYiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiYXVkIjoiQUNDRVNTIn0.EBsSIl8wlkLTenGrUb5kD6DCTgC1yzBc2MB1cQ-tUWXuTP_WjXRtSZq6MasAzFdavdeEu4_4v5RtZ79-covACA",
        credentialIssuer = "http://localhost:22222",
    )

    fun prepareOid4vciResponseW3CVCExample(): ValueExampleDescriptorConfig<PrepareOID4VCIResponse>.() -> Unit = {
        value = prepareOid4vciResponseW3CVCExample
    }

    fun prepareOid4vciResponseIETFSDJWTVCExample(): ValueExampleDescriptorConfig<PrepareOID4VCIResponse>.() -> Unit = {
        value = prepareOid4vciResponseIETFSDJWTVCExample
    }

    fun prepareOid4vciResponseMDocVCExample(): ValueExampleDescriptorConfig<PrepareOID4VCIResponse>.() -> Unit = {
        value = prepareOid4vciResponseMDocVCExample
    }

    private val submitOid4vciRequestW3CVCExample = SubmitOID4VCIRequest.build(
        response = prepareOid4vciResponseW3CVCExample,
        offeredCredentialProofsOfPossession = listOf(
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                offeredCredential = OfferedCredential(
                    format = CredentialFormat.jwt_vc_json,
                    credentialDefinition = CredentialDefinition(
                        type = listOf("VerifiableCredential", "OpenBadgeCredential"),
                    ),
                    cryptographicBindingMethodsSupported = setOf("did"),
                ),
                proofType = ProofType.jwt,
                signedProofOfPossession = "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pUTNabFV6RlpkV2RVTldWMlZub3hPR3N3V0hCalZHUXRVVlZzUzFBeFJ6ZENkVEpKTWtwb1NqRjJZeUlzSW5naU9pSkNlR04yZDBsQlZEaHVkelJ0ZGpWdFVEaFNUemxyWW1jM04yNXNiVlpvT0c1UmNXWlZVRXR1UkRWQklpd2llU0k2SWt0aFUwMXNlVEJSUVVoU1F6WktNWEZEUkRsb1JrWm5WMGRYTVhwMWNFeHdVM1p0Vkd0SE1ESm1OSGNpZlEjMCIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0IiwiYWxnIjoiRVMyNTYifQ.eyJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiaWF0IjoxNzI3NDE3OTcxLCJub25jZSI6IjRkNTEzMTk0LWZhNjEtNDA5OS05YmJjLTNmYjA4MDhjYTRmMSJ9.-NOvj6bfl0l8_BSxPR3UAyO-KkZ4zdfPZ401GNZl8DWg4MCtZN3lWIdnPBsqTY1aAiYfN3symtMzhdtCqpivAA",
            )
        ),
        credentialIssuer = "http://localhost:22222",
    )

    private val submitOid4vciRequestIETFSDJWTVCExample = SubmitOID4VCIRequest.build(
        response = prepareOid4vciResponseIETFSDJWTVCExample,
        offeredCredentialProofsOfPossession = listOf(
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                offeredCredential = OfferedCredential(
                    format = CredentialFormat.sd_jwt_vc,
                    vct = "http://localhost:22222/draft13/identity_credential",
                    cryptographicBindingMethodsSupported = setOf("jwk"),
                ),
                proofType = ProofType.jwt,
                signedProofOfPossession = "eyJ0eXAiOiJvcGVuaWQ0dmNpLXByb29mK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYiLCJraWQiOiJZQ201emhfUnVNeHNKSV9tem1ybTd3ZnQwZnhLcFJ1QTBEZy1FVlhCTnhrIiwieCI6InVxVXRkQmFWRnJScHdYWS0tRDFUUVlqMEVYckdXSlZpaDVidkhnYi1VSjgiLCJ5IjoidGg2a1lwUG1sTUlHRElHejFRb0Y4ektqeXVNa3FxSDFQaHhfRERUc0RiZyJ9fQ.eyJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiaWF0IjoxNzI3NDIxNzQ5LCJub25jZSI6Ijg0ZGJhODFhLWMwYTItNDFiYS1hYmU3LTFmNzU1MTRhZTUyZCJ9.y2X40DKyUXCfHb1gGzdGaz3__xfAk1Tss0cj3M4oxooI0edHUhuKUknS7yIe7bRQErqc8s0neIM53w0XQSpFbA",
            )
        ),
        credentialIssuer = "http://localhost:22222",
    )

    private val submitOid4vciRequestMDocVCExample = SubmitOID4VCIRequest.build(
        response = prepareOid4vciResponseMDocVCExample,
        offeredCredentialProofsOfPossession = listOf(
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                offeredCredential = OfferedCredential(
                    format = CredentialFormat.mso_mdoc,
                    docType = "org.iso.18013.5.1.mDL",
                    cryptographicBindingMethodsSupported = setOf("cose_key"),
                    proofTypesSupported = mapOf(
                        ProofType.cwt to ProofTypeMetadata(setOf("ES256"))
                    )
                ),
                proofType = ProofType.cwt,
                signedProofOfPossession = "eyJraWQiOiJkaWQ6andrOmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNklsQXRNalUySWl3aWEybGtJam9pUTNabFV6RlpkV2RVTldWMlZub3hPR3N3V0hCalZHUXRVVlZzUzFBeFJ6ZENkVEpKTWtwb1NqRjJZeUlzSW5naU9pSkNlR04yZDBsQlZEaHVkelJ0ZGpWdFVEaFNUemxyWW1jM04yNXNiVlpvT0c1UmNXWlZVRXR1UkRWQklpd2llU0k2SWt0aFUwMXNlVEJSUVVoU1F6WktNWEZEUkRsb1JrWm5WMGRYTVhwMWNFeHdVM1p0Vkd0SE1ESm1OSGNpZlEjMCIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0IiwiYWxnIjoiRVMyNTYifQ.eyJhdWQiOiJodHRwOi8vbG9jYWxob3N0OjIyMjIyIiwiaWF0IjoxNzI3NDE3OTcxLCJub25jZSI6IjRkNTEzMTk0LWZhNjEtNDA5OS05YmJjLTNmYjA4MDhjYTRmMSJ9.-NOvj6bfl0l8_BSxPR3UAyO-KkZ4zdfPZ401GNZl8DWg4MCtZN3lWIdnPBsqTY1aAiYfN3symtMzhdtCqpivAA",
            )
        ),
        credentialIssuer = "http://localhost:22222",
    )

    fun submitOid4vciRequestW3CVCExample(): ValueExampleDescriptorConfig<SubmitOID4VCIRequest>.() -> Unit = {
        value = submitOid4vciRequestW3CVCExample
    }

    fun submitOid4vciRequestIETFSDJWTVCExample(): ValueExampleDescriptorConfig<SubmitOID4VCIRequest>.() -> Unit = {
        value = submitOid4vciRequestIETFSDJWTVCExample
    }

    fun submitOid4vciRequestMDocVCExample(): ValueExampleDescriptorConfig<SubmitOID4VCIRequest>.() -> Unit = {
        value = submitOid4vciRequestMDocVCExample
    }
}
/*
                        example("When proofType == cwt") {
                            value = PrepareOID4VCIResponse(
                                did = "did:web:walt.id",
                                offerURL = "openid-credential-offer://?credential_offer=",
                                offeredCredentialsProofRequests = listOf(
                                    IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters(
                                        OfferedCredential(
                                            format = CredentialFormat.mso_mdoc,
                                        ),
                                        ProofOfPossessionParameters(
                                            ProofType.cwt,
                                            "<<JSON-ENCODED BYTE ARRAY OF CBOR MAP>>".toJsonElement(),
                                            "<<JSON-ENCODED BYTE ARRAY OF CBOR MAP>>".toJsonElement(),
                                        )
                                    )
                                ),
                                credentialIssuer = "https://issuer.portal.walt.id"
                            )
                        }
                        example("When proofType == jwt") {
                            value = PrepareOID4VCIResponse(
                                did = "did:web:walt.id",
                                offerURL = "openid-credential-offer://?credential_offer=",
                                offeredCredentialsProofRequests = listOf(
                                    IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters(
                                        OfferedCredential(
                                            format = CredentialFormat.jwt_vc_json,
                                        ),
                                        ProofOfPossessionParameters(
                                            ProofType.jwt,
                                            "<<JWT HEADER SECTION>>".toJsonElement(),
                                            "<<JWT CLAIMS SECTION>>".toJsonElement(),
                                        )
                                    )
                                ),
                                credentialIssuer = "https://issuer.portal.walt.id"
                            )
                        }


* */
