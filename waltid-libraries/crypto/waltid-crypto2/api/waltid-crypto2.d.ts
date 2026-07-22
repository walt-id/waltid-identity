type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare namespace dev.whyoleg.cryptography.providers.webcrypto {
    /** @deprecated  */
    const initHook: any;
}
export declare namespace id.walt.crypto2 {
    abstract class Crypto2Js extends KtSingleton<Crypto2Js.$metadata$.constructor>() {
        private constructor();
    }
    namespace Crypto2Js {
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace $metadata$ {
            abstract class constructor {
                normalizeStoredKey(encoded: string): string;
                storedKeyProvider(encoded: string): string;
                isStoredKey(encoded: string): boolean;
                private constructor();
            }
        }
    }
}
