export function webWalletHref(walletUrl: string, openIdUrl: string): string {
  const url = new URL(walletUrl);
  url.search = "";
  url.hash = "";
  url.searchParams.set("link", openIdUrl);
  return url.toString();
}
