import WaltIcon from '@/components/walt/logo/WaltIcon';
import { CheckCircleIcon } from '@heroicons/react/24/outline';
import { useEffect, useState, useContext } from 'react';
import { useRouter } from 'next/router';
import axios from 'axios';
import nextConfig from '@/next.config';
import Modal from '@/components/walt/modal/BaseModal';
import { EnvContext } from '@/pages/_app';

export default function Success() {
  const env = useContext(EnvContext);
  const router = useRouter();

  const [policyResults, setPolicyResults] = useState<Array<{
    policies: Array<{
      policy: string;
      is_success: boolean;
    }>
  }>>([]);
  const [credentials, setCredentials] = useState<Array<{
    type: Array<string>;
    credentialSubject: {
      [key: string]: string;
    };
  }>>([]);
  const [index, setIndex] = useState<number>(0);
  const [modal, setModal] = useState<boolean>(false);

  function parseJwt(token: string) {
    return JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString());
  }

  useEffect(() => {
    if (!router.isReady) return;

    axios
      .get(
        `${env.NEXT_PUBLIC_VERIFIER ? env.NEXT_PUBLIC_VERIFIER : nextConfig.publicRuntimeConfig!.NEXT_PUBLIC_VERIFIER}/openid4vc/session/${router.query.sessionId}`
      )
      .then((response) => {
        let vcs = parseJwt(response.data.tokenResponse.vp_token).vp.verifiableCredential;
        setCredentials(vcs.map((vc: string) => {
          let split = vc.split('~');
          let parsed = parseJwt(split[0]);

          if (split.length === 1) return parsed.vc ? parsed.vc : parsed;
          else {
            let credentialWithSdJWTAttributes = { ...parsed };
            split.slice(1).forEach((item) => {
              let parsedItem = JSON.parse(Buffer.from(item, 'base64').toString())
              credentialWithSdJWTAttributes.credentialSubject = {
                [parsedItem[1]]: parsedItem[2],
                ...credentialWithSdJWTAttributes.credentialSubject
              }
              // credentialWithSdJWTAttributes.credentialSubject._sd.map((sdItem: string) => {
              //   if (sdItem === parsedItem[0]) {
              //     return `${parsedItem[1]}: ${parsedItem[2]}`
              //   }
              //   return sdItem;
              // })
            });
            return credentialWithSdJWTAttributes;
          }
        }));
        setPolicyResults(response.data.policyResults.results);
      });
  }, [router.isReady, env]);

  return (
    <div className="h-screen flex justify-center items-center bg-gray-50">
      <Modal show={modal} securedByWalt={false} onClose={() => setModal(false)}>
        <div className="flex flex-col items-center">
          <div className="w-full">
            <textarea
              value={JSON.stringify(credentials[index]?.credentialSubject, null, 4)}
              disabled={true}
              className="w-full h-48 border-2 border-gray-300 rounded-md px-2"
            />
          </div>
        </div>
      </Modal>
      <div className="relative w-full h-full sm:h-auto sm:w-10/12 md:w-8/12 lg:w-6/12 text-center shadow-2xl rounded-lg pt-8 pb-8 px-10 bg-white">
        <h1 className="text-3xl text-gray-900 text-center font-bold mb-10">
          Presented Credentials
        </h1>
        <div className="flex items-center justify-center">
          {
            index !== 0 &&
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
                  strokeLinejoin="round" strokeWidth={2}
                  d="M15 19l-7-7 7-7"
                />
              </svg>
            </button>
          }
          <div className="group w-80 h-64 sm:h-[225px] sm:w-[400px] [perspective:1000px]">
            <div className="relative h-full w-full rounded-xl shadow-xl transition-all duration-500 [transform-style:preserve-3d] group-hover:[transform:rotateY(180deg)]">
              <div className="absolute inset-0">
                <div className="flex h-full w-full flex-col drop-shadow-sm rounded-xl py-7 px-8 text-gray-100 cursor-pointer overflow-hidden bg-gradient-to-r from-green-700 to-green-900 z-[-2]">
                  <div className="flex flex-row">
                    <WaltIcon height={35} width={35} outline type="white" />
                  </div>
                  <div className="mb-8 mt-12">
                    <h6 className={'text-2xl font-bold '}>
                      {credentials[index]?.type[credentials[index]?.type.length - 1].replace(/([a-z0-9])([A-Z])/g, '$1 $2')}</h6>
                  </div>
                </div>
              </div>
              <div className="absolute inset-0 h-full w-full rounded-xl bg-white p-5 text-slate-200 [transform:rotateY(180deg)] [backface-visibility:hidden] overflow-y-scroll">
                {credentials[index] && Object.keys(credentials[index].credentialSubject).map((key) => {
                  if (typeof (credentials[index].credentialSubject[key]) === 'string' && credentials[index].credentialSubject[key].length > 0 && credentials[index].credentialSubject[key].length < 20) {
                    return {
                      key: (key.charAt(0).toUpperCase() + key.slice(1)).replace(/([a-z0-9])([A-Z])/g, '$1 $2'),
                      value: credentials[index].credentialSubject[key]
                    }
                  }
                }).filter((item) => item !== undefined).length > 0 &&
                  <>
                    {
                      Object.keys(credentials[index].credentialSubject).map((key) => {
                        if (typeof (credentials[index].credentialSubject[key]) === 'string' && credentials[index].credentialSubject[key].length > 0 && credentials[index].credentialSubject[key].length < 20) {
                          return {
                            key: (key.charAt(0).toUpperCase() + key.slice(1)).replace(/([a-z0-9])([A-Z])/g, '$1 $2'),
                            value: credentials[index].credentialSubject[key]
                          }
                        }
                      }).map((item) => {
                        return (
                          <div key={index} className="flex flex-row py-1">
                            <div className="text-gray-600 text-left w-1/2 capitalize leading-[1.1]">
                              {item?.key}
                            </div>
                            <div className="text-slate-800 text-left w-1/2 text-[#313233]">
                              {item?.value}
                            </div>
                          </div>
                        )
                      })
                    }
                  </>
                }
                <div className="flex flex-row py-1">
                  <button onClick={() => setModal(true)} className="text-gray-500 text-center w-full capitalize leading-[1.1] underline">
                    View Credential In JSON
                  </button>
                </div>
              </div>
            </div>
          </div>
          {
            index !== credentials.length - 1 &&
            <button
              onClick={() => setIndex(index + 1)}
              className="text-gray-500 hover:text-gray-900 focus:outline-none absolute right-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6 ml-2" fill="none"
                viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M9 5l7 7-7 7" />
              </svg>
            </button>
          }
        </div>
        <div className="mt-10 px-12">
          <div className="flex flex-row items-center justify-center mb-5 text-gray-500">
            The VP was verified along with:
          </div>
          <div className="xs:grid xs:grid-cols-2 items-center justify-center">
            {
              policyResults[index + 1]?.policies.map((policy) => {
                return {
                  name: policy.policy.charAt(0).toUpperCase() + policy.policy.slice(1) + ' Policy',
                  is_success: policy.is_success
                };
              }).map((policy, index) => {
                return (
                  <div
                    key={policy.name}
                    className={`flex items-center gap-3 ${index % 2 == 1 ? 'justify-self-end' : ''}`}
                  >
                    {
                      policy.is_success ?
                        <CheckCircleIcon className="h-4 text-green-600" />
                        :
                        <CheckCircleIcon className="h-4 text-red-600" />
                    }
                    <div>{policy.name}</div>
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
