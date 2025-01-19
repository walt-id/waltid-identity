package id.walt.did.dids.resolver.local

import id.walt.did.dids.document.DidDocument
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse
import com.algorand.algosdk.v2.client.model.TransactionsResponse
import kotlinx.serialization.json.Json

actual fun fetchDidDocumentFromAlgorand(didIdentifier: String): DidDocument {
    val algodClient = AlgodClient(
        "https://https://testnet-api.4160.nodely.dev",
        443,
        ""
    )

    val response: PendingTransactionResponse =
        algodClient.PendingTransactionInformation(didIdentifier).execute().body()
    // Retrieve the note field from the transaction object
    // Extract the `note` field from the response directly
    val noteField = response.txn.tx.note as? ByteArray
        ?: throw IllegalArgumentException("No valid note field found in the transaction for ID: $didIdentifier")

    val noteString = String(noteField) // Decode ByteArray to String
    return Json.decodeFromString(noteString)
}

