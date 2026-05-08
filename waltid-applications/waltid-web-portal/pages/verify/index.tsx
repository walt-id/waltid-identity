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
import {CredentialFormats, mapFormat} from "@/types/credentials";
import {checkVerificationResult, getStateFromUrl} from "@/utils/checkVerificationResult";
import {isTransactionDataSupportedSelectedFormat} from "@/utils/transactionData";

const BUTTON_COPY_TEXT_DEFAULT = 'Copy offer URL';
const BUTTON_COPY_TEXT_COPIED = 'Copied';
const TRANSACTION_DATA_TYPE = "org.waltid.transaction-data.payment-authorization";
const TRANSACTION_CREDENTIAL_ID = "selected_credential";
const VERIFIER2_COMPLETED_STATUSES = ["SUCCESSFUL", "FAILED", "EXPIRED", "COMPLETED", "UNSUCCESSFUL", "REJECTED"];

type Verifier2StatusInfo = {
  status?: string;
};

export default function Verification() {
  const env = useContext(EnvContext);
  const [AvailableCredentials] = useContext(CredentialsContext);
  const router = useRouter();

  const [verifyURL, setverifyURL] = useState('');
  const [walletRequestUrl, setWalletRequestUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [copyText, setCopyText] = useState(BUTTON_COPY_TEXT_DEFAULT);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!router.isReady) {
      return;
    }

    let cancelled = false;

    const getverifyURL = async () => {
      setLoading(true);
      setError(null);

      try {
        const verifierBaseUrl = env.NEXT_PUBLIC_VERIFIER
          ? env.NEXT_PUBLIC_VERIFIER
          : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER;
        const verifier2BaseUrl = env.NEXT_PUBLIC_VERIFIER2
          ? env.NEXT_PUBLIC_VERIFIER2
          : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER2;
        const issuerBaseUrl = env.NEXT_PUBLIC_ISSUER
          ? env.NEXT_PUBLIC_ISSUER
          : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_ISSUER;

        const vps = router.query.vps?.toString().split(',') ?? [];
        const ids = router.query.ids?.toString().split(',') ?? [];
        const format = router.query.format?.toString() ?? CredentialFormats[0];
        const transactionEnabled = router.query.tx?.toString() === '1';
        const standardVersion: "draft13" | "draft11" = 'draft13'; // ['draft13', 'draft11']
        const issuerMetadataConfigSelector = {
          'draft13': 'credential_configurations_supported',
          'draft11': 'credentials_supported',
        } as const;

        if (!ids.length || ids[0] === '') {
          throw new Error('No credential IDs provided for verification.');
        }

        if (AvailableCredentials.length === 0) {
          return;
        }

        const credentials = AvailableCredentials.filter((cred) => {
          for (const id of ids) {
            if (id.toString() === cred.id.toString()) {
              return true;
            }
          }
          return false;
        });

        if (credentials.length === 0) {
          throw new Error('No credentials were selected for verification.');
        }

        if (transactionEnabled) {
          if (credentials.length !== 1) {
            throw new Error('Transaction data verification currently requires exactly one selected credential.');
          }
          if (!isTransactionDataSupportedSelectedFormat(format)) {
            throw new Error('Transaction data verification currently supports only SD-JWT + IETF SD-JWT VC in this flow.');
          }

          const selectedCredential = credentials[0];
          const verifier2Format = mapSelectedFormatToVerifier2Format(format);
          const selectedCredentialType = getCredentialType(selectedCredential.offer?.type, selectedCredential.title);
          const issuerMetadata = await axios.get(`${issuerBaseUrl}/${standardVersion}/.well-known/openid-credential-issuer`);
          const transactionVctValue = resolveSdJwtVctFromIssuerMetadata(
            issuerMetadata.data,
            issuerMetadataConfigSelector[standardVersion],
            selectedCredentialType,
          );
          const transactionAmount = readRequiredQueryParam(router.query.tx_amount, "tx_amount");
          const transactionCurrency = readRequiredQueryParam(router.query.tx_currency, "tx_currency").toUpperCase();
          const transactionPayee = readRequiredQueryParam(router.query.tx_payee, "tx_payee");
          const transactionReference = readRequiredQueryParam(router.query.tx_reference, "tx_reference");
          const encodedTransactionData = encodeBase64Url(JSON.stringify({
            type: TRANSACTION_DATA_TYPE,
            credential_ids: [TRANSACTION_CREDENTIAL_ID],
            transaction_data_hashes_alg: ["sha-256"],
            amount: transactionAmount,
            currency: transactionCurrency,
            payee: transactionPayee,
            reference: transactionReference,
          }));

          const response = await axios.post(`${verifier2BaseUrl}/verification-session/create`, {
            flow_type: "cross_device",
            core_flow: {
              dcql_query: {
                credentials: [
                  buildTransactionCredentialQuery(
                    verifier2Format,
                    transactionVctValue,
                  ),
                ],
              },
            },
            openid: {
              transactionData: [encodedTransactionData],
            },
          });

          const data = response.data as {
            sessionId: string;
            bootstrapAuthorizationRequestUrl?: string;
            fullAuthorizationRequestUrl?: string;
          };
          const qrUrl = data.bootstrapAuthorizationRequestUrl ?? data.fullAuthorizationRequestUrl ?? "";
          if (!qrUrl) {
            throw new Error('Verification service did not return an authorization request URL.');
          }

          if (cancelled) {
            return;
          }

          setverifyURL(qrUrl);
          setWalletRequestUrl(data.fullAuthorizationRequestUrl ?? qrUrl);
          setLoading(false);

          waitForVerifier2Completion(verifier2BaseUrl, data.sessionId, () => cancelled).then((status) => {
            if (cancelled || !VERIFIER2_COMPLETED_STATUSES.includes(status)) {
              return;
            }
            router.push(`/success/${data.sessionId}?engine=verifier2`);
          }).catch((pollError: any) => {
            if (cancelled) {
              return;
            }
            const pollErrorMessage = pollError?.response?.data?.errorDescription
              || pollError?.response?.data?.message
              || pollError?.message
              || "Could not fetch verification session status.";
            setError(pollErrorMessage);
          });

          return;
        }

        const issuerMetadata = await axios.get(`${issuerBaseUrl}/${standardVersion}/.well-known/openid-credential-issuer`);
        const request_credentials = credentials.map((credential) => {
          const credentialType = getCredentialType(credential.offer?.type, credential.title);
          if (mapFormat(format) === 'vc+sd-jwt') {
            const vctUrl = resolveSdJwtVctFromIssuerMetadata(
              issuerMetadata.data,
              issuerMetadataConfigSelector[standardVersion],
              credentialType,
            );
            return {
              vct: vctUrl,
              format: mapFormat(format),
            };
          }

          return {
            type: credentialType,
            format: mapFormat(format),
          };
        });

        const requestBody: any = {
          request_credentials: request_credentials,
        };

        if (mapFormat(format) !== 'vc+sd-jwt') {
          requestBody.vc_policies = vps.map((vp) => {
            if (vp.includes('=')) {
              return {
                policy: vp.split('=')[0],
                args: vp.split('=')[1],
              };
            }
            return vp;
          });
        }

        const response = await axios.post(
          `${verifierBaseUrl}/openid4vc/verify`,
          requestBody,
          {
            headers: {
              successRedirectUri: `${window.location.origin}/success/$id`,
              errorRedirectUri: `${window.location.origin}/success/$id`,
            },
          }
        );

        if (cancelled) {
          return;
        }

        setverifyURL(response.data);
        setWalletRequestUrl(response.data);
        setLoading(false);

        const state = getStateFromUrl(response.data);
        if (state) {
          checkVerificationResult(verifierBaseUrl, state).then((result) => {
            if (cancelled || !result) {
              return;
            }
            router.push(`/success/${state}`);
          });
        }
      } catch (e: any) {
        if (cancelled) {
          return;
        }

        const message = e?.response?.data?.errorDescription
          || e?.response?.data?.message
          || e?.message
          || 'Could not create verification request.';
        setError(message);
        setLoading(false);
      }
    };

    getverifyURL();
    return () => {
      cancelled = true;
    };
  }, [router.isReady, router.query, AvailableCredentials, env]);

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
    const requestUrl = walletRequestUrl || verifyURL;
    if (!requestUrl) {
      return;
    }

    sendToWebWallet(
      env.NEXT_PUBLIC_WALLET
        ? env.NEXT_PUBLIC_WALLET
        : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_WALLET,
      'api/siop/initiatePresentation',
      requestUrl
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
          <Button style="link" onClick={copyCurrentURLToClipboard} disabled={!verifyURL}>
            {copyText}
          </Button>
          <Button onClick={openWebWallet} style="button" disabled={!verifyURL}>
            Open Web Wallet
          </Button>
        </div>
        {error && (
          <p className="mt-6 text-sm text-red-600 break-all">{error}</p>
        )}
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

function mapSelectedFormatToVerifier2Format(selectedFormat: string): "dc+sd-jwt" {
  if (!isTransactionDataSupportedSelectedFormat(selectedFormat)) {
    throw new Error("Transaction data verification currently supports only SD-JWT + IETF SD-JWT VC in this flow.");
  }
  return "dc+sd-jwt";
}

function buildTransactionCredentialQuery(format: "dc+sd-jwt", vctValue: string) {
  return {
    id: TRANSACTION_CREDENTIAL_ID,
    format,
    meta: {
      vct_values: [vctValue],
    },
    claims: [
      { path: ["given_name"] },
      { path: ["family_name"] },
      { path: ["address", "street_address"] },
    ],
    require_cryptographic_holder_binding: true,
  };
}

function encodeBase64Url(value: string) {
  const bytes = new TextEncoder().encode(value);
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

async function waitForVerifier2Completion(verifier2BaseUrl: string, sessionId: string, isCancelled: () => boolean = () => false,): Promise<string> {
  const maxAttempts = 120;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    if (isCancelled()) {
      return ""
    }
    const response = await axios.get<Verifier2StatusInfo>(
      `${verifier2BaseUrl}/verification-session/${encodeURIComponent(sessionId)}/info`,
    );
    const status = response.data.status ?? "";
    if (VERIFIER2_COMPLETED_STATUSES.includes(status)) {
      return status;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  return "";
}

function getCredentialType(typeList: any, fallback: string): string {
  if (Array.isArray(typeList) && typeList.length > 0) {
    return typeList[typeList.length - 1];
  }
  return fallback;
}

function resolveSdJwtVctFromIssuerMetadata(
  issuerMetadata: any,
  configSelectorKey: "credential_configurations_supported" | "credentials_supported",
  credentialType: string,
): string {
  const vct = issuerMetadata?.[configSelectorKey]?.[`${credentialType}_vc+sd-jwt`]?.vct;
  if (typeof vct !== "string" || vct.length === 0) {
    throw new Error(`No SD-JWT VC configuration found for selected credential type: ${credentialType}`);
  }
  return vct;
}

function readRequiredQueryParam(value: string | string[] | undefined, queryName: string): string {
  const normalized = (Array.isArray(value) ? value[0] : value)?.trim();
  if (!normalized) {
    throw new Error(`Missing required transaction parameter: ${queryName}`);
  }
  return normalized;
}
