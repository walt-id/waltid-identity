import axios from "axios";
import { v4 as uuidv4 } from "uuid";
import { AvailableCredential, CredentialFormats, DIDMethods, DIDMethodsConfig } from "@/types/credentials";

const getOfferUrl = async (
  credentials: Array<AvailableCredential>,
  NEXT_PUBLIC_VC_REPO: string,
  NEXT_PUBLIC_ISSUER: string,
  authenticationMethod?: string,
  vpRequestValue?: string,
  vpProfile?: string
) => {
  const data = await fetch(
    `${NEXT_PUBLIC_ISSUER}/draft13/.well-known/openid-credential-issuer`
  ).then((data) => {
    return data.json();
  });
  const credential_configurations_supported =
    data.credential_configurations_supported;

  const payload = await Promise.all(
    credentials.map(async (c) => {
      c = {
        ...c,
        selectedFormat: c.selectedFormat ?? CredentialFormats[0],
        selectedDID: c.selectedDID ?? DIDMethods[0],
      };

      const offer = { ...c.offer, id: uuidv4() };
      let payload: {
        issuerDid: string;
        issuerKey: { type: string; jwk: object };
        credentialConfigurationId: string;
        credentialData: any;
        mapping?: any;
        selectiveDisclosure?: any;
        authenticationMethod?: string;
        vpRequestValue?: string;
        vpProfile?: string;
      } = {
        issuerDid:
          DIDMethodsConfig[c.selectedDID as keyof typeof DIDMethodsConfig]
            .issuerDid,
        issuerKey:
          DIDMethodsConfig[c.selectedDID as keyof typeof DIDMethodsConfig]
            .issuerKey,
        credentialConfigurationId: Object.keys(
          credential_configurations_supported
        ).find((key) => key === c.id + '_jwt_vc_json') as string,
        credentialData: offer,
      };

      if (c.selectedFormat === 'SD-JWT + IETF SD-JWT VC') {
        payload.mapping = {
          id: '<uuid>',
          iat: '<timestamp-seconds>',
          nbf: '<timestamp-seconds>',
          exp: '<timestamp-in-seconds:365d>',
        };

        // Hack - remove the following fields as they used for w3c only
        delete payload.credentialData['@context'];
        delete payload.credentialData['type'];
        delete payload.credentialData['validFrom'];
        delete payload.credentialData['expirationDate'];
        delete payload.credentialData['issuanceDate'];
        delete payload.credentialData['issued'];
        delete payload.credentialData['issuer'];

        payload.credentialConfigurationId = Object.keys(
          credential_configurations_supported
        ).find((key) => key === c.id + '_vc+sd-jwt') as string;
        payload.selectiveDisclosure = { fields: {} };
        for (const key in offer.credentialSubject) {
          if (typeof offer.credentialSubject[key] === 'string') {
            payload.selectiveDisclosure.fields[key] = {
              sd: true,
            };
          }
        }
      } else {
        payload.mapping = await await fetch(
          `${NEXT_PUBLIC_VC_REPO}/api/mapping/${c.id}`
        )
          .then((data) => {
            return data.json();
          })
          .catch((err) => {
            return null;
          });

        if (c.selectedFormat === 'SD-JWT + W3C VC') {
          payload.selectiveDisclosure = {
            fields: {
              credentialSubject: {
                sd: false,
                children: {
                  fields: {},
                },
              },
            },
          };
          for (const key in offer.credentialSubject) {
            if (typeof offer.credentialSubject[key] === 'string' || typeof offer.credentialSubject[key] === 'boolean') {
              payload.selectiveDisclosure.fields.credentialSubject.children.fields[
                key
              ] = {
                sd: true,
              };
            }
          }
        }
      }

      if (authenticationMethod) {
        payload.authenticationMethod = authenticationMethod;
      }
      if (vpRequestValue) {
        payload.vpRequestValue = vpRequestValue;
      }
      if (vpProfile) {
        payload.vpProfile = vpProfile;
      }

      if (c.selectedFormat === 'SD-JWT + IETF SD-JWT VC') {
        const { credentialSubject, ...restOfCredentialData } =
          payload.credentialData; // Destructure credentialSubject and the rest
        return {
          ...payload, // Keep the rest of the payload unchanged
          credentialData: {
            ...restOfCredentialData, // Spread other fields from credentialData (e.g., id, issuer)
            ...credentialSubject, // Spread fields from credentialSubject to the top level of credentialData
          },
        };
      } else {
        return payload;
      }
    })
  );

  const issueUrl =
    NEXT_PUBLIC_ISSUER +
    `/openid4vc/${credentials[0].selectedFormat === 'SD-JWT + W3C VC' || credentials[0].selectedFormat === 'SD-JWT + IETF SD-JWT VC' ? 'sdjwt' : 'jwt'}/${payload.length > 1 ? 'issueBatch' : 'issue'}`;
  return axios.post(issueUrl, payload.length > 1 ? payload : payload[0]);
};

export { getOfferUrl };
