import { useState, useEffect, useContext } from 'react';
import WaltIcon from '@/components/walt/logo/WaltIcon';
import Button from '@/components/walt/button/Button';
import { CredentialsContext, EnvContext } from '@/pages/_app';
import Icon from '@/components/walt/logo/Icon';
import { useRouter } from 'next/router';
import QRCode from 'react-qr-code';
import axios from 'axios';
import { sendToWebWallet } from '@/utils/sendToWebWallet';
import nextConfig from '@/next.config';
import BackButton from '@/components/walt/button/BackButton';

const BUTTON_COPY_TEXT_DEFAULT = 'Copy offer URL';
const BUTTON_COPY_TEXT_COPIED = 'Copied';

export default function Verification() {
  const env = useContext(EnvContext)
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
      let vps = router.query.vps?.toString().split(',') ?? [];
      let ids = router.query.ids?.toString().split(',') ?? [];
      let credentials = AvailableCredentials.filter((cred) => {
        for (const id of ids) {
          if (id.toString() == cred.id.toString()) {
            return true;
          }
        }
        return false;
      });
      const credentialType = credentials.map((credential) => {
        return credential.offer.type[credential.offer.type.length - 1]
      });

      const response = await axios.post(
        `${env.NEXT_PUBLIC_VERIFIER ? env.NEXT_PUBLIC_VERIFIER : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER}/openid4vc/verify`,
        {
          "request_credentials": credentialType,
          "vc_policies": vps.map((vp) => {
            if (vp.includes('=')) {
              return {
                "policy": vp.split('=')[0],
                "args": vp.split('=')[1]
              }
            } else {
              return vp;
            }
          })
        },
        {
          headers: {
            "successRedirectUri": `${window.location.origin}/success/$id`,
            "errorRedirectUri": `${window.location.origin}/success/$id`,
          },
        }
      );
      setverifyURL(response.data);
      setLoading(false);
    };
    getverifyURL();
  }, []);

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
    sendToWebWallet(env.NEXT_PUBLIC_WALLET ? env.NEXT_PUBLIC_WALLET : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_WALLET, 'api/siop/initiatePresentation', verifyURL);
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
          {
            loading ?
              <div className="animate-spin rounded-full h-32 w-32 border-b-2 border-gray-900 my-10"></div>
              :
              <QRCode
                className="h-full max-h-[220px] my-10"
                value={verifyURL}
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
