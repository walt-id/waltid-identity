package id.walt.commons.interop

object LspPotentialInterop {
  val POTENTIAL_ROOT_CA_CERT = "-----BEGIN CERTIFICATE-----${System.lineSeparator()}" +
          "MIIBZDCCAQmgAwIBAgII2x50/ui7K2wwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMTURPQyBST09UIENBMB4XDTI1MDUwNTA5NDAxNVoXDTI2MDUwNTA5NDAxNVowFzEVMBMGA1UEAwwMTURPQyBST09UIENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWP0sG+CkjItZ9KfM3sLF+rLGb8HYCfnlsIH/NWJjiXkTx57ryDLYfTU6QXYukVKHSq6MEebvQPqTJT1blZ/xeKM/MD0wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFMWhsBrivmW3c2DagA08if4Ki0cJMAoGCCqGSM49BAMCA0kAMEYCIQCierJhChwclbWNvUo0uqQRxDlIFHIGIjPuWP/Hq165YgIhALTywPN4vTXm0Z6MGNlvfbwWXdyoZ7D5XmkjvljXJfBt${System.lineSeparator()}" +
          "-----END CERTIFICATE-----${System.lineSeparator()}"
  val POTENTIAL_ISSUER_CERT = "-----BEGIN CERTIFICATE-----${System.lineSeparator()}" +
          "MIIBRzCB7qADAgECAgg57ch6mnj5KjAKBggqhkjOPQQDAjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwHhcNMjQwNTAyMTMxMzMwWhcNMjUwNTAyMTMxMzMwWjAbMRkwFwYDVQQDDBBNRE9DIFRlc3QgSXNzdWVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gaMgMB4wDAYDVR0TAQH/BAIwADAOBgNVHQ8BAf8EBAMCB4AwCgYIKoZIzj0EAwIDSAAwRQIhAI5wBBAA3ewqIwslhuzFn4rNFW9dkz2TY7xeImO7CraYAiAYhai1NzJ6abAiYg8HxcRdYpO4bu2Sej8E6CzFHK34Yw==${System.lineSeparator()}" +
      "-----END CERTIFICATE-----"
  val POTENTIAL_ISSUER_PUB = "-----BEGIN PUBLIC KEY-----${System.lineSeparator()}" +
          "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==${System.lineSeparator()}" +
          "-----END PUBLIC KEY-----${System.lineSeparator()}"
  val POTENTIAL_ISSUER_PRIV = "-----BEGIN PRIVATE KEY-----${System.lineSeparator()}" +
          "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAoniTdVyXlKP0x+rius1cGbYyg+hjf8CT88hH8SCwWFA==${System.lineSeparator()}" +
          "-----END PRIVATE KEY-----${System.lineSeparator()}"
  const val POTENTIAL_ISSUER_KEY_ID = "potential-lsp-issuer-key-01"
}
