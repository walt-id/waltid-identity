import {useContext, useEffect, useState} from "react";
import WaltIcon from "@/components/walt/logo/WaltIcon";
import Button from "@/components/walt/button/Button";
import {CredentialsContext, EnvContext} from "@/pages/_app";
import Icon from "@/components/walt/logo/Icon";
import {useRouter} from "next/router";
import QRCode from "react-qr-code";
import axios from "axios";
import {sendToWebWallet} from "@/utils/sendToWebWallet";
import nextConfig from "@/next.config";
import BackButton from "@/components/walt/button/BackButton";
import {
  AvailableCredential,
  CredentialFormats,
  ISO_MDOC_CREDENTIAL_FORMAT,
  inferDocTypeFromMdocData,
  mapFormat,
} from "@/types/credentials";
import {checkVerificationResult, getStateFromUrl} from "@/utils/checkVerificationResult";

const BUTTON_COPY_TEXT_DEFAULT = 'Copy offer URL';
const BUTTON_COPY_TEXT_COPIED = 'Copied';

export default function Verification() {
  const env = useContext(EnvContext);
  const [AvailableCredentials] = useContext(CredentialsContext);
  const router = useRouter();

  const [verifyURL, setverifyURL] = useState('');
  const [loading, setLoading] = useState(true);
  const [copyText, setCopyText] = useState(BUTTON_COPY_TEXT_DEFAULT);

  function handleCancel() {
    router.push('/');
  }

  useEffect(() => {
    const getverifyURL = async () => {
      const vps = router.query.vps?.toString().split(',') ?? [];
      const ids = router.query.ids?.toString().split(',') ?? [];
      const formatParam =
        router.query.format?.toString() ?? CredentialFormats[0];
      let formatParts = formatParam
        .split(',')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);
      if (formatParts.length === 0) {
        formatParts = [CredentialFormats[0]];
      }

      const credentials = ids
        .map((id) =>
          AvailableCredentials.find((c) => c.id.toString() === id.toString())
        )
        .filter((c): c is AvailableCredential => Boolean(c));

      const portalLabelForIndex = (index: number): string => {
        const cred = credentials[index];
        if (cred?.kind === 'mdoc') return ISO_MDOC_CREDENTIAL_FORMAT;
        return (
          formatParts[index] ??
          formatParts[0] ??
          CredentialFormats[0]
        );
      };

      const mappedFormats = credentials.map((_, index) =>
        mapFormat(portalLabelForIndex(index))
      );

      const standardVersion = 'draft13'; // ['draft13', 'draft11']
      const issuerMetadataConfigSelector = {
        draft13: 'credential_configurations_supported',
        draft11: 'credentials_supported',
      };

      const issuerMetadata = await axios.get(
        `${env.NEXT_PUBLIC_ISSUER ? env.NEXT_PUBLIC_ISSUER : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_ISSUER}/${standardVersion}/.well-known/openid-credential-issuer`
      );

      const request_credentials = credentials.map((credential, index) => {
        const mappedFormat = mappedFormats[index];

        if (credential.kind === 'mdoc') {
          const raw =
            credential.offer?.mdocData ??
            (credential.offer as Record<string, unknown>);
          const docType =
            (typeof credential.offer?.docType === 'string' &&
              credential.offer.docType) ||
            inferDocTypeFromMdocData(raw as Record<string, unknown>);
          if (!docType) {
            throw new Error(
              'Could not infer ISO mDoc document type from credential namespaces.'
            );
          }
          return {
            format: 'mso_mdoc',
            doc_type: docType,
            id: credential.id.replace(/^mdoc:/, '').replace(/\s+/g, '_'),
          };
        }
        if (mappedFormat === 'vc+sd-jwt') {
          const url =
            issuerMetadata.data[
              issuerMetadataConfigSelector[
                standardVersion as keyof typeof issuerMetadataConfigSelector
              ]
            ][
              `${
                credential.offer.type[credential.offer.type.length - 1]
              }_vc+sd-jwt`
            ].vct;
          return {
            vct: url,
            format: mappedFormat,
          };
        }
        return {
          type: credential.offer.type[credential.offer.type.length - 1],
          format: mappedFormat,
        };
      });

      const requestBody: {
        request_credentials: typeof request_credentials;
        vc_policies?: unknown;
      } = {
        request_credentials: request_credentials,
      };

      const needsVcPolicies = mappedFormats.some(
        (mf) => mf !== 'vc+sd-jwt' && mf !== 'mso_mdoc'
      );

      if (needsVcPolicies) {
        requestBody.vc_policies = vps.map((vp) => {
          if (vp.includes('=')) {
            return {
              policy: vp.split('=')[0],
              args: vp.split('=')[1],
            };
          } else {
            return vp;
          }
        });
      }

      const verifyHeaders: Record<string, string> = {
        successRedirectUri: `${window.location.origin}/success/$id`,
        errorRedirectUri: `${window.location.origin}/success/$id`,
      };
      if (mappedFormats.some((mf) => mf === 'mso_mdoc')) {
        verifyHeaders.openId4VPProfile = 'ISO_18013_7_MDOC';
      }

      const response = await axios.post(
        `${env.NEXT_PUBLIC_VERIFIER ? env.NEXT_PUBLIC_VERIFIER : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER}/openid4vc/verify`,
        requestBody,
        {
          headers: verifyHeaders,
        }
      );
      setverifyURL(response.data);
      setLoading(false);

      const state = getStateFromUrl(response.data);
      if (state) {
        checkVerificationResult(
          env.NEXT_PUBLIC_VERIFIER
            ? env.NEXT_PUBLIC_VERIFIER
            : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER,
          state
        ).then((result) => {
          if (result) {
            router.push(`/success/${state}`);
          }
        });
      }
    };
    if (!router.isReady) return;
    getverifyURL();
  }, [router.isReady, router.query, AvailableCredentials, env, router]);

  async function copyCurrentURLToClipboard() {
    navigator.clipboard.writeText(verifyURL).then(
      function () {
        setCopyText(BUTTON_COPY_TEXT_COPIED);
        setTimeout(() => {
          setCopyText(BUTTON_COPY_TEXT_DEFAULT);
        }, 3000);
      },
      function (err) {
        console.error('Could not copy text: ', err);
      }
    );
  }

  function openWebWallet() {
    sendToWebWallet(
      env.NEXT_PUBLIC_WALLET
        ? env.NEXT_PUBLIC_WALLET
        : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_WALLET,
      'api/siop/initiatePresentation',
      verifyURL
    );
  }

  return (
    <div className="flex flex-col justify-center items-center bg-gray-50">
      <div
        className="my-5 flex flex-row justify-center cursor-pointer"
        onClick={() => router.push('/')}
      >
        <Icon height={35} width={35} />
      </div>
      <div className="relative w-10/12 sm:w-7/12 lg:w-5/12 text-center shadow-2xl rounded-lg pt-8 pb-8 px-10 bg-white">
        <BackButton />
        <h1 className="text-xl sm:text-2xl lg:text-3xl text-gray-900 text-center font-bold mt-5">
          Scan to Verify
        </h1>
        <div className="flex justify-center">
          {loading ? (
            <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-gray-900 my-10"></div>
          ) : (
            <QRCode
              className="h-full max-h-[220px] my-10"
              value={verifyURL}
              viewBox={'0 0 256 256'}
            />
          )}
        </div>
        <div className="sm:flex flex-row gap-5 justify-center">
          <Button style="link" onClick={copyCurrentURLToClipboard}>
            {copyText}
          </Button>
          <Button onClick={openWebWallet} style="button">
            Open Web Wallet
          </Button>
        </div>
        <div className="flex flex-col items-center mt-12">
          <div className="flex flex-row gap-2 items-center content-center text-sm text-center text-gray-500">
            <p className="">Secured by walt.id</p>
            <WaltIcon height={15} width={15} type="gray" />
          </div>
        </div>
      </div>
    </div>
  );
}
