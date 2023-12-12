import CustomCredentialModal from '@/components/walt/modal/CustomCredentialModal';
import { MagnifyingGlassIcon } from "@heroicons/react/24/outline";
import Credential from '@/components/walt/credential/Credential';
import { AvailableCredential } from '@/types/credentials';
import Button from '@/components/walt/button/Button';
import { CredentialsContext } from '@/pages/_app';
import { Inter } from 'next/font/google';
import React, { useState } from 'react';
import { useRouter } from 'next/router';

const inter = Inter({ subsets: ['latin'] });

type CredentialToIssue = AvailableCredential & {
  selected: boolean;
};

export default function Home() {
  const [AvailableCredentials] = React.useContext(CredentialsContext);
  const router = useRouter();

  const [credentialsToIssue, setCredentialsToIssue] = useState<CredentialToIssue[]>(prepareCredentialsToIssue);
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [modalVisible, setModalVisible] = useState(false);

  const showButton = credentialsToIssue.some((cred) => cred.selected);
  const credentials = !searchTerm ? credentialsToIssue : credentialsToIssue.filter(credential => {
    return credential.title.toLowerCase().includes(searchTerm.toLowerCase());
  })

  function prepareCredentialsToIssue(): CredentialToIssue[] {
    return AvailableCredentials.map((cred: AvailableCredential) => {
      return {
        ...cred,
        selected: false,
      };
    });
  }

  React.useEffect(() => {
    setCredentialsToIssue(prepareCredentialsToIssue);
  }, [AvailableCredentials]);

  function getIdsForCredentialsToIssue() {
    const ids: string[] = [];
    credentialsToIssue.forEach((cred) => {
      if (cred.selected) {
        ids.push(cred.id);
      }
    });

    return ids;
  }

  function handleCredentialSelect(id: string) {
    const updatedCreds = credentialsToIssue.map((cred) => {
      if (cred.id === id) {
        return {
          ...cred,
          selected: !cred.selected,
        };
      } else {
        return cred;
      }
    });

    setCredentialsToIssue(updatedCreds);
  }

  function handleStartIssuance() {
    const idsToIssue = getIdsForCredentialsToIssue();

    const params = new URLSearchParams();
    params.append('ids', idsToIssue.join(','));

    router.push(`/credentials?${params.toString()}`);
  }

  function handleSearchTermChange(e: any) {
    const value = e.target.value;
    setSearchTerm(value);
  }

  return (
    <div>
      <div className="flex flex-col justify-center items-center mt-10">
        <h1 className="text-4xl font-bold text-primary-900 text-center mt-5">
          Walt.id Portal
        </h1>
        <p className="mt-4 text-lg text-primary-900">
          Select Credential(s) to issue or verify
        </p>
      </div>
      <main className="flex flex-col items-center gap-5 justify-between mt-16 md:w-[740px] m-auto">
        <div className='flex flex-row gap-5 w-full px-5'>
          <div className='flex flex-row w-full border-b border-b-1 border-gray-200'>
            <MagnifyingGlassIcon className='h-6 mt-3 text-gray-500' />
            <input type="text" className='w-full mt-1 border-none outline-none focus:ring-0 bg-gray-50' onChange={handleSearchTermChange} />
          </div>
          <Button size='sm' onClick={() => { setModalVisible(true); }}>Custom Credential</Button>
        </div>
        {credentials.length === 0 && <div className='w-full mt-10 text-center'>No Credential with that name.</div>}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-y-10 gap-x-5 mt-10">
          {credentials.map(({ id, title, selected }) => (
            <Credential
              id={id}
              title={title}
              selected={selected}
              onClick={handleCredentialSelect}
              key={id}
            />
          ))}
        </div>
      </main>
      <Button
        className={`transition-all duration-700 ease-in-out fixed ${!showButton && '-translate-y-20'
          } top-5 right-5 left-0`}
        size="lg"
        onClick={handleStartIssuance}
      >
        Start
      </Button>
      <CustomCredentialModal show={modalVisible} onClose={() => { setModalVisible(!modalVisible) }} />
    </div>
  );
}
