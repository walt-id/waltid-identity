import axios from "axios";
import {v4 as uuidv4} from "uuid";
import {AvailableCredential, CredentialFormats, DIDMethods, DIDMethodsConfig} from "@/types/credentials";

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

      if (c.selectedFormat === 'mDoc (ISO 18013-5)') {
        // mDoc format - uses mdocData instead of credentialData
        // Look for matching mDoc credential configuration
        payload.credentialConfigurationId = Object.keys(
          credential_configurations_supported
        ).find((key) => {
          const config = credential_configurations_supported[key];
          return config.format === 'mso_mdoc' &&
                 (key.toLowerCase().includes(c.id.toLowerCase().replace(/\s+/g, '_')) ||
                  key === 'org.iso.18013.5.1.mDL' ||
                  key === 'eu.europa.ec.eudi.pid_mso_mdoc' ||
                  key === 'eu.europa.ec.eudi.pid.1');
        }) as string;

        // For mDoc, the offer data should already be in namespace format
        // Convert credentialData to mdocData format
        const mdocData: any = {};
        const docType = credential_configurations_supported[payload.credentialConfigurationId]?.docType || 'org.iso.18013.5.1.mDL';

        // If offer already has namespace format, use it directly
        if (offer[docType]) {
          Object.assign(mdocData, offer);
        } else if (offer.credentialSubject) {
          // Convert W3C-style to mDoc namespace format
          mdocData[docType] = { ...offer.credentialSubject };
        } else {
          // Use offer as-is for the namespace
          mdocData[docType] = { ...offer };
          delete mdocData[docType].id;
        }

        // Replace credentialData with mdocData
        delete (payload as any).credentialData;
        (payload as any).mdocData = mdocData;

        // Remove mapping for mDoc
        delete (payload as any).mapping;

      } else if (c.selectedFormat === 'DC+SD-JWT (EUDI)') {
        // DC+SD-JWT format for EUDI wallet
        payload.mapping = {
          iat: '<timestamp-seconds>',
          nbf: '<timestamp-seconds>',
          exp: '<timestamp-in-seconds:365d>',
        };

        // Remove W3C-specific fields
        delete payload.credentialData['@context'];
        delete payload.credentialData['type'];
        delete payload.credentialData['validFrom'];
        delete payload.credentialData['expirationDate'];
        delete payload.credentialData['issuanceDate'];
        delete payload.credentialData['issued'];
        delete payload.credentialData['issuer'];
        delete payload.credentialData['id'];

        // Find matching DC+SD-JWT credential configuration
        payload.credentialConfigurationId = Object.keys(
          credential_configurations_supported
        ).find((key) => {
          const config = credential_configurations_supported[key];
          return config.format === 'dc+sd-jwt' ||
                 key === 'urn:eudi:pid:1';
        }) as string;

        payload.selectiveDisclosure = { fields: {} };
        // Flatten credentialSubject for DC+SD-JWT
        const subjectData = offer.credentialSubject || offer;
        for (const key in subjectData) {
          if (typeof subjectData[key] === 'string' || typeof subjectData[key] === 'boolean') {
            payload.selectiveDisclosure.fields[key] = {
              sd: true,
            };
          }
        }
      } else if (c.selectedFormat === 'SD-JWT + IETF SD-JWT VC') {
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

      if (c.selectedFormat === 'SD-JWT + IETF SD-JWT VC' || c.selectedFormat === 'DC+SD-JWT (EUDI)') {
        const { credentialSubject, ...restOfCredentialData } =
          payload.credentialData; // Destructure credentialSubject and the rest
        return {
          ...payload, // Keep the rest of the payload unchanged
          credentialData: {
            ...restOfCredentialData, // Spread other fields from credentialData (e.g., id, issuer)
            ...credentialSubject, // Spread fields from credentialSubject to the top level of credentialData
          },
        };
      } else if (c.selectedFormat === 'mDoc (ISO 18013-5)') {
        // mDoc payload already has mdocData instead of credentialData
        return payload;
      } else {
        return payload;
      }
    })
  );

  // Determine the issue endpoint based on format
  const selectedFormat = credentials[0].selectedFormat;
  let issueEndpoint: string;

  if (selectedFormat === 'mDoc (ISO 18013-5)') {
    issueEndpoint = 'mdoc';
  } else if (selectedFormat === 'SD-JWT + W3C VC' || selectedFormat === 'SD-JWT + IETF SD-JWT VC' || selectedFormat === 'DC+SD-JWT (EUDI)') {
    issueEndpoint = 'sdjwt';
  } else {
    issueEndpoint = 'jwt';
  }

  const issueUrl =
    NEXT_PUBLIC_ISSUER +
    `/openid4vc/${issueEndpoint}/${payload.length > 1 ? 'issueBatch' : 'issue'}`;
  return axios.post(issueUrl, payload.length > 1 ? payload : payload[0]);
};

export { getOfferUrl };
