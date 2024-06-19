import QRCode from 'react-qr-code';
import Button from '@/components/walt/button/Button';
import Icon from '@/components/walt/logo/Icon';
import WaltIcon from '@/components/walt/logo/WaltIcon';
import { useState, useEffect, useContext } from 'react';
import { useRouter } from 'next/router';
import { CredentialsContext, EnvContext } from '@/pages/_app';
import { sendToWebWallet } from '@/utils/sendToWebWallet';
import { getOfferUrl } from '@/utils/getOfferUrl';
import nextConfig from '@/next.config';
import BackButton from '@/components/walt/button/BackButton';

const BUTTON_COPY_TEXT_DEFAULT = 'Copy offer URL';
const BUTTON_COPY_TEXT_COPIED = 'Copied';

export default function Offer() {
  const [AvailableCredentials] = useContext(CredentialsContext);
  const env = useContext(EnvContext);
  const router = useRouter();

  const [offerURL, setOfferURL] = useState('');
  const [loading, setLoading] = useState(true);
  const [copyText, setCopyText] = useState(BUTTON_COPY_TEXT_DEFAULT);

  function handleCancel() {
    router.push('/');
  }

  useEffect(() => {
    const getOfferURL = async () => {
      let credentials;
      if (localStorage.getItem('offer')){
        credentials = JSON.parse(localStorage.getItem('offer')!);
        localStorage.removeItem('offer');
      }
      else{
        let ids = router.query.ids?.toString().split(',') ?? [];
        credentials = AvailableCredentials.filter((cred) => {
          for (const id of ids) {
            if (id.toString() == cred.id.toString()) {
              return true;
            }
          }
          return false;
        });
      }
      if (credentials) {
        const response = await getOfferUrl(credentials, env.NEXT_PUBLIC_VC_REPO ? env.NEXT_PUBLIC_VC_REPO : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VC_REPO, env.NEXT_PUBLIC_ISSUER ? env.NEXT_PUBLIC_ISSUER : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_ISSUER);
        setOfferURL(response.data);
        setLoading(false);
      }
    };
    getOfferURL();
  }, [router.query.credentialId]);

  async function copyCurrentURLToClipboard() {
    navigator.clipboard.writeText(offerURL).then(
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
    sendToWebWallet(env.NEXT_PUBLIC_WALLET ? env.NEXT_PUBLIC_WALLET : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_WALLET as string, 'api/siop/initiateIssuance', offerURL);
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
          Claim Your Credential
        </h1>
        <div className="flex justify-center">
          {
            loading ?
              <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-gray-900 my-10"></div>
              :
              <QRCode
                className="h-full max-h-[220px] my-10"
                value={offerURL}
                viewBox={'0 0 256 256'}
              />
          }
        </div>
        <div className="sm:flex flex-row gap-5 justify-center">
          <Button style="link" onClick={copyCurrentURLToClipboard}>
            {copyText}
          </Button>
          <Button onClick={openWebWallet} style="button">Open Web Wallet</Button>
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
