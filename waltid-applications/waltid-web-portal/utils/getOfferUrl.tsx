import axios from "axios";
import {v4 as uuidv4} from "uuid";
import {
  AvailableCredential,
  CredentialFormats,
  DIDMethods,
  DIDMethodsConfig,
  ISO_MDOC_CREDENTIAL_FORMAT,
  MDOC_ISSUANCE_DEFAULTS,
  resolveMdocCredentialConfigurationId,
} from "@/types/credentials";

const getOfferUrl = async (
  credentials: Array<AvailableCredential>,
  NEXT_PUBLIC_VC_REPO: string,
  NEXT_PUBLIC_ISSUER: string,
  authenticationMethod?: string,
  vpRequestValue?: string,
  vpProfile?: string
) => {
  if (!credentials?.length) {
    throw new Error('No credentials selected for issuance.');
  }

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
        selectedFormat:
          c.selectedFormat ??
          (c.kind === 'mdoc'
            ? ISO_MDOC_CREDENTIAL_FORMAT
            : CredentialFormats[0]),
        selectedDID: c.selectedDID ?? DIDMethods[0],
      };

      if (c.kind === 'mdoc') {
        const mdocData =
          c.offer?.mdocData ?? (c.offer as Record<string, unknown>);
        const explicitDocType =
          typeof c.offer?.docType === 'string' ? c.offer.docType : undefined;
        const credentialConfigurationId = resolveMdocCredentialConfigurationId(
          credential_configurations_supported,
          mdocData as Record<string, unknown>,
          explicitDocType
        );
        if (!credentialConfigurationId) {
          throw new Error(
            "This issuer does not advertise a matching mDoc configuration for the credential namespaces in the VC repository data."
          );
        }
        const mdocPayload: {
          issuerKey: typeof MDOC_ISSUANCE_DEFAULTS.issuerKey;
          credentialConfigurationId: string;
          mdocData: Record<string, unknown>;
          x5Chain: string[];
          authenticationMethod?: string;
          vpRequestValue?: string;
          vpProfile?: string;
        } = {
          issuerKey: MDOC_ISSUANCE_DEFAULTS.issuerKey,
          credentialConfigurationId,
          mdocData: mdocData as Record<string, unknown>,
          x5Chain: MDOC_ISSUANCE_DEFAULTS.x5Chain,
        };
        if (authenticationMethod) {
          mdocPayload.authenticationMethod = authenticationMethod;
        }
        if (vpRequestValue) {
          mdocPayload.vpRequestValue = vpRequestValue;
        }
        if (vpProfile) {
          mdocPayload.vpProfile = vpProfile;
        }
        return mdocPayload;
      }

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

  if (!payload.length) {
    throw new Error('Issuance payload is empty.');
  }

  const first = credentials[0];
  const sel = String(
    first.selectedFormat ??
      (first.kind === 'mdoc'
        ? ISO_MDOC_CREDENTIAL_FORMAT
        : CredentialFormats[0])
  );
  const pathSegment =
    first.kind === 'mdoc' || sel === ISO_MDOC_CREDENTIAL_FORMAT
      ? 'mdoc'
      : sel === 'SD-JWT + W3C VC' || sel === 'SD-JWT + IETF SD-JWT VC'
        ? 'sdjwt'
        : 'jwt';
  const batchAllowed =
    payload.length > 1 && pathSegment !== 'mdoc';
  const issueUrl =
    NEXT_PUBLIC_ISSUER +
    `/openid4vc/${pathSegment}/${batchAllowed ? 'issueBatch' : 'issue'}`;
  return axios.post(
    issueUrl,
    batchAllowed ? payload : payload[0]
  );
};

export { getOfferUrl };
