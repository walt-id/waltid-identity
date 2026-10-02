# Released session-store compatibility fixture

`ReleasedSessionStore.class.bin` was compiled once from the adjacent Java source against
`id.walt.protocols:waltid-openid4vc-wallet-jvm:1.1.0` with `javac --release 11 -proc:none`.
The test loads that unchanged bytecode against the candidate library. Do not regenerate
it against candidate classes: doing so would conceal changes to the released interface.

Released JAR SHA-256: `ccc08776788361f36cf820d8ef95735efb5311a1d1a9d6f2bc5557d65bae18b9`.
Fixture SHA-256: `0345c2cdc940da91102d87b9dca8390382caf32c0ae2a03be1527ec360c56868`.
