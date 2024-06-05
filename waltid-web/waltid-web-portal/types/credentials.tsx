export type AvailableCredential = {
  id: string;
  title: string;
  selectedFormat?: String;
  selectedDID?: String;
  offer: any;
};

export const CredentialFormats = [
  'JWT + VCDM',
  'SD-JWT + VCDM',
  // 'Data Integrity/JSON-LD+ VCDM',
  // 'mdoc / mdl (IEC/ISO 18013-5) ',
];

export const DIDMethods = [
  'did:key',
  'did:ebsi',
  'did:jwk',
  'did:web',
  'did:cheqd',
]

export const DIDMethodsConfig = {
  'did:key': {
    'issuerDid': 'did:key:zWmu9RCrS3hBhGSyhKfNSywuTWFCMjMxJeKhMPt6iHCZU8jTdd3GFExQUWze9TEE877crWsxxaJ7w4UCUKBwfpSe5uW6aVhfXLTUXUSR4A22HtgtR3dMxn2kkWvrZGNo',
    'issuerKey': { "type": "jwk", "jwk": "{\"kty\":\"EC\",\"d\":\"_zAoNn2NdPkLmWfUm5HSARkiV34uHMDfNh3NfItBfKc\",\"crv\":\"P-256\",\"kid\":\"AczCHuog4j86KkSoZHopTOdyw2lnHPv_Pvvn_vrvZxQ\",\"x\":\"QRTTjcCikd89Kl-jA0Bt3I252wKpLquWa4ZJJySPpOY\",\"y\":\"eEmDUil7BMPDR9Raewfu77yNHOVzaktvCGntE1YGcvw\"}" }
  },
  'did:ebsi': {
    'issuerDid': 'did:ebsi:zf39qHTXaLrr6iy3tQhT3UZ',
    'issuerKey': { "type": "jwk", "jwk": "{\"kty\":\"EC\",\"x\":\"SgfOvOk1TL5yiXhK5Nq7OwKfn_RUkDizlIhAf8qd2wE\",\"y\":\"u_y5JZOsw3SrnNPydzJkoaiqb8raSdCNE_nPovt1fNI\",\"crv\":\"P-256\",\"d\":\"UqSi2MbJmPczfRmwRDeOJrdivoEy-qk4OEDjFwJYlUI\"}" }
  },
  'did:jwk': {
    'issuerDid': 'did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiM1lOZDlGbng5Smx5UFZZd2dXRkUzN0UzR3dJMGVHbENLOHdGbFd4R2ZwTSIsIngiOiJGb3ZZMjFMQUFPVGxnLW0tTmVLV2haRUw1YUZyblIwdWNKakQ1VEtwR3VnIiwieSI6IkNyRkpmR1RkUDI5SkpjY3BRWHV5TU8zb2h0enJUcVB6QlBCSVRZajBvZ0EifQ',
    'issuerKey': { "type": "jwk", "jwk": "{\"kty\":\"EC\",\"d\":\"8jH4vwtvCw6tcBzdxQ6V7FY2L215lBGm-x3flgENx4Y\",\"crv\":\"P-256\",\"kid\":\"3YNd9Fnx9JlyPVYwgWFE37E3GwI0eGlCK8wFlWxGfpM\",\"x\":\"FovY21LAAOTlg-m-NeKWhZEL5aFrnR0ucJjD5TKpGug\",\"y\":\"CrFJfGTdP29JJccpQXuyMO3ohtzrTqPzBPBITYj0ogA\"}" }
  },
  'did:web': {
    'issuerDid': 'did:web:wallet.walt.id:wallet-api:registry:portal',
    'issuerKey': { "type": "jwk", "jwk": "{\"kty\":\"EC\",\"d\":\"6rVNEWMQzVdPgin7ER_ptWlSnkozGwOWYlSDcQHMRZw\",\"crv\":\"secp256k1\",\"kid\":\"hxKurYDplZbY3PgDdXNtz1CwaG6CJ9dyslsyJY11rQs\",\"x\":\"fTlAxVt3AHGX4LfqStS8MRIWjBrNYbcdHwW95FKZTiU\",\"y\":\"SqeitQcdT7lZg4z2JgCCD8JabsZvE_6W8dbMlVNxXeo\"}" }
  },
  'did:cheqd': {
    'issuerDid': 'did:cheqd:testnet:16047c9a-8f6f-4258-b35e-73098c6981e0',
    'issuerKey': { "type": "jwk", "jwk": "{\"kty\":\"OKP\",\"d\":\"YqOrL8iTCxeoVAFAxXC-CVxX7-RfOtVggl55wwP3wg0\",\"crv\":\"Ed25519\",\"kid\":\"AMSWqtZTHp2PosnSCeFJ10rES2Vd6IzNx8UV3oZuKGw\",\"x\":\"3oKRKU2W66W8DycLCQ26WCv8scVBGI-H3PvTIvZ0Fjw\"}" }
  }
}