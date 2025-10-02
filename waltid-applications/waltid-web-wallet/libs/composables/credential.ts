import {parseDisclosures} from "../composables/disclosures.ts";
import {computedAsync} from "@vueuse/core";
import {parseJwt} from "../utils/jwt.ts";
import {computed, type Ref, ref, watchEffect} from "vue";
import {useCurrentWallet} from "./accountWallet";

export type WalletCredential = {
    wallet: string;
    id: string;
    document: string;
    disclosures?: string;
    addedOn: string;
    manifest?: string;
    parsedDocument?: {
        [key: string]: any;
        display?: Array<Object>;
    }
    format: string;
};

export function useCredential(credential: Ref<WalletCredential | null>) {
    const currentWallet = useCurrentWallet()
    const jwtJson = computedAsync(async () => {
        if (credential.value) {
            if (credential.value.parsedDocument) return credential.value.parsedDocument;

            let parsed;
            if (credential.value.format && credential.value.format === "mso_mdoc") {
                let resp = await fetch(`/wallet-api/util/parseMDoc`, {
                    method: "POST",
                    body: credential.value.document,
                });
                parsed = await resp.json();
            }
            else {
                parsed = parseJwt(credential.value.document);
            }

            if (parsed.vc) return parsed.vc; else return parsed;
        } else return null;
    });

    const disclosures = computed(() => {
        if (credential.value && credential.value.disclosures) {
            return parseDisclosures(credential.value.disclosures);
        } else return null;
    });

    const manifest = computed(() => (credential.value?.manifest && credential.value.manifest != "{}" ? (typeof credential.value.manifest === 'string' ? JSON.parse(credential.value.manifest) : credential.value.manifest) : credential.value?.parsedDocument?.display?.[0] ?? null));
    const manifestClaims = computed(() => manifest.value?.display?.claims);

    // Function to resolve VCT URL and fetch the name parameter
    async function fetchVctName(vct: string): Promise<String> {
        try {
            const response = await fetch(`/wallet-api/wallet/${currentWallet.value}/exchange/resolveVctUrl?vct=${vct}`);
            const data = await response.json();
            return data.name || null;
        } catch (error) {
            console.error('Error fetching VCT name:', error);
            return null;
        }
    }

    const titleTitelized = ref('');

    watchEffect(async () => {
        if (jwtJson.value?.vct) {
            const vct = jwtJson.value.vct;
            titleTitelized.value = await fetchVctName(vct)
        } else {
            // Fallback logic if there's no `vct`
            titleTitelized.value = manifest.value?.display?.title
                ?? jwtJson.value?.type?.at(-1)?.replace(/([a-z0-9])([A-Z])/g, "$1 $2")
                ?? jwtJson.value?.vct?.replace("_vc+sd-jwt", "").replace(/([a-z0-9])([A-Z])/g, "$1 $2")
                ?? jwtJson.value?.docType;
        }
    });

    const credentialSubtitle = computed(() => manifest.value?.display?.card?.description ?? jwtJson.value?.name);
    const credentialImageUrl = computed(() => manifest.value?.display?.card?.logo?.uri ?? jwtJson.value?.issuer?.image?.id ?? jwtJson.value?.issuer?.image);
    const issuerName = computed(() => manifest.value?.display?.card?.issuedBy ?? jwtJson.value?.issuer?.name);
    const issuerLogo = computed(() => jwtJson.value?.issuer?.image?.id ?? jwtJson.value?.issuer?.image);
    const issuerDid = computed(() => manifest.value?.input?.issuer ?? jwtJson.value?.issuer?.id ?? jwtJson.value?.issuer);
    const issuerKid = computed(() =>
        credential.value.format === "vc+sd-jwt" ? jwtJson.value?.iss ?? null : null
    );
    const credentialIssuerService = computed(() => manifest.value?.input?.credentialIssuer);

    const isNotExpired = computed(() => jwtJson.value?.expirationDate ? new Date(jwtJson.value?.expirationDate).getTime() > new Date().getTime() : jwtJson.value?.validUntil ? new Date(jwtJson.value?.validUntil).getTime() > new Date().getTime() : true);
    const issuanceDate = computed(() => {
        if (jwtJson.value?.issuanceDate) {
            return new Date(jwtJson.value?.issuanceDate).toISOString().slice(0, 10);
        } else if (jwtJson.value?.validFrom) {
            return new Date(jwtJson.value?.validFrom).toISOString().slice(0, 10);
        } else {
            return null;
        }
    });
    const expirationDate = computed(() => {
        if (jwtJson.value?.expirationDate) {
            return new Date(jwtJson.value?.expirationDate).toISOString().slice(0, 10);
        } else if (jwtJson.value?.validUntil) {
            return new Date(jwtJson.value?.validUntil).toISOString().slice(0, 10);
        } else {
            return null;
        }
    });

    return {
        jwtJson,
        disclosures,
        manifest,
        manifestClaims,
        titleTitelized,
        credentialSubtitle,
        credentialImageUrl,
        issuerName,
        issuerLogo,
        issuerDid,
        issuerKid,
        credentialIssuerService,
        isNotExpired,
        issuanceDate,
        expirationDate
    };
}
