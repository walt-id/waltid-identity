import { useLazyAsyncData, createError, navigateTo } from "nuxt/app";
import { useCurrentWallet } from "./accountWallet.ts";
import { decodeRequest } from "./siop-requests.ts";
import { type Ref, ref, watch } from "vue";
import { groupBy } from "./groupings.ts";

export async function useIssuance(query: any) {
    const currentWallet = useCurrentWallet()
    const { data: dids, pending: pendingDids } = await useLazyAsyncData<Array<{ did: string; default: boolean; }>>(() => $fetch(`/wallet-api/wallet/${currentWallet.value}/dids`));
    const selectedDid: Ref<{ did: string; default: boolean; } | null> = ref(null);

    watch(dids, async (newDids) => {
        await nextTick();
        selectedDid.value = newDids?.find((item) => { return item.default == true; }) ?? newDids[0] ?? null;
    });

    async function resolveCredentialOffer(request: string) {
        try {
            const response: {
                credential_issuer: string;
                credential_configuration_ids: string[];
            } = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolveCredentialOffer`, {
                method: "POST",
                body: request
            });
            return response;
        } catch (e) {
            failed.value = true;
            throw e;
        }
    }

    const request = decodeRequest(query.request as string);
    const credentialOffer = await resolveCredentialOffer(request);
    if (credentialOffer == null) {
        throw createError({
            statusCode: 400,
            statusMessage: "Invalid issuance request: No credential_offer",
        });
    }

    const issuer = credentialOffer["credential_issuer"];
    let issuerHost: String;
    try {
        issuerHost = new URL(issuer).host;
    } catch {
        issuerHost = issuer;
    }

    const credential_issuer: { credential_configurations_supported: Array<{ types: Array<String>; }>; } = await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolveIssuerOpenIDMetadata?issuer=${issuer}`)
    const credentialList = credentialOffer.credential_configuration_ids.map((id) => credential_issuer.credential_configurations_supported[id]);

    let credentialTypes: String[] = [];
    for (let credentialListElement of credentialList) {

        if (typeof credentialListElement["credential_definition"] !== 'undefined') {
            const typeList = credentialListElement["credential_definition"]["type"] as Array<String>;
            const lastType = typeList[typeList.length - 1] as String;
            credentialTypes.push(lastType);
        }

        if (typeof credentialListElement["vct"] !== 'undefined') {

            const response = await fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolveVctUrl?vct=${credentialListElement["vct"]}`);

            if (response.status < 200 || response.status >= 300) {
                throw new Error(`VCT URL returns error: ${response.status}`);
            }

            const data = await response.json();
            const nameOrDescription = data.name ?? data.description ?? data.vct ?? null
            credentialTypes.push(nameOrDescription);
        }
    }
    const credentialCount = credentialTypes.length;

    let i = 0;
    const groupedCredentialTypes = groupBy(
        credentialTypes.map((item) => {
            return { id: ++i, name: item };
        }),
        (c: { name: string }) => c.name,
    );

    const failed = ref(false);
    const failMessage = ref("Unknown error occurred.");
    async function acceptCredential() {
        const did: string | null = selectedDid.value?.did ?? dids.value[0]?.did ?? null;
        if (did === null) { return; }

        try {
            await $fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/useOfferRequest?did=${did}`, {
                method: "POST",
                body: request,
            });
            navigateTo(`/wallet/${currentWallet.value}`);
        } catch (e: any) {
            failed.value = true;

            let errorMessage = e?.data.startsWith("{") ? JSON.parse(e.data) : e.data ?? e;
            errorMessage = errorMessage?.message ?? errorMessage;

            failMessage.value = errorMessage;

            console.log("Error: ", e?.data);
            alert("Error occurred while trying to receive credential: " + failMessage.value);

            throw e;
        }
    }

    return {
        currentWallet,
        dids,
        selectedDid,
        pendingDids,
        acceptCredential,
        failed,
        failMessage,
        credentialTypes,
        credentialCount,
        groupedCredentialTypes,
        issuerHost
    }
}
