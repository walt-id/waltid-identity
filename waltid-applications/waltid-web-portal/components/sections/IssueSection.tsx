import RowCredential from '@/components/walt/credential/RowCredential';
import Dropdown from '@/components/walt/forms/Dropdown';
import {AuthenticationMethods, VpProfiles} from  '@/types/credentials'
import Checkbox from '@/components/walt/forms/Checkbox';
import InputField from '@/components/walt/forms/Input';
import Button from '@/components/walt/button/Button';
import WaltIcon from '@/components/walt/logo/WaltIcon';
import { CredentialsContext, EnvContext } from '@/pages/_app';
import React, { useState } from 'react';
import { useRouter } from 'next/router';
import { getOfferUrl } from '@/utils/getOfferUrl';
import { sendToWebWallet } from '@/utils/sendToWebWallet';
import nextConfig from '@/next.config';
import { AvailableCredential } from '@/types/credentials';
import { LockClosedIcon } from '@heroicons/react/24/outline';

export default function IssueSection() {
  const env = React.useContext(EnvContext);
  const [AvailableCredentials] = React.useContext(CredentialsContext);

  const [selectedAuthenticationMethod, setSelectedAuthenticationMethod] = React.useState(AuthenticationMethods[0]);
  const [requirePin, setRequirePin] = useState<boolean>(false);
  const [pin, setPin] = useState<string>('0235');
  const [requireVpRequestValue, setRequireVpRequestValue] = useState<boolean>(false);
  const [vpRequestValue, setVpRequestValue] = useState<string>('NaturalPersonVerifiableID');
  const [requireVpProfile, setRequireVpProfile] = useState<boolean>(false);
  const [selectedVpProfile, setSelectedVpProfile] = React.useState(VpProfiles[0]);

  const router = useRouter();
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

  function handleCancel() {
    router.push('/');
  }

  async function handleIssue() {
    if (checkCallbackUrlParameter()) {
      const offer = await getOfferUrl(credentialsToIssue, env.NEXT_PUBLIC_VC_REPO ?? nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VC_REPO, env.NEXT_PUBLIC_ISSUER ?? nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_ISSUER);
      sendToWebWallet(decodeURI(params.callback!.toString()), 'api/siop/initiateIssuance', offer.data);
    } else {
      console.log("show qr-offer");
      localStorage.setItem('offer', JSON.stringify(credentialsToIssue));
      let url = `/offer?ids=${idsToIssue.join(',')}`;
      url = url + `&authenticationMethod=${selectedAuthenticationMethod}`;
      if (requireVpRequestValue && vpRequestValue?.trim().length) {
        url = url + `&vpRequestValue=${vpRequestValue}`;
      }
      if (requireVpProfile && selectedVpProfile?.trim().length) {
        url = url + `&vpProfile=${selectedVpProfile}`;
      }

      await router.push(url);
    }
  }

  function checkCallbackUrlParameter(): Boolean {
    const callback = params.callback;
    return !(callback === undefined || callback === null || callback === "");
  }

  if (params.ids === undefined) {
    return <Button onClick={() => router.push('/')}>Select Credentials</Button>;
  }

  return (
    <>
      <h1 className="text-3xl text-gray-900 text-center font-bold">
        Customise Issuance
      </h1>
      <p className="mt-3 text-gray-600">
        Adjust credential data, format and issuance security
      </p>
      <hr className="mt-8" />
      <h3 className="text-gray-500 text-left mt-2 font-semibold">
        Credential Configuration
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
      <div className="mt-10"></div>
      <hr className="text-green-900 border border-[0.5px] border-gray-100" />
      <h3 className="text-gray-500 text-left mt-2 font-semibold">
        Security Settings
      </h3>
      <div className="mt-5 flex flex-col sm:flex-row justify-between">
        <div className="mt-2">
          <div className="flex flex-row gap-2 items-center">
            <LockClosedIcon className="h-5" />
            {/* <div className="hidden sm:block bg-primary-400 w-[45px] h-[28px] rounded-lg"></div> */}
            <span> Authentication Method</span>
          </div>
        </div>
        <Dropdown 
              values={AuthenticationMethods}
              selected={selectedAuthenticationMethod}
              setSelected={setSelectedAuthenticationMethod}
            />
      </div>
      <div className="mt-3 flex flex-col sm:flex-row justify-between">
        <div className="">
          <Checkbox value={requirePin} onChange={setRequirePin}>
            Require User Pin
          </Checkbox>
        </div>
        <InputField
          error={false}
          label="Test"
          value={pin}
          name="test"
          type="id"
          placeholder=""
          onChange={setPin}
        />
      </div>
      <div className="mt-1 flex flex-col sm:flex-row justify-between">
        <div className="">
          <Checkbox value={requireVpRequestValue} onChange={setRequireVpRequestValue}>
            VP Token Requested Value
          </Checkbox>
        </div>
        <InputField
          error={false}
          label="Test"
          value={vpRequestValue}
          name="test"
          type="id"
          placeholder=""
          onChange={setVpRequestValue}
        />
      </div>

      <div className="mt-1 flex flex-col sm:flex-row justify-between">
       <div className="">
          <Checkbox value={requireVpProfile} onChange={setRequireVpProfile}>
            VP Token Requested Profile
          </Checkbox>
        </div>
        <Dropdown 
              values={VpProfiles}
              selected={selectedVpProfile}
              setSelected={setSelectedVpProfile}
            />
      </div>

      <hr className="my-5" />
      <div className="flex flex-row justify-center gap-3 mt-14">
        <Button onClick={handleCancel} style="link" color="secondary">
          Cancel
        </Button>
        <Button
          disabled={!(credentialsToIssue.length > 0 && (credentialsToIssue.length < 2 || credentialsToIssue.filter((cred) => cred.selectedFormat === "SD-JWT + VCDM").length === 0))}
          onClick={handleIssue}>Issue</Button>
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
