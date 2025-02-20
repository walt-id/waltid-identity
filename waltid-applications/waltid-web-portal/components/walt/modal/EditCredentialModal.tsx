import React from "react";
import Button from "@/components/walt/button/Button";
import BaseModal from "@/components/walt/modal/BaseModal";

type Props = {
  show: boolean;
  onClose: () => void;
  credentialSubject: JSON;
  setCredentialSubject: (credentialSubject: JSON) => void;
};

export default function EditCredentialModal({
  show,
  onClose,
  credentialSubject,
  setCredentialSubject,
}: Props) {
  const [subjectJson, setSubjectJson] = React.useState(credentialSubject);

  return (
    <BaseModal show={show} securedByWalt={false} onClose={onClose}>
      <div className="flex flex-col items-center">
        <div className="w-full">
          <textarea
            value={JSON.stringify(subjectJson, null, 2)}
            onChange={(e) => {
              try {
                setSubjectJson(JSON.parse(e.target.value));
              } catch (error) {
                console.log(error);
              }
            }}
            className="w-full h-48 border-2 border-gray-300 rounded-md p-2"
            placeholder="Paste your JSON here"
          />
        </div>
        <div className="flex flex-row justify-end gap-2 mt-5">
          <Button onClick={onClose} style="link">
            Cancel
          </Button>
          <Button
            onClick={() => {
              setCredentialSubject(subjectJson);
              onClose();
            }}
            style="button"
          >
            Save
          </Button>
        </div>
      </div>
    </BaseModal>
  );
}
