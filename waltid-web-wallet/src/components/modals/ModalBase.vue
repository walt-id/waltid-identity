<template>
    <Teleport to="body">
        <Transition name="modal-fade">
            <div v-if="store.modalState?.component" aria-modal="true" class="modal-wrapper" role="dialog" tabindex="-1">
                <component :is="store.modalState?.component" class="relative p-5 rounded-lg outline outline-offset-2 outline-blue-500 bg-white dark:bg-slate-800" v-bind="store.modalState?.props" />
            </div>
        </Transition>
    </Teleport>
</template>

<script lang="ts" setup>
import useModalStore from "~/stores/useModalStore";

const store = useModalStore();

// Make a function that will trigger on keydown
function keydownListener(event: KeyboardEvent) {
    // Assert the key is escape
    if (event.key === "Escape") store.closeModal();
}

// Attach event listener on mount
onMounted(() => {
    document.addEventListener("keydown", keydownListener);
});

// Clean up on unmount
onUnmounted(() => {
    document.removeEventListener("keydown", keydownListener);
});
</script>

<style scoped>
.modal-wrapper {
    position: fixed;
    left: 0;
    top: 0;

    z-index: 500;

    width: 100vw;
    height: 100vh;
    background: rgba(0, 0, 0, 0.2);

    display: grid;
    place-items: center;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
    opacity: 0;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
    transition: 0.25s ease all;
}
</style>
