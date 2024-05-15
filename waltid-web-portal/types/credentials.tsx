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
    'issuerDid': '',
    'issuerKey': { "type": "", "jwk": "" }
  },
  'did:ebsi': {
    'issuerDid': 'did:ebsi:zf39qHTXaLrr6iy3tQhT3UZ',
    'issuerKey': { "type": "jwk", "jwk": "{\"kty\":\"EC\",\"x\":\"SgfOvOk1TL5yiXhK5Nq7OwKfn_RUkDizlIhAf8qd2wE\",\"y\":\"u_y5JZOsw3SrnNPydzJkoaiqb8raSdCNE_nPovt1fNI\",\"crv\":\"P-256\",\"d\":\"UqSi2MbJmPczfRmwRDeOJrdivoEy-qk4OEDjFwJYlUI\"}" }
  },
  'did:jwk': {
    'issuerDid': '',
    'issuerKey': { "type": "", "jwk": "" }
  },
  'did:web': {
    'issuerDid': '',
    'issuerKey': { "type": "", "jwk": "" }
  },
  'did:cheqd': {
    'issuerDid': '',
    'issuerKey': { "type": "", "jwk": "" }
  }
}