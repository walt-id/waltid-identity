export interface TransactionDataProfile {
  type: string;
  displayName: string;
  fields: string[];
}

export function useTransactionDataProfiles(verifierBase: string) {
  const profiles = ref<TransactionDataProfile[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  async function load() {
    loading.value = true;
    error.value = null;

    try {
      const response = await fetch(
        `${verifierBase}/transaction-data-profiles`,
        {
          headers: { accept: "application/json" },
        },
      );

      if (!response.ok) {
        const body = await response.text();
        throw new Error(`HTTP ${response.status}: ${body}`);
      }

      const data = await response.json();
      profiles.value = Array.isArray(data)
        ? data.filter(
            (profile): profile is TransactionDataProfile =>
              typeof profile?.type === "string" &&
              typeof profile?.displayName === "string" &&
              Array.isArray(profile?.fields) &&
              profile.fields.every(
                (field: unknown) => typeof field === "string",
              ),
          )
        : [];
    } catch (e) {
      error.value =
        e instanceof Error
          ? e.message
          : "Could not load transaction data profiles.";
      profiles.value = [];
    } finally {
      loading.value = false;
    }
  }

  return { profiles, loading, error, load };
}
