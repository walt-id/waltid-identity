import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import { AvailableCredential } from '@/types/credentials';

const getOfferUrl = async (credentials: Array<AvailableCredential>, NEXT_PUBLIC_VC_REPO: string, NEXT_PUBLIC_ISSUER: string) => {
  const data = await fetch(`${NEXT_PUBLIC_ISSUER}/.well-known/openid-credential-issuer`).then(data => {
    return data.json();
  });
  const credential_configurations_supported = data.credential_configurations_supported;

  const payload = await Promise.all(credentials.map(async (c) => {
    const offer = { ...c.offer, id: uuidv4() };
    const mapping = await (await fetch(`${NEXT_PUBLIC_VC_REPO}/api/mapping/${c.id}`).then(data => {
      return data.json();
    }).catch(err => {
      return null;
    }));

    let payload: {
      'issuerDid': string,
      'issuerKey': { "type": "jwk", "jwk": string },
      credentialConfigurationId: string,
      credentialData: any,
      mapping?: any,
      selectiveDisclosure?: any
    } = {
      'issuerDid': 'did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiY1lIZjdrekcta2tvZkRaV1BQUVpKc3VLQU5YdUZ3UjViSkJKV0NCbkNhQSIsIngiOiJjZjU1b0h3WFhDZUJsR0pnYjFFS0dQdlBySGlWZlFZWlJCbVMzVG9CbDNVIiwieSI6ImFXMFAtVVI2WnhXaE9DVl9hYWkxT21iOHNQRmVsV1F6RUZQYjVXemo1cTAifQ',
      'issuerKey': { "type": "jwk", "jwk": "{\"kty\":\"EC\",\"d\":\"cexqfMJ6ZS9SX9_ogHxkXxUOBX-biKpqYRd6-QcDsHs\",\"crv\":\"P-256\",\"kid\":\"cYHf7kzG-kkofDZWPPQZJsuKANXuFwR5bJBJWCBnCaA\",\"x\":\"cf55oHwXXCeBlGJgb1EKGPvPrHiVfQYZRBmS3ToBl3U\",\"y\":\"aW0P-UR6ZxWhOCV_aai1Omb8sPFelWQzEFPb5Wzj5q0\"}" },
      credentialConfigurationId: Object.keys(credential_configurations_supported).find(key => key === c.id + "_jwt_vc_json") as string,
      credentialData: offer
    }

    if (c.selectedFormat === "SD-JWT + VCDM") {
      payload.credentialConfigurationId = Object.keys(credential_configurations_supported).find(key => key === c.id + "_vc+sd-jwt") as string;
      payload.selectiveDisclosure = {
        "fields": {
          "credentialSubject": {
            sd: false,
            children: {
              fields: {}
            }
          }
        }
      }
      for (const key in offer.credentialSubject) {
        if (typeof offer.credentialSubject[key] === 'string') {
          payload.selectiveDisclosure.fields.credentialSubject.children.fields[key] = {
            sd: true
          }
        }
      }
    }
    return mapping ? { ...payload, mapping } : payload;
  }));

  //TODO: throw error when credentials length is 0
  const issueUrl = NEXT_PUBLIC_ISSUER + `/openid4vc/${credentials.length === 1 && credentials[0].selectedFormat === "SD-JWT + VCDM" ? "sdjwt" : "jwt"}/${(payload.length > 1 ? 'issueBatch' : 'issue')}`;
  return axios.post(issueUrl, payload.length > 1 ? payload : payload[0]);
}

export { getOfferUrl };
