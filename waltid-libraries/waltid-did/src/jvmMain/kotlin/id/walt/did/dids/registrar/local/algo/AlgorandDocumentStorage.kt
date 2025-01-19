package id.walt.did.dids.registrar.local.algo

import id.walt.did.dids.document.DidDocument
import com.algorand.algosdk.account.Account
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder

actual fun saveDidDocumentToAlgorand(didDocument: DidDocument) {
    val account = Account("future before follow brain spy reform kit name over foster put law")

    val algodClient = AlgodClient(
        "https://https://testnet-api.4160.nodely.dev",
        443,
        ""
    )

    val params = algodClient.TransactionParams().execute().body()

    val transaction = Transaction.PaymentTransactionBuilder()
        .sender(account.address)
        .receiver(account.address) // Self-payment
        .amount(0)
        .suggestedParams(params)
        .noteUTF8(didDocument.toString())
        .build()

    val signedTransaction = account.signTransaction(transaction)
    val signedTransactionBytes = Encoder.encodeToMsgPack(signedTransaction)
    val txId = algodClient.RawTransaction().rawtxn(signedTransactionBytes).execute().body().txId
    println("DID Document saved to Algorand with transaction ID: $txId")
}


