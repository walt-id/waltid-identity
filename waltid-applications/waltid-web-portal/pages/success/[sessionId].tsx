import WaltIcon from "@/components/walt/logo/WaltIcon";
import {CheckCircleIcon, XCircleIcon} from "@heroicons/react/24/outline";
import {useContext, useEffect, useMemo, useState} from "react";
import {useRouter} from "next/router";
import axios from "axios";
import nextConfig from "@/next.config";
import Modal from "@/components/walt/modal/BaseModal";
import {EnvContext} from "@/pages/_app";

type DisplayPolicyEntry = {
  policy: string;
  policyId?: string;
  is_success: boolean;
  result?: unknown;
  error?: string | null;
  source?: "vp" | "vc" | "specific_vc" | "legacy";
  queryId?: string;
};

type DisplayPolicyGroup = {
  policyResults: DisplayPolicyEntry[];
};

type DisplayCredential = {
  type?: string[] | string;
  vct?: string;
  credentialSubject?: Record<string, unknown>;
  [key: string]: unknown;
};

const EMPTY_POLICY_GROUP: DisplayPolicyGroup = { policyResults: [] };

export default function Success() {
  const env = useContext(EnvContext);
  const router = useRouter();
  const isVerifier2Engine = router.query.engine?.toString() === 'verifier2';
  const [vctName, setVctName] = useState<string | null>(null);

  const [policyResults, setPolicyResults] = useState<DisplayPolicyGroup[]>([]);
  const [credentials, setCredentials] = useState<DisplayCredential[]>([]);
  const [index, setIndex] = useState<number>(0);
  const [modal, setModal] = useState<boolean>(false);
  const [statusLabel, setStatusLabel] = useState<string | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);

  useEffect(() => {
    if (!router.isReady || !router.query.sessionId) return;

    if (isVerifier2Engine) {
      const verifier2BaseUrl = env.NEXT_PUBLIC_VERIFIER2
        ? env.NEXT_PUBLIC_VERIFIER2
        : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER2;
      axios
        .get(
          `${verifier2BaseUrl}/verification-session/${encodeURIComponent(router.query.sessionId.toString())}/info`
        )
        .then((response) => {
          const sessionInfo = response.data as Record<string, unknown>;
          const rawPresentedCredentials = sessionInfo.presented_credentials
            ?? sessionInfo.presentedCredentials
            ?? sessionInfo.presented_presentations
            ?? asRecord(sessionInfo.presented_raw_data)?.vpToken
            ?? asRecord(sessionInfo.tokenResponse)?.vp_token;
          const rawPolicyResults = sessionInfo.policy_results
            ?? sessionInfo.policyResults
            ?? asRecord(sessionInfo.authorizationRequest)?.policies;

          setCredentials(normalizePresentedCredentials(rawPresentedCredentials));
          setPolicyResults(normalizePolicyResults(rawPolicyResults));
          setStatusLabel(typeof sessionInfo.status === "string" ? sessionInfo.status : "Unknown");
          setPageError(null);
          setVctName(null);
        })
        .catch((error) => {
          const message = error?.response?.data?.errorDescription
            || error?.response?.data?.message
            || error?.message
            || 'Could not load verification session.';
          setPageError(message);
          console.error(error);
        });
      return;
    }

    axios
      .get(
        `${env.NEXT_PUBLIC_VERIFIER ? env.NEXT_PUBLIC_VERIFIER : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER}/openid4vc/session/${router.query.sessionId}`
      )
      .then((response) => {
        let parsedToken = parseJwt(response.data.tokenResponse.vp_token);
        const parsedVp = asRecord(parsedToken.vp);
        let containsVP = !!parsedVp?.verifiableCredential;
        let vcs = containsVP
          ? parsedVp?.verifiableCredential
          : [response.data.tokenResponse.vp_token];

        setCredentials(normalizePresentedCredentials(vcs));

        setPolicyResults(() => {
          if (containsVP) {
            return normalizePolicyResults(response.data.policyResults?.results);
          }
          return normalizePolicyResults([
            {
              policyResults: [
                {
                  policy: 'new_policy',
                  is_success: true,
                },
              ],
            },
            ...(response.data.policyResults?.results ?? []),
          ]);
        });

        if (!containsVP) {
          const vct = parsedToken['vct'];
          if (typeof vct === "string") {
            const vctUrl = new URL(vct);
            const vctResolutionUrl = `${vctUrl.origin}/.well-known/vct${vctUrl.pathname}`;
            fetchVctName(vctResolutionUrl).then((name) => setVctName(name));
          }
        }
        setStatusLabel(null);
        setPageError(null);
      })
      .catch((error) => {
        const message = error?.response?.data?.errorDescription
          || error?.response?.data?.message
          || error?.message
          || 'Could not load verification session.';
        setPageError(message);
        console.error(error);
      });
  }, [router.isReady, router.query.sessionId, isVerifier2Engine, env]);

  useEffect(() => {
    if (index < credentials.length) {
      return;
    }
    setIndex(0);
  }, [credentials.length, index]);

  const activeCredential = credentials[index];
  const credentialRows = useMemo(
    () => buildCredentialRows(activeCredential),
    [activeCredential],
  );
  const hasSyntheticLeadingPolicyGroup = (policyResults[0]?.policyResults?.length ?? 0) === 0;
  const activePolicyGroupIndex = index + (hasSyntheticLeadingPolicyGroup ? 1 : 0);
  const activePolicyResults = policyResults[activePolicyGroupIndex]?.policyResults
    ?? policyResults[index]?.policyResults
    ?? [];
  const transactionDataPolicyResults = useMemo(
    () => activePolicyResults.filter((policy) => isTransactionDataPolicyId(policy.policyId ?? policy.policy)),
    [activePolicyResults],
  );
  const nonTransactionPolicyResults = useMemo(
    () => activePolicyResults.filter((policy) => !isTransactionDataPolicyId(policy.policyId ?? policy.policy)),
    [activePolicyResults],
  );
  const titleLabel = getCredentialTitle(activeCredential, vctName);

  return (
    <div className="min-h-screen flex justify-center bg-gray-50 py-8 px-4 overflow-y-auto">
      <Modal show={modal} securedByWalt={false} onClose={() => setModal(false)}>
        <div className="flex flex-col items-center">
          <div className="w-full">
            <textarea
              value={JSON.stringify(
                activeCredential?.credentialSubject ?? activeCredential,
                null,
                4
              )}
              disabled={true}
              className="w-full h-48 border-2 border-gray-300 rounded-md px-2"
            />
          </div>
        </div>
      </Modal>
      <div className="relative w-full sm:w-10/12 md:w-8/12 lg:w-6/12 text-center shadow-2xl rounded-lg pt-8 pb-8 px-6 sm:px-10 bg-white">
        <h1 className="text-3xl text-gray-900 text-center font-bold mb-10">
          Presented Credentials
        </h1>
        {isVerifier2Engine && (
          <div className="mb-8 text-sm text-gray-600">
            <div>
              Session ID: <span className="font-mono text-gray-800">{router.query.sessionId?.toString()}</span>
            </div>
            <div className="mt-2">
              Status: <span className="font-semibold text-gray-800">{statusLabel ?? "Unknown"}</span>
            </div>
          </div>
        )}
        {pageError && (
          <p className="text-sm text-red-600 break-all mb-6">{pageError}</p>
        )}
        {credentials.length > 0 ? (
          <div className="flex items-center justify-center">
            {index !== 0 && credentials.length > 1 && (
              <button
                onClick={() => setIndex(index - 1)}
                className="text-gray-500 hover:text-gray-900 focus:outline-none absolute left-10"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  className="h-6 w-6 mr-2"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M15 19l-7-7 7-7"
                  />
                </svg>
              </button>
            )}
            <div className="group h-[225px] w-[400px] [perspective:1000px]">
              <div className="relative h-full w-full rounded-xl shadow-xl transition-all duration-500 [transform-style:preserve-3d] group-hover:[transform:rotateY(180deg)]">
                <div className="absolute inset-0">
                  <div className="flex h-full w-full flex-col drop-shadow-sm rounded-xl py-7 px-8 text-gray-100 cursor-pointer overflow-hidden bg-gradient-to-r from-green-700 to-green-900 z-[-2]">
                    <div className="flex flex-row">
                      <WaltIcon height={35} width={35} outline type="white" />
                    </div>
                    <div className="mb-8 mt-12">
                      <h6 className={'text-2xl font-bold overflow-hidden text-ellipsis whitespace-nowrap'}>
                        {titleLabel}
                      </h6>
                    </div>
                  </div>
                </div>
                <div className="absolute inset-0 h-full w-full rounded-xl bg-white p-5 text-slate-200 [transform:rotateY(180deg)] [backface-visibility:hidden] overflow-y-scroll">
                  {credentialRows.map((item) => {
                    return (
                      <div key={item.key} className="flex flex-row py-1">
                        <div className="text-gray-600 text-left w-1/2 capitalize leading-[1.1]">
                          {item.key}
                        </div>
                        <div className="text-slate-800 text-left w-1/2 text-[#313233]">
                          {item.value}
                        </div>
                      </div>
                    );
                  })}
                  <div className="flex flex-row py-1">
                    <button
                      onClick={() => setModal(true)}
                      className="text-gray-500 text-center w-full capitalize leading-[1.1] underline"
                      disabled={!activeCredential}
                    >
                      View Credential In JSON
                    </button>
                  </div>
                </div>
              </div>
            </div>
            {index !== credentials.length - 1 && credentials.length > 1 && (
              <button
                onClick={() => setIndex(index + 1)}
                className="text-gray-500 hover:text-gray-900 focus:outline-none absolute right-10"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  className="h-6 w-6 ml-2"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 5l7 7-7 7"
                  />
                </svg>
              </button>
            )}
          </div>
        ) : (
          <div className="rounded-xl border border-gray-200 w-full max-w-[640px] mx-auto px-6 py-6 text-sm text-gray-600 text-left">
            <div className="font-medium text-gray-700 mb-2">No credential payload available</div>
            <div>
              This can happen after session finalization: credential payloads may be cleared while policy outcomes
              remain available.
            </div>
          </div>
        )}
        <div className="mt-10 px-12">
          {isVerifier2Engine && (
            <div className="mb-8">
              <div className="flex flex-row items-center justify-center mb-3 text-gray-500">
                Transaction Data Verification
              </div>
              {transactionDataPolicyResults.length > 0 ? (
                <div className="xs:grid xs:grid-cols-1 items-center justify-center gap-2">
                  {transactionDataPolicyResults.map((policy, idx) => {
                    const policyLabel = formatPolicyLabel(policy.policyId ?? policy.policy);
                    return (
                      <div
                        key={`${policy.policyId ?? policy.policy}-${idx}`}
                        className="flex items-start gap-3"
                      >
                        {policy.is_success ? (
                          <CheckCircleIcon className="h-4 text-green-600 mt-[3px]" />
                        ) : (
                          <XCircleIcon className="h-4 text-red-600 mt-[3px]" />
                        )}
                        <div className="text-left">
                          <div>{policyLabel}</div>
                          {policy.error && (
                            <div className="text-xs text-red-600 mt-1">{policy.error}</div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="text-sm text-gray-500 text-center">
                  No transaction_data policy result was reported for this session.
                </div>
              )}
            </div>
          )}
          <div className="flex flex-row items-center justify-center mb-5 text-gray-500">
            {nonTransactionPolicyResults.length
              ? 'The VP was verified along with:'
              : 'No additional non-transaction policy results were reported.'}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-y-2 gap-x-8 items-start justify-center">
            {nonTransactionPolicyResults
              .map((policy) => {
                return {
                  name: toPolicyDisplayName(policy.policyId ?? policy.policy),
                  is_success: policy.is_success,
                };
              })
              .map((policy, idx) => {
                return (
                  <div
                    key={policy.name}
                    className={`flex items-start gap-3 min-w-0 ${idx % 2 == 1 ? 'sm:justify-self-end' : ''}`}
                  >
                    {policy.is_success ? (
                      <CheckCircleIcon className="h-4 text-green-600 mt-[3px] shrink-0" />
                    ) : (
                      <XCircleIcon className="h-4 text-red-600 mt-[3px]" />
                    )}
                    <div className="text-left break-words">{policy.name}</div>
                  </div>
                );
              })}
          </div>
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

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  return Buffer.from(padded, "base64").toString();
}

function parseJwt(token: string): Record<string, unknown> {
  try {
    const payload = token.split(".")[1];
    if (!payload) {
      return {};
    }
    return JSON.parse(decodeBase64Url(payload)) as Record<string, unknown>;
  } catch (error) {
    console.error("Could not parse JWT payload", error);
    return {};
  }
}

const fetchVctName = async (vctUrl: string) => {
  try {
    const response = await axios.get(vctUrl);
    const bodyJson = response.data;
    return bodyJson['name'] as string;
  } catch (error) {
    console.error('Error fetching vct:', error);
    return 'Unknown VCT';
  }
};

function toDisplayCredential(value: unknown): DisplayCredential | null {
  const record = asRecord(value);
  if (!record) {
    return null;
  }

  const vc = asRecord(record.vc);
  if (vc) {
    return toDisplayCredential(vc);
  }

  const credentialSubject = asRecord(record.credentialSubject) ?? asRecord(record.claims);
  const hasCredentialMarkers = Boolean(
    credentialSubject ||
    record.type ||
    record.vct ||
    record.id ||
    record.format
  );
  if (!hasCredentialMarkers) {
    return null;
  }

  const credential: DisplayCredential = {
    ...record,
    ...(credentialSubject ? { credentialSubject } : {}),
  };

  if (Array.isArray(record.type) || typeof record.type === "string") {
    credential.type = record.type as string[] | string;
  }
  if (typeof record.vct === "string") {
    credential.vct = record.vct;
  }

  return credential;
}

function parseCredentialToken(vcToken: string): DisplayCredential | null {
  if (!vcToken || typeof vcToken !== "string") {
    return null;
  }

  const split = vcToken.split("~");
  const parsed = parseJwt(split[0]);

  if (split.length === 1) {
    return toDisplayCredential(parsed) ?? toDisplayCredential(parsed.vc) ?? null;
  }

  const credentialWithSdJWTAttributes = (toDisplayCredential(parsed) ?? {}) as DisplayCredential;
  const parsedVc = asRecord(parsed.vc);
  split.slice(1).forEach((item) => {
    if (item.split('.').length === 3) {
      return;
    }

    try {
      const parsedItem = JSON.parse(decodeBase64Url(item)) as unknown;
      if (!Array.isArray(parsedItem) || parsedItem.length < 3 || typeof parsedItem[1] !== "string") {
        return;
      }

      const existingCredentialSubject = asRecord(credentialWithSdJWTAttributes.credentialSubject) ?? {};
      credentialWithSdJWTAttributes.credentialSubject = {
        [parsedItem[1]]: parsedItem[2],
        ...existingCredentialSubject,
      };
    } catch (error) {
      console.error("Could not decode SD-JWT disclosure", error);
    }
  });

  if (Array.isArray(parsedVc?.type) || typeof parsedVc?.type === "string") {
    credentialWithSdJWTAttributes.type = parsedVc?.type as string[] | string;
  }

  return credentialWithSdJWTAttributes;
}

function normalizePresentedCredentials(raw: unknown): DisplayCredential[] {
  if (raw == null) {
    return [];
  }

  if (Array.isArray(raw)) {
    return raw.flatMap((item) => normalizePresentedCredentials(item));
  }

  if (typeof raw === "string") {
    const parsed = parseCredentialToken(raw);
    return parsed ? [parsed] : [];
  }

  const record = asRecord(raw);
  if (!record) {
    return [];
  }

  const vp = asRecord(record.vp);
  if (vp?.verifiableCredential) {
    return normalizePresentedCredentials(vp.verifiableCredential);
  }

  if (record.vp_token) {
    return normalizePresentedCredentials(record.vp_token);
  }
  if (record.vpToken) {
    return normalizePresentedCredentials(record.vpToken);
  }

  const credential = toDisplayCredential(record);
  if (credential) {
    return [credential];
  }

  // Verifier2 may return credential payloads as keyed maps (query-id/credential-id -> payload).
  // If this object is not itself a credential shape, normalize nested values recursively.
  return Object.values(record).flatMap((entry) => normalizePresentedCredentials(entry));
}

function normalizePolicyResults(raw: unknown): DisplayPolicyGroup[] {
  if (!raw) {
    return [EMPTY_POLICY_GROUP];
  }

  const policyRecord = asRecord(raw);
  if (policyRecord?.results) {
    return normalizePolicyResults(policyRecord.results);
  }
  if (policyRecord?.policyResults) {
    return normalizePolicyResults(policyRecord.policyResults);
  }
  if (policyRecord?.policy_results) {
    return normalizePolicyResults(policyRecord.policy_results);
  }

  if (policyRecord) {
    const looksLikeVerifier2Structure = Boolean(
      policyRecord.vc_policies || policyRecord.specific_vc_policies || policyRecord.vp_policies,
    );
    if (looksLikeVerifier2Structure) {
      const normalizedPolicies = [
        ...normalizePolicyEntryList(policyRecord.vc_policies, { source: "vc" }),
        ...normalizeSpecificPolicyEntries(policyRecord.specific_vc_policies),
        ...normalizeVpPolicyEntries(policyRecord.vp_policies),
      ];
      return normalizedPolicies.length
        ? [EMPTY_POLICY_GROUP, { policyResults: normalizedPolicies }]
        : [EMPTY_POLICY_GROUP];
    }
  }

  if (Array.isArray(raw)) {
    const looksLikeLegacyGroupedPolicies = raw.every((item) => {
      const grouped = asRecord(item)?.policyResults;
      return Array.isArray(grouped);
    });
    if (looksLikeLegacyGroupedPolicies) {
      return raw.map((group) => {
        const groupPolicies = asRecord(group)?.policyResults;
        return {
          policyResults: normalizePolicyEntryList(groupPolicies, { source: "legacy" }),
        };
      });
    }

    const normalizedPolicies = normalizePolicyEntryList(raw, { source: "legacy" });
    return normalizedPolicies.length
      ? [EMPTY_POLICY_GROUP, { policyResults: normalizedPolicies }]
      : [EMPTY_POLICY_GROUP];
  }

  const normalizedPolicy = normalizePolicyEntry(raw, { source: "legacy" });
  return normalizedPolicy
    ? [EMPTY_POLICY_GROUP, { policyResults: [normalizedPolicy] }]
    : [EMPTY_POLICY_GROUP];
}

function normalizeVpPolicyEntries(rawVpPolicies: unknown): DisplayPolicyEntry[] {
  const vpPolicyMap = asRecord(rawVpPolicies);
  if (!vpPolicyMap) {
    return [];
  }

  return Object.entries(vpPolicyMap).flatMap(([queryId, queryPolicyRuns]) => {
    const runByPolicyId = asRecord(queryPolicyRuns);
    if (!runByPolicyId) {
      return [];
    }

    return Object.entries(runByPolicyId)
      .map(([policyId, policyRun]) => {
        const parsed = normalizePolicyEntry(policyRun, { source: "vp", queryId });
        if (parsed) {
          return parsed;
        }
        return {
          policy: policyId,
          policyId,
          is_success: false,
          source: "vp" as const,
          queryId,
        };
      });
  });
}

function normalizeSpecificPolicyEntries(rawSpecificPolicies: unknown): DisplayPolicyEntry[] {
  const specificPolicyMap = asRecord(rawSpecificPolicies);
  if (!specificPolicyMap) {
    return [];
  }

  return Object.entries(specificPolicyMap).flatMap(([queryId, policies]) =>
    normalizePolicyEntryList(policies, { source: "specific_vc", queryId }),
  );
}

function normalizePolicyEntryList(
  rawPolicies: unknown,
  context: { source: DisplayPolicyEntry["source"]; queryId?: string },
): DisplayPolicyEntry[] {
  if (!rawPolicies) {
    return [];
  }

  const policyArray = Array.isArray(rawPolicies) ? rawPolicies : [rawPolicies];
  return policyArray
    .map((policy) => normalizePolicyEntry(policy, context))
    .filter((policy): policy is DisplayPolicyEntry => policy !== null);
}

function normalizePolicyEntry(
  rawPolicy: unknown,
  context: { source: DisplayPolicyEntry["source"]; queryId?: string },
): DisplayPolicyEntry | null {
  const policyObj = asRecord(rawPolicy);
  if (!policyObj) {
    return null;
  }

  const policyId = extractPolicyId(policyObj);
  const isSuccess = extractPolicySuccess(policyObj);
  const error = extractPolicyError(policyObj);

  return {
    policy: policyId,
    policyId,
    is_success: isSuccess,
    result: policyObj.result ?? policyObj.results,
    error,
    source: context.source,
    queryId: context.queryId,
  };
}

function extractPolicyId(policyObj: Record<string, unknown>): string {
  const policyField = policyObj.policy;
  if (typeof policyField === "string" && policyField.length > 0) {
    return policyField;
  }

  const policyObjField = asRecord(policyField);
  if (policyObjField && typeof policyObjField.id === "string" && policyObjField.id.length > 0) {
    return policyObjField.id;
  }

  const executedPolicy = asRecord(policyObj.policy_executed);
  if (executedPolicy && typeof executedPolicy.id === "string" && executedPolicy.id.length > 0) {
    return executedPolicy.id;
  }

  if (typeof policyObj.id === "string" && policyObj.id.length > 0) {
    return policyObj.id;
  }

  return "policy";
}

function extractPolicySuccess(policyObj: Record<string, unknown>): boolean {
  const explicitSuccess = policyObj.is_success ?? policyObj.isSuccess ?? policyObj.success;
  if (typeof explicitSuccess === "boolean") {
    return explicitSuccess;
  }

  const errors = policyObj.errors;
  if (Array.isArray(errors)) {
    return errors.length === 0;
  }

  return false;
}

function extractPolicyError(policyObj: Record<string, unknown>): string | null {
  if (typeof policyObj.error === "string" && policyObj.error.length > 0) {
    return policyObj.error;
  }

  const errors = policyObj.errors;
  if (!Array.isArray(errors) || errors.length === 0) {
    return null;
  }

  const firstError = asRecord(errors[0]);
  if (!firstError) {
    return null;
  }
  if (typeof firstError.message === "string" && firstError.message.length > 0) {
    return firstError.message;
  }
  if (typeof firstError.error === "string" && firstError.error.length > 0) {
    return firstError.error;
  }
  return null;
}

function isTransactionDataPolicyId(policyId: string): boolean {
  return policyId.includes("transaction-data") || policyId.includes("transaction_data");
}

function formatPolicyLabel(policyId: string): string {
  const normalized = policyId
    .split("/")
    .pop()
    ?.replace(/[+]/g, " plus ")
    .replace(/[-_]/g, " ")
    .trim() ?? policyId;

  if (!normalized.length) {
    return "Policy";
  }

  return normalized.replace(/\b\w/g, (c) => c.toUpperCase());
}

function toPolicyDisplayName(policyId: string): string {
  const label = formatPolicyLabel(policyId);
  return /policy$/i.test(label) ? label : `${label} Policy`;
}

function getCredentialTitle(credential: DisplayCredential | undefined, vctName: string | null): string {
  if (!credential) {
    return "Credential";
  }

  if (Array.isArray(credential.type) && credential.type.length > 0) {
    return credential.type[credential.type.length - 1].replace(/([a-z0-9])([A-Z])/g, "$1 $2");
  }
  if (typeof credential.type === "string" && credential.type.length > 0) {
    return credential.type.replace(/([a-z0-9])([A-Z])/g, "$1 $2");
  }
  if (credential.vct) {
    return vctName ?? credential.vct;
  }

  return "Credential";
}

function buildCredentialRows(credential: DisplayCredential | undefined): Array<{ key: string; value: string }> {
  if (!credential) {
    return [];
  }

  const sourceRecord = asRecord(credential.credentialSubject) ?? asRecord(credential);
  if (!sourceRecord) {
    return [];
  }

  return Object.entries(sourceRecord)
    .map(([key, value]) => {
      if (typeof value !== "string" || value.length === 0 || value.length >= 40) {
        return null;
      }
      return {
        key: (key.charAt(0).toUpperCase() + key.slice(1)).replace(/([a-z0-9])([A-Z])/g, "$1 $2"),
        value,
      };
    })
    .filter((item): item is { key: string; value: string } => item !== null);
}
