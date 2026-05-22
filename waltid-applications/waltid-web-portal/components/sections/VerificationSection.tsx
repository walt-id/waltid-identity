import RowCredential from "@/components/walt/credential/RowCredential";
import PolicyListItem from "@/components/walt/policy/PolicyListItem";
import {AvailableCredential} from "@/types/credentials";
import WaltIcon from "@/components/walt/logo/WaltIcon";
import InputField from "@/components/walt/forms/Input";
import Button from "@/components/walt/button/Button";
import React, {useContext, useEffect, useState} from "react";
import {CredentialsContext, EnvContext} from "@/pages/_app";
import {useRouter} from "next/router";
import nextConfig from "@/next.config";
import {mapFormat} from "@/types/credentials";

type TransactionDataProfile = {
  type: string;
  displayName: string;
  fields: string[];
};

export default function VerificationSection() {
  const router = useRouter();
  const env = useContext(EnvContext);
  const [AvailableCredentials] = useContext(CredentialsContext);

  const [signaturePolicy, setSignaturePolicy] = useState<boolean>(true);
  const [expiredPolicy, setExpiredPolicy] = useState<boolean>(true);
  const [notBeforePolicy, setNotBeforePolicy] = useState<boolean>(true);
  const [webhookPolicy, setWebhookPolicy] = useState<boolean>(false);
  const [webhook, setWebhook] = useState<string>('');
  const [transactionDataEnabled, setTransactionDataEnabled] = useState<boolean>(false);

  const [profiles, setProfiles] = useState<TransactionDataProfile[]>([]);
  const [selectedProfileType, setSelectedProfileType] = useState<string>('');
  const [fieldValues, setFieldValues] = useState<Record<string, string>>({});

  useEffect(() => {
    const verifier2BaseUrl = env.NEXT_PUBLIC_VERIFIER2
      ? env.NEXT_PUBLIC_VERIFIER2
      : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER2;
    if (!verifier2BaseUrl) return;

    fetch(`${verifier2BaseUrl}/transaction-data-profiles`)
      .then((res) => res.json())
      .then((data: TransactionDataProfile[]) => {
        setProfiles(data);
        if (data.length > 0) {
          setSelectedProfileType(data[0].type);
        }
      })
      .catch(() => {});
  }, [env.NEXT_PUBLIC_VERIFIER2]);

  function handleCancel() {
    router.push('/');
  }

  const params = router.query;
  const idsRaw = params.ids;
  const idsToIssue = (Array.isArray(idsRaw) ? idsRaw : typeof idsRaw === "string" ? [idsRaw] : [])
    .flatMap((value) => value.split(","))
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
  const idsToIssueKey = idsToIssue.join(',');
  const [credentialsToIssue, setCredentialsToIssue] = useState<
    AvailableCredential[]
  >([]);
  const selectedTransactionFormat = credentialsToIssue[0]?.selectedFormat?.toString() ?? '';
  const selectedProtocolFormat = selectedTransactionFormat ? mapFormat(selectedTransactionFormat) : '';
  const TRANSACTION_DATA_FORMATS = ['vc+sd-jwt', 'mso_mdoc'];
  const formatSupportsTransactionData = !selectedProtocolFormat || TRANSACTION_DATA_FORMATS.includes(selectedProtocolFormat);
  const isUnsupportedTransactionFormat =
    transactionDataEnabled &&
    credentialsToIssue.length === 1 &&
    selectedTransactionFormat.length > 0 &&
    !formatSupportsTransactionData;

  const selectedProfile = profiles.find((p) => p.type === selectedProfileType)
    ?? profiles[0] ?? null;

  useEffect(() => {
    if (!selectedProfile) return;
    const defaults: Record<string, string> = {};
    for (const field of selectedProfile.fields) {
      defaults[field] = fieldValues[field] ?? '';
    }
    setFieldValues(defaults);
  }, [selectedProfileType]);

  React.useEffect(() => {
    setCredentialsToIssue(
      AvailableCredentials.filter((cred) => {
        for (const id of idsToIssue) {
          if (id === cred.id.toString()) {
            return true;
          }
        }
        return false;
      })
    );
  }, [AvailableCredentials, idsToIssueKey]);

  function handleVerify() {
    const vps = [];
    if (!transactionDataEnabled) {
      if (signaturePolicy) {
        vps.push('signature');
      }
      if (expiredPolicy) {
        vps.push('expired');
      }
      if (notBeforePolicy) {
        vps.push('not-before');
      }
      if (webhookPolicy) {
        if (webhook.length == 0) {
          alert('Please enter a webhook url');
          return;
        }
        vps.push('webhook=' + webhook);
      }
    } else if (credentialsToIssue.length !== 1) {
      alert('Transaction data verification currently requires exactly one selected credential.');
      return;
    } else if (!formatSupportsTransactionData) {
      alert('The selected credential format does not support transaction data.');
      return;
    }

    const queryParams = new URLSearchParams();
    queryParams.append('ids', idsToIssue.join(','));
    if (vps.length) {
      queryParams.append('vps', vps.join(','));
    }

    queryParams.append(
      'format',
      (credentialsToIssue[0]?.selectedFormat ?? 'JWT + W3C VC') as string
    );

    if (transactionDataEnabled && selectedProfile) {
      queryParams.append('tx', '1');
      queryParams.append('tx_type', selectedProfile.type);
      for (const field of selectedProfile.fields) {
        queryParams.append(`tx_${field}`, fieldValues[field] ?? '');
      }
    }

    router.push(`/verify?${queryParams.toString()}`);
  }

  if (params.ids === undefined) {
    return <Button onClick={() => router.push('/')}>Select Credentials</Button>;
  }

  return (
    <>
      <h1 className="text-3xl text-gray-900 text-center font-bold">
        Customise Verification
      </h1>
      <p className="mt-3 text-gray-600">
        Select credential format and policies which should be checked
      </p>
      <hr className="mt-8" />
      <h3 className="text-gray-500 text-left mt-2 font-semibold">
        Credential Formats
      </h3>
      <div className="mt-12"></div>
      {/*START*/}
      <div className="flex flex-col gap-6">
        {credentialsToIssue.map((credential) => (
          <RowCredential
            credentialToEdit={credential}
            credentialsToIssue={credentialsToIssue}
            setCredentialsToIssue={setCredentialsToIssue}
            key={credential.title}
          />
        ))}
      </div>
      {/*END*/}
      <div className="mt-12"></div>
      <hr className="text-green-900 border border-[0.5px] border-gray-100" />
      <h3 className="text-gray-500 text-left mt-2 font-semibold">
        Transaction Data
      </h3>
      <div className="mt-6">
        <label className="inline-flex items-center gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={transactionDataEnabled}
            onChange={(event) => setTransactionDataEnabled(event.target.checked)}
            className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-600"
          />
          <span className="text-gray-900">Enable transaction data</span>
        </label>
      </div>
      {isUnsupportedTransactionFormat && (
        <p className="text-sm text-red-600 text-left mt-2">
          The selected format <span className="font-medium">{selectedTransactionFormat}</span> does not support transaction data.
        </p>
      )}
      {transactionDataEnabled && profiles.length > 0 && (
        <div className="mt-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">Profile</label>
          <select
            value={selectedProfileType}
            onChange={(e) => setSelectedProfileType(e.target.value)}
            className="block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm p-2 border"
          >
            {profiles.map((profile) => (
              <option key={profile.type} value={profile.type}>
                {profile.displayName}
              </option>
            ))}
          </select>
        </div>
      )}
      {transactionDataEnabled && selectedProfile && (
        <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-4">
          {selectedProfile.fields.map((field) => (
            <InputField
              key={field}
              error={false}
              label={field.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())}
              value={fieldValues[field] ?? ''}
              name={`tx_${field}`}
              type="text"
              placeholder=""
              onChange={(value) => setFieldValues((prev) => ({ ...prev, [field]: value }))}
              showLabel={true}
            />
          ))}
        </div>
      )}
      <div className="mt-12"></div>
      <hr className="text-green-900 border border-[0.5px] border-gray-100" />
      <h3 className="text-gray-500 text-left mt-2 font-semibold">
        Credential Policies
      </h3>
      {transactionDataEnabled && (
        <p className="text-sm text-gray-500 text-left mt-2">
          Policy toggles are disabled in transaction mode because transaction and presentation policies are applied automatically.
        </p>
      )}
      <div className="flex flex-row justify-start mt-8">
        <div className={`flex flex-col gap-3 w-full ${transactionDataEnabled ? 'opacity-60 pointer-events-none' : ''}`}>
          <PolicyListItem
            name="Signature Policy"
            value={signaturePolicy}
            onChange={setSignaturePolicy}
          />
          <PolicyListItem
            name="Expired Policy"
            value={expiredPolicy}
            onChange={setExpiredPolicy}
          />
          <PolicyListItem
            name="Not Before Policy"
            value={notBeforePolicy}
            onChange={setNotBeforePolicy}
          />
          <div className="sm:flex justify-between">
            <PolicyListItem
              name="Webhook Policy"
              value={webhookPolicy}
              onChange={setWebhookPolicy}
            />
            <InputField
              error={false}
              label=""
              value={webhook}
              name=""
              type=""
              placeholder="https://webhook.site/..."
              onChange={setWebhook}
              disabled={transactionDataEnabled}
            />
          </div>
        </div>
      </div>
      <div className="mt-12" />
      <hr />
      <div className="flex flex-row justify-center gap-3 mt-14">
        <Button onClick={handleCancel} style="link" color="secondary">
          Cancel
        </Button>
        <Button onClick={handleVerify}>Verify</Button>
      </div>
      <div className="flex flex-col items-center mt-12">
        <div className="flex flex-row gap-2 items-center content-center text-sm text-center text-gray-500">
          <p className="">Secured by walt.id</p>
          <WaltIcon height={15} width={15} type="gray" />
        </div>
      </div>
    </>
  );
}
