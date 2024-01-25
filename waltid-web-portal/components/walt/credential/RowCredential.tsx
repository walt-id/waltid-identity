import { AvailableCredential, CredentialFormats } from '@/types/credentials';
import EditCredentialModal from '../modal/EditCredentialModal';
import { PencilSquareIcon } from '@heroicons/react/24/outline';
import Dropdown from '@/components/walt/forms/Dropdown';
import React from 'react';

type Props = {
  credentialToEdit: AvailableCredential;
  credentialsToIssue: AvailableCredential[];
  setCredentialsToIssue: (credentials: AvailableCredential[]) => void;
};

export default function RowCredential({ credentialToEdit, credentialsToIssue, setCredentialsToIssue }: Props) {
  const [credentialSubject, setCredentialSubject] = React.useState(credentialToEdit.offer.credentialSubject);
  const [selectedFormat, setSelectedFormat] = React.useState(CredentialFormats[0]);
  const [modalVisible, setModalVisible] = React.useState(false);

  React.useEffect(() => {
    setCredentialsToIssue(
      credentialsToIssue.map((credential) => {
        if (credential.offer.id == credentialToEdit.offer.id) {
          return {
            ...credential,
            offer: {
              ...credential.offer,
              credentialSubject: credentialSubject,
            },
          };
        } else {
          return credential;
        }
      })
    );
  }, [credentialSubject]);

  React.useEffect(() => {
    setCredentialsToIssue(
      credentialsToIssue.map((credential) => {
        if (credential.offer.id == credentialToEdit.offer.id) {
          return {
            ...credential,
              selectedFormat: selectedFormat,
          };
        } else {
          return credential;
        }
      })
    );
  }, [selectedFormat]);

  return (
    <>
      <div className="flex flex-row gap-5 justify-between">
        <div className="flex flex-row gap-5 items-center">
          <div className="hidden sm:block bg-primary-400 w-[45px] h-[28px] rounded-lg"></div>
          <span className="text-gray-900 text-lg text-left">{credentialToEdit.title}</span>
          <PencilSquareIcon onClick={() => { setModalVisible(true) }} className="h-4 text-gray-500 hover:text-primary-400 cursor-pointer" />
        </div>
        <div className="flex flex-row items-center gap-3 w-5/12">
          <div className="w-[2px] h-[2px] bg-gray-200"></div>
          <div className="w-full">
            <Dropdown
              values={CredentialFormats}
              selected={selectedFormat}
              setSelected={setSelectedFormat}
            />
          </div>
        </div>
      </div>
      <EditCredentialModal show={modalVisible} onClose={() => { setModalVisible(false) }} credentialSubject={credentialSubject} setCredentialSubject={setCredentialSubject} />
    </>
  );
}
