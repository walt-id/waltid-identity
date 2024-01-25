export type AvailableCredential = {
  id: string;
  title: string;
  selectedFormat?: String;
  offer: any;
};

export const CredentialFormats = [
  'JWT + VCDM',
  'SD-JWT + VCDM',
  // 'Data Integrity/JSON-LD+ VCDM',
  // 'mdoc / mdl (IEC/ISO 18013-5) ',
];