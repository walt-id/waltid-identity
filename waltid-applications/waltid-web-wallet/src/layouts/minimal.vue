<template>
    <div class="h-full">
        <slot class="h-full" />

        <ClientOnly>
            <div v-if="$pwa?.needRefresh" class="pwa-toast" role="alert">
                <div class="message">New content available, click on reload button to update.</div>
                <button @click="$pwa.updateServiceWorker()">Reload</button>
            </div>
            <div v-if="$pwa?.showInstallPrompt && !$pwa?.offlineReady && !$pwa?.needRefresh" class="pwa-toast" role="alert">
                <div class="message">
                    <span> Install PWA </span>
                </div>
                <button @click="$pwa.install()">Install</button>
                <button @click="$pwa.cancelInstall()">Cancel</button>
            </div>
        </ClientOnly>
    </div>
</template>

<style>
.pwa-toast {
    position: fixed;
    right: 0;
    bottom: 0;
    margin: 16px;
    padding: 12px;
    border: 1px solid #8885;
    border-radius: 4px;
    z-index: 1;
    text-align: left;
    box-shadow: 3px 4px 5px 0 #8885;
}

.pwa-toast .message {
    margin-bottom: 8px;
}

.pwa-toast button {
    border: 1px solid #8885;
    outline: none;
    margin-right: 5px;
    border-radius: 2px;
    padding: 3px 10px;
}
</style>
<script setup></script>
