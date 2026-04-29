import {
  AvailableCredential,
  CredentialFormats,
  DIDMethods,
  ISO_MDOC_CREDENTIAL_FORMAT,
} from "@/types/credentials";
import EditCredentialModal from "../modal/EditCredentialModal";
import {PencilSquareIcon} from "@heroicons/react/24/outline";
import Dropdown from "@/components/walt/forms/Dropdown";
import React from "react";

type Props = {
  credentialToEdit: AvailableCredential;
  credentialsToIssue: AvailableCredential[];
  setCredentialsToIssue: React.Dispatch<
    React.SetStateAction<AvailableCredential[]>
  >;
};

function formatsForCredential(c: AvailableCredential): string[] {
  return c.kind === "mdoc"
    ? [ISO_MDOC_CREDENTIAL_FORMAT]
    : CredentialFormats.filter((f) => f !== ISO_MDOC_CREDENTIAL_FORMAT);
}

export default function RowCredential({
  credentialToEdit,
  credentialsToIssue: _credentialsToIssue,
  setCredentialsToIssue,
}: Props) {
  const initialSubject =
    credentialToEdit.kind === "mdoc"
      ? credentialToEdit.offer?.mdocData ?? credentialToEdit.offer ?? {}
      : credentialToEdit.offer?.credentialSubject ?? {};

  const [credentialSubject, setCredentialSubject] =
    React.useState(initialSubject);
  const formatChoices = formatsForCredential(credentialToEdit);
  const [selectedFormat, setSelectedFormat] = React.useState(
    String(
      credentialToEdit.kind === "mdoc"
        ? ISO_MDOC_CREDENTIAL_FORMAT
        : credentialToEdit.selectedFormat ?? CredentialFormats[0]
    )
  );
  const [selectedDID, setSelectedDID] = React.useState(
    String(credentialToEdit.selectedDID ?? DIDMethods[0])
  );
  const [modalVisible, setModalVisible] = React.useState(false);

  React.useEffect(() => {
    setCredentialsToIssue((prev) =>
      prev.map((credential) => {
        if (credential.id !== credentialToEdit.id) {
          return credential;
        }
        const updatedCredential = { ...credential };
        updatedCredential.selectedFormat = selectedFormat;
        updatedCredential.selectedDID = selectedDID;
        if (credentialToEdit.kind === "mdoc") {
          updatedCredential.offer = {
            ...credential.offer,
            mdocData: credentialSubject,
          };
        } else {
          updatedCredential.offer = {
            ...credential.offer,
            credentialSubject,
          };
        }
        return updatedCredential;
      })
    );
  }, [
    credentialSubject,
    selectedFormat,
    selectedDID,
    credentialToEdit.id,
    credentialToEdit.kind,
    setCredentialsToIssue,
  ]);

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
              values={formatChoices}
              selected={selectedFormat}
              setSelected={setSelectedFormat}
            />
          </div>
          {credentialToEdit.kind !== "mdoc" && (
            <div className="w-full">
              <Dropdown
                values={DIDMethods}
                selected={selectedDID}
                setSelected={setSelectedDID}
              />
            </div>
          )}
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
