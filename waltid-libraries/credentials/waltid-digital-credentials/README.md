# waltid-digital-credentials

Credential abstraction layer for unified credential handling.

Differentiate between:
- W3C (W3C 1.1 DM, W3C 2 DM), W3C with selectively disclosable attributes (which could also be W3C 1.1 DM, W3C 2 DM)
    - detect signatures: JOSE, specifically JWT, and SD-JWT; also: COSE, DataIntegrityProof (ECDSA, EdDSA, ECDSA-SD, BBS), or unsigned
- SD-JWT VC (which could follow SD-JWT VC (= no) or SD-JWT VCDM (= w3c similar data model)
- Mdocs (COSE encoded with Base64Url or Hex)

Detection function where any of the above mentioned can be inputted (e.g. unsigned SD-JWT VC or SD-JWT VC DM, or a ECDSA-DataIntegrityProof signed W3C 2.0), and it parses to the respective credential type (W3C, SD-JWT VC, Mdocs), subtype (W3C: W3C 1.1, W3C 2; SD-JWT VC: SD-JWT VC, SD-JWT VCDM; Mdocs) and signature (Unsigned, JWT, SD-JWT (JWT with disclosures), DataIntegrityProof, COSE)

