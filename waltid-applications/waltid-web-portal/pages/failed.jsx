import WaltIcon from "@/components/walt/logo/WaltIcon";
import {XCircleIcon} from "@heroicons/react/24/outline";
import {useState} from "react";

export default function Failed() {
  const [verificationPolicies, setVerificationPolicies] = useState([
    'SignaturePolicy',
    'ChallengePolicy',
    'PresentationDefinitionPolicy',
  ]);

  return (
    <div className="h-screen flex justify-center items-center bg-gray-50">
      <div className="relative w-full h-full sm:h-auto sm:w-10/12 md:w-8/12 lg:w-6/12 text-center shadow-2xl rounded-lg pt-8 pb-8 px-10 bg-white">
        <h1 className="text-3xl text-gray-900 text-center font-bold mb-10">
          Presented Credentials
        </h1>
        <div className="flex flex-col items-center">
          <div className="w-80 h-60 sm:h-[225px] sm:w-[400px]">
            <div className="flex h-full w-full flex-col drop-shadow-sm rounded-xl py-7 px-8 text-gray-100 overflow-hidden bg-gradient-to-r from-red-700 to-red-900 z-[-2]">
              <div className="flex flex-row">
                <WaltIcon height={35} width={35} outline type="white" />
              </div>
              <div className="mb-8 mt-12">
                <h6 className={'text-2xl font-bold '}>Failed to verify</h6>
              </div>
            </div>
          </div>
        </div>
        <div className="mt-10 px-12">
          <div className="xs:grid xs:grid-cols-2 items-center justify-center">
            {verificationPolicies.map((policy, index) => {
              return (
                <div
                  key={policy}
                  className={`flex items-center gap-3 ${
                    index % 2 == 1 ? 'justify-self-end' : ''
                  }`}
                >
                  <XCircleIcon className="h-4 text-red-600" />
                  <div>{policy}</div>
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
