# Pinned ITB test configuration

[wallet-catalogue.json](wallet-catalogue.json) records the observed ITB inventory date, public suite and case identifiers, and the prerequisites for each selected wallet case. VP-011/012/013 pin their invocation schemes. The [wallet case ledger](../../../../docs/ITB-WALLET.md#wallet-case-ledger) records runner selection, the last observed results and what to retry.

`pidissuerca02-eu.pem` is the public **PID Issuer CA 02** certificate from the [official EUDI Android reference wallet](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/blob/5080ae099c59c38cbd8092a7b511efce31648de2/resources-logic/src/main/res/raw/pidissuerca02_eu.pem) (Git blob `afcd0cdf3672f9c3bbf4b4adccfd4386594b052f`).

SHA-256 certificate fingerprint:

```text
3B:0A:22:3E:87:48:F3:C4:22:17:29:8A:D9:05:EC:F1:A1:04:E7:3C:D2:C5:4F:F2:1D:A1:6F:DA:80:70:B2:97
```

The deployed ITB verifier certificate chained to this independently retrieved CA during VP-002 on 2026-09-21. The runner pins it only for the ephemeral ITB test wallet; it does not modify application defaults or system trust. Do not automatically import a request's `x5c` chain as trusted. `ITB_X509_TRUST_ANCHORS` can explicitly replace this fixture with a PEM file when the operator changes the test environment.
