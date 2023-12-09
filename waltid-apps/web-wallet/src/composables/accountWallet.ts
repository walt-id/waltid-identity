export type WalletListing = {
    id: string,
    name: string
    createdOn: string
    addedOn: string
    permission: string
}

export type WalletListings = {
    account: string,
    wallets: WalletListing[]
}

export async function listWallets() {
    const { data, refresh } = useFetch<WalletListings>("/wallet-api/wallet/accounts/wallets");
    await refresh()
    return data;
}

export function setWallet(
    newWallet: string | null,
    redirectUri: ((walletId: string) => string) | undefined = (walletId) => `/wallet/${walletId}`
) {
    console.log("New wallet: ", newWallet);
    useCurrentWallet().value = newWallet;

    if (newWallet != null && redirectUri != undefined)
        navigateTo(redirectUri(newWallet));
}

export function useCurrentWallet() {
    return useState<string | null>("wallet", () => {
        const currentRoute = useRoute();
        const currentWalletId = currentRoute.params["wallet"] ?? null;

        if (currentWalletId == null && currentRoute.name != "/") {
            console.log("Error for currentWallet at: ", currentRoute);
        } else {
            console.log("Returning: " + currentWalletId + ", at: " + currentRoute.fullPath);
            return currentWalletId;
        }
    });
}
