package id.walt.certificate

object TestKeys {
    val opensslHexFormat = HexFormat {
        upperCase = true
        bytes.byteSeparator = ":"
    }

    val rsa4096privateKeyPem = """
        -----BEGIN PRIVATE KEY-----
        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
        VaosD6JIoEgsp+v+PZ7QkZCcvMlzbac=
        -----END PRIVATE KEY-----        
    """.trimIndent()

    val ed25519KeyPem = """
        -----BEGIN PRIVATE KEY-----
        MC4CAQAwBQYDK2VwBCIEIDx0vG4DAppkgdmhQ5mYoMc0qxYHZj6BxpzItcKCGMJS
        -----END PRIVATE KEY-----
        -----BEGIN PUBLIC KEY-----
        MCowBQYDK2VwAyEAlVlBjtMNTjEGrortpNnENfR2qtL7ttgciQdd/CZgtGg=
        -----END PUBLIC KEY-----
    """.trimIndent()

    val ecP256KeyPem = """
        -----BEGIN PRIVATE KEY-----
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgpCx4+BY+9c+2CRpO
        b1r1KiBXU2WwyN85svFYSaH9O8WhRANCAATj3KZxtouhG7C7t3wrAkdDY9W/ppM0
        7WfhnOH8Uz7oL2AYyKf49GR6yQsrD4WsMeZ/rdpF+aOA7Di8/nYwlJyY
        -----END PRIVATE KEY-----
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE49ymcbaLoRuwu7d8KwJHQ2PVv6aT
        NO1n4Zzh/FM+6C9gGMin+PRkeskLKw+FrDHmf63aRfmjgOw4vP52MJScmA==
        -----END PUBLIC KEY-----
    """.trimIndent()
}