sequenceDiagram
  autonumber
  participant "Mobile Wallet (Apple / DC API)" as WAL
  participant "RP Web App (Browser)" as RP
  participant "Verifier2 Backend" as BE
  participant "waltid-18013-7-verifier (lib)" as LIB

  RP->>BE: POST /annex-c/create
  note right of RP: {docType, requestedElements, policies, origin}
  BE-->>RP: { sessionId, expiresAt }

  RP->>BE: POST /annex-c/request
  note right of RP: {sessionId}
  BE->>LIB: buildRequest(...) -> { deviceRequestB64, encryptionInfoB64 }
  BE-->>RP: { protocol:"org.iso.mdoc", data:{deviceRequest,encryptionInfo}, meta:{sessionId,expiresAt} }

  RP->>WAL: navigator.credentials.get({protocol:"org.iso.mdoc", data:{deviceRequest,encryptionInfo}})
  WAL-->>RP: { response: b64url(cbor(EncryptedResponse)) }

  RP->>BE: POST /annex-c/response
  note right of RP: {sessionId, response}
  BE-->>RP: { status:"received" } (async validation started)
  BE-->>BE: enqueue async validation job
  BE->>LIB: AnnexCResponseVerifier.decryptToDeviceResponse(encryptedResponseB64, encryptionInfoB64, origin, recipientPrivateKey)
  LIB-->>BE: deviceResponseCborBytes

  RP->>BE: GET /annex-c/info?sessionId=...
  BE-->>RP: { status, policyResults, ... }
