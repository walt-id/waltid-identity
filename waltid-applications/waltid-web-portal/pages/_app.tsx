import React from 'react';
import axios from 'axios';
import '@/styles/globals.css';
import type { AppProps } from 'next/app';
import { AvailableCredential } from '@/types/credentials';

export const EnvContext = React.createContext({} as { [key: string]: string });
export const CredentialsContext = React.createContext(
  [[], (credentials: AvailableCredential[]) => { }] as
  [AvailableCredential[], (credentials: AvailableCredential[]) => void]
)

export default function App({ Component, pageProps }: AppProps) {
  const [AvailableCredentials, setAvailableCredentials] = React.useState<AvailableCredential[]>([]);
  const [env, setEnv] = React.useState({} as { [key: string]: string });

  React.useEffect(() => {
    if (env.hasOwnProperty('NEXT_PUBLIC_VC_REPO')) {
      axios.get(`${env.NEXT_PUBLIC_VC_REPO}/api/list`).then((response) => {
        response.data.forEach((credential: string) => {
          axios.get(`${env.NEXT_PUBLIC_VC_REPO}/api/vc/${credential}`).then((data) => {
            setAvailableCredentials((prev) => [...prev, {
              id: credential,
              title: credential,
              offer: data.data,
            }]);
          });
        });
      });
    }
    else {
      axios.get('/api/env').then((response) => {
        setEnv(response.data);
      });
    }
  }, [env]);

  return (
    <EnvContext.Provider value={env}>
      <CredentialsContext.Provider value={[AvailableCredentials, setAvailableCredentials]}>
        <Component {...pageProps} />
      </CredentialsContext.Provider>
    </EnvContext.Provider>
  )
}
