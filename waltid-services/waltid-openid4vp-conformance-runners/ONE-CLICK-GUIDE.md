# Deprecated VCI Wallet Manual Flow

The former VCI wallet profile runner required opening a credential-offer URL and
manually completing all the browser flows. It has been replaced by the generated
wallet conformance matrix and Playwright automation.

Use [docs/VCI-WALLET.md](docs/VCI-WALLET.md) instead. Wallet API2 is a separate
Java process and must first start with the locally generated truststore:

```bash
# Terminal 1
./run-wallet-api2-conformance-local.sh
```

Leave that process running. In a second terminal, start the conformance run:

```bash
# Terminal 2
./run-wallet-conformance-local.sh
```

The conformance wrapper starts the local suite stack, drives supported
front-channel steps, writes matrix reports, removes its temporary Wallet API2
wallet, and returns a non-zero exit code for failed or blocked contexts.