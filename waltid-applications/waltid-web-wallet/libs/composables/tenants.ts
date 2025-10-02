import {createError, useAppConfig, useFetch, useRequestURL, useState} from "nuxt/app";

export function useTenant() {
    return useState("tenant-config", async () => {
        const config = useAppConfig();

        if (config.enableCloudTenants) {
            const baseTenantDomain = useRequestURL().host.replace(":3000", ":8080");
            console.log("Base tenant domain is: ", baseTenantDomain);

            const { data, error } = await useFetch("http://" + baseTenantDomain + "/config/wallet");
            console.log("Tenant configuration: ", data.value);

            if (error.value) {
                const dataMessage = error.value?.data?.message;
                const localErrorMessage = error.value?.message;

                console.log("Tenant error, error message: ", dataMessage);
                console.log("Tenant error, data message: ", dataMessage);

                const errorMessage = dataMessage ? dataMessage : localErrorMessage;

                throw createError({
                    statusCode: 404,
                    statusMessage: "Tenant error: " + errorMessage,
                    fatal: true
                });
            }

            return data.value;
        } else {
            return config.localTenant;
        }
    });
}
