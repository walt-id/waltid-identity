import RowCredential from '@/components/walt/credential/RowCredential';
import PolicyListItem from '@/components/walt/policy/PolicyListItem';
import { AvailableCredential } from '@/types/credentials';
import WaltIcon from '@/components/walt/logo/WaltIcon';
import InputField from '@/components/walt/forms/Input';
import Button from '@/components/walt/button/Button';
import React, { useState, useContext } from 'react';
import { CredentialsContext } from '@/pages/_app';
import { useRouter } from 'next/router';

export default function VerificationSection() {
  const router = useRouter();
  const [AvailableCredentials] = useContext(CredentialsContext);

  const [signaturePolicy, setSignaturePolicy] = useState<boolean>(true);
  const [expiredPolicy, setExpiredPolicy] = useState<boolean>(true);
  const [notBeforePolicy, setNotBeforePolicy] = useState<boolean>(true);
  const [webhookPolicy, setWebhookPolicy] = useState<boolean>(false);
  const [webhook, setWebhook] = useState<string>('');

  function handleCancel() {
    router.push('/');
  }

  const params = router.query;

  const idsToIssue = (params as unknown as { ids: string }).ids?.split(',') ? (params as unknown as { ids: string }).ids?.split(',') : [(params as unknown as { ids: string }).ids];
  const [credentialsToIssue, setCredentialsToIssue] = useState<AvailableCredential[]>([]);

  React.useEffect(() => {
    setCredentialsToIssue(AvailableCredentials.filter((cred) => {
      for (const id of idsToIssue) {
        if (id.toString() == cred.id.toString()) {
          return true;
        }
      }
      return false;
    }
    ));
  }, [AvailableCredentials]);

  function handleVerify() {
    const vps = [];
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

    const params = new URLSearchParams();
    params.append('ids', idsToIssue.join(','));
    params.append('vps', vps.join(','));

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
            key={credential.title} />
        ))}
      </div>
      {/*END*/}
      <div className="mt-12"></div>
      <hr className="text-green-900 border border-[0.5px] border-gray-100" />
      <h3 className="text-gray-500 text-left mt-2 font-semibold">
        Credential Policies
      </h3>
      <div className="flex flex-row justify-start mt-8">
        <div className="flex flex-col gap-3 w-full">
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
          <div className='sm:flex justify-between'>
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
