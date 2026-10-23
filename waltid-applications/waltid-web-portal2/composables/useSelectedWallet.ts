import { WALLETS, getWallet, type WalletId } from "~/utils/walletCatalog";

const walletId = ref<WalletId>("walt-id");

export function useSelectedWallet() {
  const wallet = computed(() => getWallet(walletId.value));

  function selectWallet(id: WalletId) {
    walletId.value = id;
  }

  return { walletId, wallet, wallets: WALLETS, selectWallet };
}
