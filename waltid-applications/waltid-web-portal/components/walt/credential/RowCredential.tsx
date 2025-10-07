import {AvailableCredential, CredentialFormats, DIDMethods} from "@/types/credentials";
import EditCredentialModal from "../modal/EditCredentialModal";
import {PencilSquareIcon} from "@heroicons/react/24/outline";
import Dropdown from "@/components/walt/forms/Dropdown";
import React from "react";

type Props = {
  credentialToEdit: AvailableCredential;
  credentialsToIssue: AvailableCredential[];
  setCredentialsToIssue: (credentials: AvailableCredential[]) => void;
};

export default function RowCredential({
  credentialToEdit,
  credentialsToIssue,
  setCredentialsToIssue,
}: Props) {
  const [credentialSubject, setCredentialSubject] = React.useState(
    credentialToEdit.offer.credentialSubject
  );
  const [selectedFormat, setSelectedFormat] = React.useState(
    CredentialFormats[0]
  );
  const [selectedDID, setSelectedDID] = React.useState(DIDMethods[0]);
  const [modalVisible, setModalVisible] = React.useState(false);

  React.useEffect(() => {
    setCredentialsToIssue(
      credentialsToIssue.map((credential) => {
        if (credential.offer.id == credentialToEdit.offer.id) {
          let updatedCredential = { ...credential };

          if (credentialSubject !== credential.offer.credentialSubject) {
            updatedCredential.offer.credentialSubject = credentialSubject;
          }
          updatedCredential.selectedFormat = selectedFormat;
          updatedCredential.selectedDID = selectedDID;

          return updatedCredential;
        } else {
          let updatedCredential = { ...credential };
          updatedCredential.selectedFormat = selectedFormat;
          return updatedCredential;
        }
      })
    );
  }, [credentialSubject, selectedFormat, selectedDID]);

  return (
    <>
      <div className="lg:flex flex-row gap-5 justify-between">
        <div className="flex flex-row gap-5 items-center">
          <div className="hidden sm:block bg-primary-400 w-[45px] h-[28px] rounded-lg"></div>
          <span className="text-gray-900 text-lg text-left">
            {credentialToEdit.title}
          </span>
          <PencilSquareIcon
            onClick={() => {
              setModalVisible(true);
            }}
            className="h-4 text-gray-500 hover:text-primary-400 cursor-pointer"
          />
        </div>
        <div className="flex flex-row items-center gap-3 lg:w-5/12">
          <div className="hidden lg:block w-[2px] h-[2px] bg-gray-200"></div>
          <div className="w-full">
            <Dropdown
              values={CredentialFormats}
              selected={selectedFormat}
              setSelected={setSelectedFormat}
            />
          </div>
          <div className="w-full">
            <Dropdown
              values={DIDMethods}
              selected={selectedDID}
              setSelected={setSelectedDID}
            />
          </div>
        </div>
      </div>
      <EditCredentialModal
        show={modalVisible}
        onClose={() => {
          setModalVisible(false);
        }}
        credentialSubject={credentialSubject}
        setCredentialSubject={setCredentialSubject}
      />
    </>
  );
}
