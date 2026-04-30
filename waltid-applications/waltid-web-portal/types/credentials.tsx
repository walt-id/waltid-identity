export type CredentialKind = 'w3c' | 'mdoc';

export type AvailableCredential = {
  id: string;
  title: string;
  kind?: CredentialKind;
  selectedFormat?: String;
  selectedDID?: String;
  offer: any;
};

/** Portal label for ISO mDoc issuance via `/openid4vc/mdoc/issue`. */
export const ISO_MDOC_CREDENTIAL_FORMAT = 'ISO mDoc (18013-5)';

/** VC repo titles whose JSON uses an ambiguous ISO mDL namespace but a distinct doc type (matches issuer `MDocTypes`). */
export const MDOC_DOC_TYPE_HINT_BY_TITLE: Record<string, string> = {
  'Google ID Card': 'com.google.wallet.idcard.1',
};

export const CredentialFormats = [
  'JWT + W3C VC',
  'SD-JWT + W3C VC',
  'SD-JWT + IETF SD-JWT VC',
  ISO_MDOC_CREDENTIAL_FORMAT,
];

/** Demo ES256 key + DS cert chain aligned with issuer API docs (`MdocDocs.mdlBaseIssuanceExample`). */
export const MDOC_ISSUANCE_DEFAULTS = {
  issuerKey: {
    type: 'jwk',
    jwk: {
      kty: 'EC',
      d: '-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s',
      crv: 'P-256',
      kid: 'sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0',
      x: 'Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U',
      y: '6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo',
    },
  },
  x5Chain: [
    '-----BEGIN CERTIFICATE-----\nMIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M\n-----END CERTIFICATE-----\n',
  ],
};

export function inferDocTypeFromMdocData(mdocData: unknown): string | undefined {
  if (
    mdocData == null ||
    typeof mdocData !== 'object' ||
    Array.isArray(mdocData)
  ) {
    return undefined;
  }
  const namespaces = mdocData as Record<string, unknown>;
  if ('eu.europa.ec.eudi.pid.1' in namespaces) return 'eu.europa.ec.eudi.pid.1';
  if ('eu.europa.ec.av.1' in namespaces) return 'eu.europa.ec.av.1';
  if ('at.gv.id-austria.2023' in namespaces) return 'at.gv.id-austria.2023.iso';
  if ('org.iso.23220.1' in namespaces) return 'org.iso.23220.photoid.1';
  if ('org.iso.18013.5.1' in namespaces) return 'org.iso.18013.5.1.mDL';
  return undefined;
}

function metadataDocType(cfg: Record<string, unknown>): string | undefined {
  const v = cfg.doctype ?? cfg.doc_type ?? cfg.docType;
  return typeof v === 'string' ? v : undefined;
}

export function resolveMdocCredentialConfigurationId(
  supported: Record<string, Record<string, unknown>>,
  mdocData: Record<string, unknown>,
  explicitDocType?: string
): string | undefined {
  const docType = explicitDocType ?? inferDocTypeFromMdocData(mdocData);
  if (!docType) return undefined;
  for (const [configId, cfg] of Object.entries(supported)) {
    if (cfg.format === 'mso_mdoc' && metadataDocType(cfg) === docType) {
      return configId;
    }
  }
  return undefined;
}

export function issuanceCombinationAllowed(
  credentialsToIssue: AvailableCredential[]
): boolean {
  if (credentialsToIssue.length === 0) return false;
  const mdocCredentials = credentialsToIssue.filter((c) => c.kind === 'mdoc');
  if (mdocCredentials.length > 0) {
    return credentialsToIssue.length === 1 && mdocCredentials.length === 1;
  }
  if (credentialsToIssue.length === 1) return true;
  const formats = credentialsToIssue.map((c) =>
    String(c.selectedFormat ?? CredentialFormats[0])
  );
  const allSdJwt = formats.every((f) => f.startsWith('SD-JWT'));
  const allJwtW3c = formats.every((f) => f === 'JWT + W3C VC');
  return allSdJwt || allJwtW3c;
}

// Get Value
export function mapFormat(format: string): string {
  switch (format) {
    case 'JWT + W3C VC':
    case 'SD-JWT + W3C VC':
      return 'jwt_vc_json';
    case 'SD-JWT + IETF SD-JWT VC':
      return 'vc+sd-jwt';
    case ISO_MDOC_CREDENTIAL_FORMAT:
      return 'mso_mdoc';
    default:
      throw new Error(`Unsupported format: ${format}`);
  }
}

export const DIDMethods = [
  'did:jwk',
  'did:key',
  'did:ebsi',
  'did:web',
  'did:cheqd',
]

export const DIDMethodsConfig = {
  'did:key': {
    'issuerDid': 'did:key:z6MkmANLkdcnbriWeVaqdfrA3MmtXoVPNu98tww6xDeyVnyF',
    'issuerKey': {
      "type": "jwk",
      "jwk": {
        "kty": "OKP",
        "d": "fbpXmCh4KkcVIGOnkcjHvWAcaUPvvkBvgMFPE4nAgvA",
        "crv": "Ed25519",
        "kid": "DJ3X4BZqk4GJsMGZL44hEZrlEy9scbMcSA_QuUi3tGs",
        "x": "Y64Ns3aRo6KQgJTtCZKFA78uYvslBcIrOk7xaS1PIZI"
      }
    }
  },
  'did:ebsi': {
    'issuerDid': 'did:ebsi:zf39qHTXaLrr6iy3tQhT3UZ',
    'issuerKey': {
      "type": "jwk",
      "jwk": {
        "kty": "EC",
        "x": "SgfOvOk1TL5yiXhK5Nq7OwKfn_RUkDizlIhAf8qd2wE",
        "y": "u_y5JZOsw3SrnNPydzJkoaiqb8raSdCNE_nPovt1fNI",
        "crv": "P-256",
        "d": "UqSi2MbJmPczfRmwRDeOJrdivoEy-qk4OEDjFwJYlUI"
      }
    }
  },
  'did:jwk': {
    'issuerDid': 'did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiM1lOZDlGbng5Smx5UFZZd2dXRkUzN0UzR3dJMGVHbENLOHdGbFd4R2ZwTSIsIngiOiJGb3ZZMjFMQUFPVGxnLW0tTmVLV2haRUw1YUZyblIwdWNKakQ1VEtwR3VnIiwieSI6IkNyRkpmR1RkUDI5SkpjY3BRWHV5TU8zb2h0enJUcVB6QlBCSVRZajBvZ0EifQ',
    'issuerKey': {
      "type": "jwk",
      "jwk": {
        "kty": "EC",
        "d": "8jH4vwtvCw6tcBzdxQ6V7FY2L215lBGm-x3flgENx4Y",
        "crv": "P-256",
        "kid": "3YNd9Fnx9JlyPVYwgWFE37E3GwI0eGlCK8wFlWxGfpM",
        "x": "FovY21LAAOTlg-m-NeKWhZEL5aFrnR0ucJjD5TKpGug",
        "y": "CrFJfGTdP29JJccpQXuyMO3ohtzrTqPzBPBITYj0ogA"
      }
    }
  },
  'did:web': {
    'issuerDid': 'did:web:wallet.demo.walt.id:wallet-api:registry:portal',
    'issuerKey': {
      "type": "jwk",
      "jwk": {
        "kty": "EC",
        "d": "6rVNEWMQzVdPgin7ER_ptWlSnkozGwOWYlSDcQHMRZw",
        "crv": "secp256k1",
        "kid": "hxKurYDplZbY3PgDdXNtz1CwaG6CJ9dyslsyJY11rQs",
        "x": "fTlAxVt3AHGX4LfqStS8MRIWjBrNYbcdHwW95FKZTiU",
        "y": "SqeitQcdT7lZg4z2JgCCD8JabsZvE_6W8dbMlVNxXeo"
      }
    }
  },
  'did:cheqd': {
    'issuerDid': 'did:cheqd:testnet:16047c9a-8f6f-4258-b35e-73098c6981e0',
    'issuerKey': {
      "type": "jwk",
      "jwk": {
        "kty": "OKP",
        "d": "YqOrL8iTCxeoVAFAxXC-CVxX7-RfOtVggl55wwP3wg0",
        "crv": "Ed25519",
        "kid": "AMSWqtZTHp2PosnSCeFJ10rES2Vd6IzNx8UV3oZuKGw",
        "x": "3oKRKU2W66W8DycLCQ26WCv8scVBGI-H3PvTIvZ0Fjw"
      }
    }
  }
}

export const AuthenticationMethods = [
  'PRE_AUTHORIZED',
  'PWD',
  'ID_TOKEN',
  'VP_TOKEN',
  'NONE',
]

export const VpProfiles = [
  'EBSIV3',
  'DEFAULT',
  'ISO_18013_7_MDOC',
]
