import Button from "@/components/walt/button/Button";
import WaltIcon from "@/components/walt/logo/WaltIcon";
import {EnvContext} from "@/pages/_app";
import nextConfig from "@/next.config";
import {sendToWebWallet} from "@/utils/sendToWebWallet";
import axios from "axios";
import QRCode from "react-qr-code";
import {FormEvent, useContext, useEffect, useMemo, useState} from "react";
import {useRouter} from "next/router";

const TRANSACTION_DATA_TYPE = "org.waltid.transaction-data.payment-authorization";
const ISO_MDL_DOCTYPE = "org.iso.18013.5.1.mDL";

const PRESENTATION_FORMATS = [
  {
    id: "dc+sd-jwt",
    label: "SD-JWT VC",
    description: "Hash-bound transaction authorization with KB-JWT.",
  },
  {
    id: "mso_mdoc",
    label: "mdoc",
    description: "DeviceSigned transaction authorization for mobile documents.",
  },
] as const;

type PresentationFormat = (typeof PRESENTATION_FORMATS)[number]["id"];

type VerificationSessionInfo = {
  status?: string;
  policyResults?: unknown;
  presentedCredentials?: unknown;
};

function encodeBase64Url(value: string) {
  const bytes = new TextEncoder().encode(value);
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

export default function TransactionVerification() {
  const env = useContext(EnvContext);
  const router = useRouter();
  const verifier2BaseUrl = env.NEXT_PUBLIC_VERIFIER2
    ? env.NEXT_PUBLIC_VERIFIER2
    : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER2;
  const issuerBaseUrl = env.NEXT_PUBLIC_ISSUER
    ? env.NEXT_PUBLIC_ISSUER
    : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_ISSUER;
  const walletBaseUrl = env.NEXT_PUBLIC_WALLET
    ? env.NEXT_PUBLIC_WALLET
    : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_WALLET;

  const [amount, setAmount] = useState("42.00");
  const [currency, setCurrency] = useState("EUR");
  const [payee, setPayee] = useState("ACME Corp");
  const [reference, setReference] = useState("INV-2026-042");
  const [presentationFormat, setPresentationFormat] = useState<PresentationFormat>("dc+sd-jwt");
  const [loading, setLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [requestUrl, setRequestUrl] = useState("");
  const [walletUrl, setWalletUrl] = useState("");
  const [sessionInfo, setSessionInfo] = useState<VerificationSessionInfo | null>(null);
  const [error, setError] = useState<string | null>(null);

  const transactionPreview = useMemo(
    () => ({
      type: TRANSACTION_DATA_TYPE,
      credential_ids: ["payment_credential"],
      ...(presentationFormat === "dc+sd-jwt" ? { transaction_data_hashes_alg: ["sha-256"] } : {}),
      require_cryptographic_holder_binding: true,
      amount,
      currency,
      payee,
      reference,
    }),
    [amount, currency, payee, presentationFormat, reference],
  );

  const selectedPresentationFormat = PRESENTATION_FORMATS.find(
    (candidate) => candidate.id === presentationFormat,
  ) ?? PRESENTATION_FORMATS[0];

  useEffect(() => {
    if (!sessionId) {
      return;
    }

    const interval = window.setInterval(async () => {
      try {
        const response = await axios.get<VerificationSessionInfo>(
          `${verifier2BaseUrl}/verification-session/${encodeURIComponent(sessionId)}/info`,
        );
        setSessionInfo(response.data);
        if (["SUCCESSFUL", "FAILED", "COMPLETED"].includes(response.data.status ?? "")) {
          window.clearInterval(interval);
        }
      } catch (pollError) {
        console.error("Could not poll verifier2 session", pollError);
      }
    }, 1500);

    return () => window.clearInterval(interval);
  }, [sessionId, verifier2BaseUrl]);

  useEffect(() => {
    setSessionId(null);
    setRequestUrl("");
    setWalletUrl("");
    setSessionInfo(null);
    setError(null);
  }, [amount, currency, payee, presentationFormat, reference]);

  async function createTransactionVerification(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setSessionId(null);
    setRequestUrl("");
    setWalletUrl("");
    setSessionInfo(null);

    try {
      const encodedTransactionData = encodeBase64Url(JSON.stringify(transactionPreview));
      const response = await axios.post(`${verifier2BaseUrl}/verification-session/create`, {
        flow_type: "cross_device",
        core_flow: {
          dcql_query: {
            credentials: [buildCredentialQuery(presentationFormat, issuerBaseUrl)],
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
      setSessionId(data.sessionId);
      setRequestUrl(qrUrl);
      setWalletUrl(data.fullAuthorizationRequestUrl ?? qrUrl);
      setSessionInfo(null);
    } catch (requestError) {
      console.error(requestError);
      setError("Could not create the transaction verification session.");
    } finally {
      setLoading(false);
    }
  }

  function openWallet() {
    if (!walletUrl) {
      return;
    }

    sendToWebWallet(
      walletBaseUrl,
      "api/siop/initiatePresentation",
      walletUrl,
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 px-6 py-10">
      <div className="mx-auto max-w-6xl">
        <button
          onClick={() => router.push("/")}
          className="text-sm text-gray-500 underline"
        >
          Back to portal
        </button>

        <div className="mt-6 grid gap-8 lg:grid-cols-[1.05fr_0.95fr]">
          <section className="rounded-[28px] bg-white p-8 shadow-xl shadow-slate-200/70">
            <div className="flex items-center gap-3">
              <WaltIcon height={30} width={30} type="gray" />
              <div>
                <h1 className="text-3xl font-semibold text-slate-900">
                  Transaction Verification
                </h1>
                <p className="mt-2 text-sm text-slate-500">
                  Create an OpenID4VP 1.0 request with transaction data and verify it through verifier2.
                </p>
              </div>
            </div>

            <form className="mt-8 space-y-5" onSubmit={createTransactionVerification}>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="block sm:col-span-2">
                  <span className="text-sm font-medium text-slate-700">Credential format</span>
                  <select
                    className="mt-2 w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 outline-none focus:border-slate-400"
                    value={presentationFormat}
                    onChange={(event) => setPresentationFormat(event.target.value as PresentationFormat)}
                  >
                    {PRESENTATION_FORMATS.map((format) => (
                      <option key={format.id} value={format.id}>
                        {format.label}
                      </option>
                    ))}
                  </select>
                  <p className="mt-2 text-sm text-slate-500">
                    {selectedPresentationFormat.description}
                  </p>
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-slate-700">Amount</span>
                  <input
                    className="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none focus:border-slate-400"
                    value={amount}
                    onChange={(event) => setAmount(event.target.value)}
                  />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-slate-700">Currency</span>
                  <input
                    className="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none focus:border-slate-400"
                    value={currency}
                    onChange={(event) => setCurrency(event.target.value.toUpperCase())}
                  />
                </label>
              </div>

              <label className="block">
                <span className="text-sm font-medium text-slate-700">Payee</span>
                <input
                  className="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none focus:border-slate-400"
                  value={payee}
                  onChange={(event) => setPayee(event.target.value)}
                />
              </label>

              <label className="block">
                <span className="text-sm font-medium text-slate-700">Reference</span>
                <input
                  className="mt-2 w-full rounded-2xl border border-slate-200 px-4 py-3 outline-none focus:border-slate-400"
                  value={reference}
                  onChange={(event) => setReference(event.target.value)}
                />
              </label>

              <div className="rounded-3xl bg-slate-950 px-5 py-5 text-slate-50">
                <div className="text-sm uppercase tracking-[0.2em] text-slate-400">
                  Encoded transaction payload
                </div>
                <div className="mt-4 text-sm text-slate-300">
                  Request format: {selectedPresentationFormat.label}
                </div>
                <pre className="mt-4 overflow-x-auto whitespace-pre-wrap break-all text-sm">
                  {JSON.stringify(transactionPreview, null, 2)}
                </pre>
              </div>

              <div className="flex flex-wrap gap-3">
                <Button type="submit" loading={loading} loadingText="Creating">
                  Create verification request
                </Button>
                <Button
                  color="secondary"
                  onClick={() => {
                    setSessionId(null);
                    setRequestUrl("");
                    setWalletUrl("");
                    setSessionInfo(null);
                    setError(null);
                  }}
                >
                  Reset
                </Button>
              </div>

              {error ? <p className="text-sm text-red-600">{error}</p> : null}
            </form>
          </section>

          <section className="rounded-[28px] bg-white p-8 shadow-xl shadow-slate-200/70">
            <h2 className="text-2xl font-semibold text-slate-900">Verifier session</h2>
            <p className="mt-2 text-sm text-slate-500">
              Scan the QR code or open the wallet directly once the request has been created.
            </p>

            <div className="mt-8 flex min-h-[280px] items-center justify-center rounded-[28px] border border-dashed border-slate-200 bg-slate-50">
              {requestUrl ? (
                <QRCode
                  className="h-full max-h-[240px] w-full max-w-[240px]"
                  value={requestUrl}
                  viewBox="0 0 256 256"
                />
              ) : (
                <p className="max-w-xs text-center text-sm text-slate-400">
                  The QR code will appear here after you create a transaction verification request.
                </p>
              )}
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <Button onClick={openWallet} disabled={!walletUrl}>
                Open web wallet
              </Button>
              <Button
                color="secondary"
                disabled={!requestUrl}
                onClick={() => navigator.clipboard.writeText(requestUrl)}
              >
                Copy request URL
              </Button>
            </div>

            <div className="mt-8 rounded-3xl border border-slate-200 px-5 py-5">
              <div className="text-sm uppercase tracking-[0.2em] text-slate-400">
                Session status
              </div>
              <div className="mt-3 text-2xl font-semibold text-slate-900">
                {sessionInfo?.status ?? (sessionId ? "Waiting for wallet" : "Not started")}
              </div>
              {sessionId ? (
                <div className="mt-3 text-sm text-slate-500">
                  Session ID: <span className="font-mono text-slate-700">{sessionId}</span>
                </div>
              ) : null}
            </div>

            {sessionInfo?.policyResults ? (
              <div className="mt-6 rounded-3xl bg-slate-950 px-5 py-5 text-slate-50">
                <div className="text-sm uppercase tracking-[0.2em] text-slate-400">
                  Policy results
                </div>
                <pre className="mt-4 overflow-x-auto whitespace-pre-wrap break-all text-xs">
                  {JSON.stringify(sessionInfo.policyResults, null, 2)}
                </pre>
              </div>
            ) : null}
          </section>
        </div>
      </div>
    </div>
  );
}

function buildCredentialQuery(presentationFormat: PresentationFormat, issuerBaseUrl: string) {
  if (presentationFormat === "mso_mdoc") {
    return {
      id: "payment_credential",
      format: "mso_mdoc",
      meta: {
        doctype_value: ISO_MDL_DOCTYPE,
      },
      claims: [
        { path: ["org.iso.18013.5.1", "given_name"] },
        { path: ["org.iso.18013.5.1", "family_name"] },
        { path: ["org.iso.18013.5.1", "issuing_country"] },
      ],
      require_cryptographic_holder_binding: true,
    };
  }

  return {
    id: "payment_credential",
    format: "dc+sd-jwt",
    meta: {
      vct_values: [`${issuerBaseUrl}/identity_credential`],
    },
    claims: [
      { path: ["given_name"] },
      { path: ["family_name"] },
      { path: ["address", "street_address"] },
    ],
    require_cryptographic_holder_binding: true,
  };
}
