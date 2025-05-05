# walt.id Credential libraries

- Digital Credentials (**waltid-digital-credentials**)
  - Wraps waltid credential libraries:
      - W3C Verifiable Credentials (**waltid-w3c-credentials**)
      - Mobile Documents or mdocs (ISO/IEC 18013 and ISO/IEC 23220 series) (**waltid-mdoc-credentials**)
      - IETF SD-JWT VC (**waltid-sdjwt**)
        - provides SD-JWT functionality for SD-JWT VC, and also other credentials (e.g. W3C Verifiable Credentials)
  - Supports parsing any kind of above credential a unified structure.

Other credential handling:
- waltid-verification-policies
  - Run a variety of verification policies on these credentials.
- waltid-dif-definitions-parser
  - Match credentials against a Presentation Definition
- waltid-dcql-query
  - Query for credentials with a DCQL query
