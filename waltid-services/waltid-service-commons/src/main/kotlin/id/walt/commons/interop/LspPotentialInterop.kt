package id.walt.commons.interop

object LspPotentialInterop {
  val POTENTIAL_ROOT_CA_CERT = "-----BEGIN CERTIFICATE-----${System.lineSeparator()}" +
          "MIIBZTCCAQugAwIBAgII2x50/ui7K2wwCgYIKoZIzj0EAwIwFzEVMBMGA1UEAwwMTURPQyBST09UIENBMCAXDTI1MDUxNDE0MDI1M1oYDzIwNzUwNTAyMTQwMjUzWjAXMRUwEwYDVQQDDAxNRE9DIFJPT1QgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARY/Swb4KSMi1n0p8zewsX6ssZvwdgJ+eWwgf81YmOJeRPHnuvIMth9NTpBdi6RUodKrowR5u9A+pMlPVuVn/F4oz8wPTAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUxaGwGuK+ZbdzYNqADTyJ/gqLRwkwCgYIKoZIzj0EAwIDSAAwRQIhAOEYhbDYF/1kgDgy4anwZfoULmwt4vt08U6EU2AjXI09AiACCM7m3FnO7bc+xYQRT+WBkZXe/Om4bVmlIK+av+SkCA==${System.lineSeparator()}" +
          "-----END CERTIFICATE-----${System.lineSeparator()}"
  val POTENTIAL_ISSUER_CERT = "-----BEGIN CERTIFICATE-----${System.lineSeparator()}" +
            "MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB${System.lineSeparator()}" +
      "-----END CERTIFICATE-----"
  val POTENTIAL_ISSUER_PUB = "-----BEGIN PUBLIC KEY-----${System.lineSeparator()}" +
          "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==${System.lineSeparator()}" +
          "-----END PUBLIC KEY-----${System.lineSeparator()}"
  val POTENTIAL_ISSUER_PRIV = "-----BEGIN PRIVATE KEY-----${System.lineSeparator()}" +
          "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAoniTdVyXlKP0x+rius1cGbYyg+hjf8CT88hH8SCwWFA==${System.lineSeparator()}" +
          "-----END PRIVATE KEY-----${System.lineSeparator()}"
  const val POTENTIAL_ISSUER_KEY_ID = "potential-lsp-issuer-key-01"
}
