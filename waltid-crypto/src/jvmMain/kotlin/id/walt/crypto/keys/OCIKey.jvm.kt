package id.walt.crypto.keys


import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec


fun signRequest(
    tenancyOcid: String,
    userOcid: String,
    fingerprint: String,
    restApi: String,
    host: String
) {
    val date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now())
    val dateHeader = "date: $date"
    val hostHeader = "host: $host"
    val requestTarget = "(request-target): GET $restApi"
    val signingString = "$hostHeader$requestTarget$dateHeader$hostHeader"

    println("the signing string is: $signingString")
    val headers = "(request-target) date host"

    // Load the private key from file
    val privateKeyContent ="PRIVATE_KEY_HERE"
    val privateKeyPEM = privateKeyContent
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\n", "")
    println(privateKeyPEM)
    // Decode the private key
    val decodedPrivateKeyBytes = java.util.Base64.getDecoder().decode(privateKeyPEM)
    val privateKeySpec = PKCS8EncodedKeySpec(decodedPrivateKeyBytes)
    val privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec)

    // Sign the string
    val signature = Signature.getInstance("SHA256withRSA")
    signature.initSign(privateKey)
    signature.update(signingString.toByteArray())
    val signedBytes = signature.sign()
    val signedString = java.util.Base64.getEncoder().encodeToString(signedBytes)
    println(signedString)
    val url = URL("https://$host$restApi")
    val con = url.openConnection() as HttpURLConnection
    con.requestMethod = "GET"
    con.setRequestProperty("host", host)
    con.setRequestProperty("Accept", "*/*")
    con.setRequestProperty("Authorization", """Signature version="1",headers="$headers",keyId="$tenancyOcid/$userOcid/$fingerprint",algorithm="rsa-sha256",signature="$signedString"""")

    println("""Signature version="1",headers="$headers",keyId="$tenancyOcid/$userOcid/$fingerprint",algorithm="rsa-sha256",signature="$signedString"""")
    println(con)
    println(con.requestProperties)

    println(con.responseMessage)
}

fun main() {
    val tenancyOcid = "Tenancy_here"
    val userOcid = "ocid_here"
    val fingerprint = "fingerprint_here"
    val restApi = "/20180608/keys?compartmentId=ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q&sortBy=TIMECREATED&sortOrder=DESC"
    val host = "host_here"

    val connection = signRequest(tenancyOcid, userOcid, fingerprint, restApi, host)
    println(connection)
}

