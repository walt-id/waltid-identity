import RowCredential from "@/components/walt/credential/RowCredential";
import PolicyListItem from "@/components/walt/policy/PolicyListItem";
import {AvailableCredential} from "@/types/credentials";
import WaltIcon from "@/components/walt/logo/WaltIcon";
import InputField from "@/components/walt/forms/Input";
import Button from "@/components/walt/button/Button";
import React, {useContext, useState} from "react";
import {CredentialsContext} from "@/pages/_app";
import {useRouter} from "next/router";
import {TRANSACTION_DATA_SUPPORTED_SELECTED_FORMAT, isTransactionDataSupportedSelectedFormat} from "@/utils/transactionData";

export default function VerificationSection() {
  const router = useRouter();
  const [AvailableCredentials] = useContext(CredentialsContext);

  const [signaturePolicy, setSignaturePolicy] = useState<boolean>(true);
  const [expiredPolicy, setExpiredPolicy] = useState<boolean>(true);
  const [notBeforePolicy, setNotBeforePolicy] = useState<boolean>(true);
  const [webhookPolicy, setWebhookPolicy] = useState<boolean>(false);
  const [webhook, setWebhook] = useState<string>('');
  const [transactionDataEnabled, setTransactionDataEnabled] = useState<boolean>(false);
  const [transactionAmount, setTransactionAmount] = useState<string>('42.00');
  const [transactionCurrency, setTransactionCurrency] = useState<string>('EUR');
  const [transactionPayee, setTransactionPayee] = useState<string>('ACME Corp');
  const [transactionReference, setTransactionReference] = useState<string>('INV-2026-042');

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
  const isUnsupportedTransactionFormat =
    transactionDataEnabled &&
    credentialsToIssue.length === 1 &&
    selectedTransactionFormat.length > 0 &&
    !isTransactionDataSupportedSelectedFormat(selectedTransactionFormat);

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
    } else if (!isTransactionDataSupportedSelectedFormat(credentialsToIssue[0]?.selectedFormat?.toString())) {
      alert('Transaction data verification currently supports only SD-JWT + IETF SD-JWT VC in this flow.');
      return;
    }

    const params = new URLSearchParams();
    params.append('ids', idsToIssue.join(','));
    if (vps.length) {
      params.append('vps', vps.join(','));
    }

    params.append(
      'format',
      (credentialsToIssue[0]?.selectedFormat ?? 'JWT + W3C VC') as string
    );

    if (transactionDataEnabled) {
      params.append('tx', '1');
      params.append('tx_amount', transactionAmount);
      params.append('tx_currency', transactionCurrency.toUpperCase());
      params.append('tx_payee', transactionPayee);
      params.append('tx_reference', transactionReference);
    }

    router.push(`/verify?${params.toString()}`);
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
      {transactionDataEnabled && (
        <p className="text-sm text-gray-500 text-left mt-3">
          Transaction data is currently supported for format: <span className="font-medium">SD-JWT + IETF SD-JWT VC</span>.
        </p>
      )}
      {isUnsupportedTransactionFormat && (
        <p className="text-sm text-red-600 text-left mt-2">
          Selected format <span className="font-medium">{selectedTransactionFormat}</span> does not support transaction data in this flow.
        </p>
      )}
      {transactionDataEnabled && (
        <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-4">
          <InputField
            error={false}
            label="Amount"
            value={transactionAmount}
            name="tx_amount"
            type="text"
            placeholder="42.00"
            onChange={setTransactionAmount}
            showLabel={true}
          />
          <InputField
            error={false}
            label="Currency"
            value={transactionCurrency}
            name="tx_currency"
            type="text"
            placeholder="EUR"
            onChange={(value) => setTransactionCurrency(value.toUpperCase())}
            showLabel={true}
          />
          <InputField
            error={false}
            label="Payee"
            value={transactionPayee}
            name="tx_payee"
            type="text"
            placeholder="ACME Corp"
            onChange={setTransactionPayee}
            showLabel={true}
          />
          <InputField
            error={false}
            label="Reference"
            value={transactionReference}
            name="tx_reference"
            type="text"
            placeholder="INV-2026-042"
            onChange={setTransactionReference}
            showLabel={true}
          />
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
