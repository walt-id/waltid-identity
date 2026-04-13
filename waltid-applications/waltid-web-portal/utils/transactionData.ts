export const TRANSACTION_DATA_SUPPORTED_SELECTED_FORMAT = "SD-JWT + IETF SD-JWT VC";

export function isTransactionDataSupportedSelectedFormat(selectedFormat?: string): boolean {
  return selectedFormat === TRANSACTION_DATA_SUPPORTED_SELECTED_FORMAT;
}
