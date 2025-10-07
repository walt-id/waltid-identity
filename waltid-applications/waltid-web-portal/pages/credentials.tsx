import {useRouter} from "next/router";
import {ViewfinderCircleIcon} from "@heroicons/react/24/outline";
import WaltIcon from "@/components/walt/logo/WaltIcon";
import {useEffect, useState} from "react";
import SelectButton from "@/components/walt/forms/SelectButton";
import {TbRubberStamp} from "react-icons/tb";
import IssueSection from "@/components/sections/IssueSection";
import VerificationSection from "@/components/sections/VerificationSection";
import {addQueryParamToCurrentURL} from "@/lib/helper/addQueryParamToCurrentURL";

const ISSUE_MODE = 'issuance';
const VERIFY_MODE = 'verification';

type PortalMode = typeof ISSUE_MODE | typeof VERIFY_MODE;

export default function Credentials() {
  const [portalType, setPortalType] = useState<PortalMode>(ISSUE_MODE);

  const issuanceMode = portalType === ISSUE_MODE;
  const verificationMode = portalType === VERIFY_MODE;

  const router = useRouter();
  const params = router.query;

  function handlePortalModeChange() {
    if (portalType === VERIFY_MODE) {
      setPortalType(ISSUE_MODE);
      addQueryParamToCurrentURL('mode', ISSUE_MODE);
    } else {
      setPortalType(VERIFY_MODE);
      addQueryParamToCurrentURL('mode', VERIFY_MODE);
    }
  }

  useEffect(() => {
    const { mode } = params;
    if (mode !== undefined) {
      if ((mode as unknown as PortalMode) === ISSUE_MODE) {
        setPortalType(ISSUE_MODE);
      } else {
        setPortalType(VERIFY_MODE);
      }
    }
  }, [params]);

  return (
    <div className="flex flex-col justify-center items-center">
      <div
        className="mt-10 flex flex-row justify-center cursor-pointer"
        onClick={() => router.push('/')}
      >
        <WaltIcon height={35} width={35} type="primary" />
      </div>
      <div className="mt-10 bg-gray-100 rounded-lg py-2.5 px-5">
        <div className="flex flex-row gap-5">
          <SelectButton
            icon={TbRubberStamp}
            selected={issuanceMode}
            onClick={handlePortalModeChange}
          >
            Issue
          </SelectButton>
          <SelectButton
            icon={ViewfinderCircleIcon}
            selected={verificationMode}
            onClick={handlePortalModeChange}
          >
            Verify
          </SelectButton>
        </div>
      </div>

      <div className="w-11/12 md:w-7/12 text-center shadow-2xl rounded-lg mt-5 pt-8 pb-8 px-10 bg-white max-w-[960px]">
        {issuanceMode && <IssueSection />}
        {verificationMode && <VerificationSection />}
      </div>
    </div>
  );
}
