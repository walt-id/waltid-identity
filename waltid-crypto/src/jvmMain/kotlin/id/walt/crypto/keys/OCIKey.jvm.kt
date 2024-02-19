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
    val privateKeyContent ="-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC0o+MR0tljVFdt\n" +
            "EyNnov8nxXv905sWC4Px2lXAY6N1Y1ok8onERT9BsQ1004tlh1fyJqtUgqUMbvum\n" +
            "U5iBfOWMeCFzZlNP7tqrlZ/lvrEkDo8q6Cy0zu7gJIHkKQcD9VLCdtqpkM59BSGh\n" +
            "Uy3rQ7p1/JRQRkAqVlvjbj4ZJwySKn4c8k1gkGPv4e2ixMGonWbOn71A7F/WylC0\n" +
            "0NXaoSxp5Xxt8LufrV+ZuS9ck/o69kXiWCuD7Tkh0Lulob4hRhELgG/9qJz39qR2\n" +
            "WOLVM+3gaZroXN4C+UU2c2qKYiyEk/NIOiQWTwsRKtcrZz4biY/tWfUUpRn60/Jm\n" +
            "x5J4Xm7bAgMBAAECggEAUGn2nwwcbw3wP2O6KpwSdyuAN24YR3eH3MXjz5nrfcnl\n" +
            "KrZ3otJqxv0g7uVvVBqsiWUydxQTklXjm9bx+I7XhzFBPuSJ6Pb9DE422KkLpW//\n" +
            "xsuFf1XxXAGUezPSZi4zEdOkIsEOUvRSVMU5F1bWrkhq49NqJA6+qB9+8GaEP5p/\n" +
            "cgBUQvUIxGX7lEK3T0fEI2/J/4f240V0QbLL4mJsXNMYctcIz4+mzCKTv1v/Cylk\n" +
            "EJ4jg59Zi4r9oIuSQdkERygh1jpgEMJAjoHdbqwA7hG9VFewHxfJNFChqf046Skl\n" +
            "R5v+4j3PyC0YSkr2H+zt54kvybHprutK3LISusGD8QKBgQDvC95Odp/r6xznfe7g\n" +
            "KQ/EsR+SQyc917Cjfc0cEOkfzHSlm1WU1UxEUUTsSpe8tvwZPkJWrDO+FCrlUqd1\n" +
            "d3kS4N99AaSHD8q01rUk2c2Kaoey6DlKoBb3fJUTFGfKtam6DackxriGBB+eMZAc\n" +
            "vi1Tnin7/ObXMimo5LZtZdcpiwKBgQDBc5g4gA9nAUHsrEWOk5k9Ft83jLGwRw8v\n" +
            "pXiZzELMZQGQTf9L/YuhY9QEk6gu3njhW4v1+DbhMniLblcQ1rvpX4YezFT4Du1U\n" +
            "XVjHlxCyjAdNB0AzC6DasS20k+xKZw07KDKIITGnQnLBrefQ+bjdHKpve7MNWexX\n" +
            "XRKy1/1Z8QKBgQCHOyyZMMyJtylT4E9JqpEMEbOtl6XUe3enFdz5+qsXXR/ELBr/\n" +
            "JqeNongeQJiUnuQBF9KJm8NtzZTyxI6NingI8QQdgNdlvM5M/YXeggSgQGHiGTOH\n" +
            "/wbfHTBsacfJynlpEp4y1OTAlAabBKjlScT0n+5aapjgtrUQocp+GvXcvwKBgQCa\n" +
            "xvTMh9FK9ZucU70XNQqO8QTJOh1Uz5Xb5kWWr/Hl/Q6COZWAZCzahLe2rbkLPt5y\n" +
            "WD+kHeMyzKHb1P1+MICKWO5DJ/L3wWGrdUA5+KjYYebZf2qjLLOXJdlOuGd+o/LX\n" +
            "GPNNLVm/3A9a3NwzvAlnh67poYwBq6fHwmTaiKtg0QKBgAwurBRizSv/YgKuozWm\n" +
            "uecDhQgPR8S58H+czzhzkUmBVnz4b9FBUM5Iweda17NYdu2fSLwb/2aZn6L5CcjK\n" +
            "vr+2JsnLyXGQj7WCoJ3gh1YVo3X/d/L85bkgUIlQGrhi1cRy1Rsi+112mTgi/PRW\n" +
            "vW29ypivZnCZIC5WEzEzIGcn\n" +
            "-----END PRIVATE KEY-----\n"
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
    val tenancyOcid = "ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q"
    val userOcid = "ocid1.user.oc1..aaaaaaaaxjkkfjqxdqk7ldfjrxjmacmbi7sci73rbfiwpioehikavpbtqx5q"
    val fingerprint = "bb:d4:4b:0c:c8:3a:49:15:7f:87:55:d5:2b:7e:dd:bc"
    val restApi = "/20180608/keys?compartmentId=ocid1.tenancy.oc1..aaaaaaaaiijfupfvsqwqwgupzdy5yclfzcccmie4ktp2wlgslftv5j7xpk6q&sortBy=TIMECREATED&sortOrder=DESC"
    val host = "ens3g6m3aabyo-management.kms.eu-frankfurt-1.oraclecloud.com"

    val connection = signRequest(tenancyOcid, userOcid, fingerprint, restApi, host)
    println(connection)
}

