import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import { AvailableCredential } from '@/types/credentials';

const getOfferUrl = async (credentials: Array<AvailableCredential>, NEXT_PUBLIC_VC_REPO: string, NEXT_PUBLIC_ISSUER: string) => {
  const payload = await Promise.all(credentials.map(async (c) => {
    const offer = { ...c.offer, id: uuidv4() };
    const mapping = await (await fetch(`${NEXT_PUBLIC_VC_REPO}/api/mapping/${c.id}`).then(data => {
      return data.json();
    }).catch(err => {
      return null;
    }));
    let payload: {
      'issuerDid': string,
      'issuanceKey': { "type": "local", "jwk": string },
      vc: any,
      mapping?: any,
      selectiveDisclosure?: any
    } = {
      'issuerDid': 'did:jwk:eyJrdHkiOiJPS1AiLCJjcnYiOiJFZDI1NTE5Iiwia2lkIjoiQ0ZRLU5yYTV5bnlCc2Z4d3k3YU5mOGR1QUVVQ01sTUlyUklyRGc2REl5NCIsIngiOiJoNW5idzZYOUptSTBCdnVRNU0wSlhmek84czJlRWJQZFYyOXdzSFRMOXBrIn0',
      'issuanceKey': { "type": "local", "jwk": "{\"kty\":\"OKP\",\"d\":\"HIN9WcVCqhGvwZ8I47WeMtxGceSKpvaEnu5eXAoWyDo\",\"crv\":\"Ed25519\",\"kid\":\"CFQ-Nra5ynyBsfxwy7aNf8duAEUCMlMIrRIrDg6DIy4\",\"x\":\"h5nbw6X9JmI0BvuQ5M0JXfzO8s2eEbPdV29wsHTL9pk\"}" },
      vc: offer
    }

    if (c.selectedFormat === "SD-JWT + VCDM") {
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