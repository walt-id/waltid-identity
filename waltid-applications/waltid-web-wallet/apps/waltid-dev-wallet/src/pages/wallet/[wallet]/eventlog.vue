<template>
  <CenterMain>
    <h1 class="text-2xl font-semibold mb-2">Event log</h1>

    <LoadingIndicator v-if="pending" class="my-6 mb-12 w-full">
      Loading eventLog...
    </LoadingIndicator>

    <p class="mb-1">The list of events is shown below:</p>
    <div class="flex items-center justify-between gap-x-6 py-4">
      <div class="flex flex-none items-center gap-x-4">
        <button
          v-if="eventLog?.currentStartingAfter"
          @click="topPage('-1')"
          class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
        >
          Top
        </button>
      </div>
      <div class="flex flex-none items-center gap-x-4">
        <button
          v-if="eventLog?.nextStartingAfter"
          @click="nextPage(eventLog.nextStartingAfter)"
          class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
        >
          Next
        </button>
      </div>
    </div>

    <div
      v-if="eventLog?.count > 0"
      aria-label="Credential list"
      class="h-full overflow-y-auto shadow-xl"
    >
      <div class="container mx-auto px-4 sm:px-8">
        <div class="py-8">
          <!-- table source: https://codepen.io/superfly/pen/wvvPLZB -->
          <div>
            <h2 class="text-2xl font-semibold leading-tight">List of events</h2>
          </div>
          <div class="-mx-4 sm:-mx-8 px-4 sm:px-8 py-4 overflow-x-auto">
            <div
              class="inline-block min-w-full shadow-md rounded-lg overflow-hidden"
            >
              <table class="min-w-full leading-normal">
                <thead>
                  <tr>
                    <th
                      class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider"
                    >
                      Timestamp
                    </th>
                    <th
                      class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider"
                    >
                      Event
                    </th>
                    <th
                      class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider"
                    >
                      Action
                    </th>
                    <th
                      class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider"
                    >
                      Tenant
                    </th>
                    <th
                      class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider"
                    >
                      Originator
                    </th>
                    <th
                      class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100 text-left text-xs font-semibold text-gray-700 uppercase tracking-wider"
                    ></th>
                    <th
                      class="px-5 py-3 border-b-2 border-gray-200 bg-gray-100"
                    ></th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="item in eventLog.items" :key="item">
                    <tr>
                      <td
                        class="px-5 py-5 border-b border-gray-200 bg-white text-sm"
                      >
                        <div class="flex">
                          <div class="ml-3">
                            <p class="text-gray-900 whitespace-no-wrap">
                              {{ item.timestamp }}
                            </p>
                            <p class="text-gray-600 whitespace-no-wrap">
                              000004
                            </p>
                          </div>
                        </div>
                      </td>
                      <td
                        class="px-5 py-5 border-b border-gray-200 bg-white text-sm"
                      >
                        <p class="text-gray-900 whitespace-no-wrap">
                          {{ item.event }}
                        </p>
                      </td>
                      <td
                        class="px-5 py-5 border-b border-gray-200 bg-white text-sm"
                      >
                        <p class="text-gray-900 whitespace-no-wrap">
                          {{ item.action }}
                        </p>
                      </td>
                      <td
                        class="px-5 py-5 border-b border-gray-200 bg-white text-sm"
                      >
                        <p class="text-gray-900 whitespace-no-wrap">
                          {{ item.tenant }}
                        </p>
                      </td>
                      <td
                        class="px-5 py-5 border-b border-gray-200 bg-white text-sm"
                      >
                        <p class="text-gray-900 whitespace-no-wrap">
                          {{ item.originator }}
                        </p>
                      </td>

                      <td
                        class="px-5 py-5 border-b border-gray-200 bg-white text-sm text-right"
                      >
                        <button
                          class="inline-block text-gray-500 hover:text-gray-700"
                          @click="
                            viewData(`${item.event}.${item.action}`, item.data)
                          "
                          type="button"
                        >
                          <!-- horizontal -->
                          <svg
                            class="w-5 h-5"
                            aria-hidden="true"
                            xmlns="http://www.w3.org/2000/svg"
                            fill="currentColor"
                            viewBox="0 0 16 3"
                          >
                            <path
                              d="M2 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3Zm6.041 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3ZM14 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3Z"
                            />
                          </svg>
                          <!-- vertical -->
                          <!-- <svg class="inline-block h-6 w-6 fill-current" viewBox="0 0 24 24">
                                            <path d="M12 6a2 2 0 110-4 2 2 0 010 4zm0 8a2 2 0 110-4 2 2 0 010 4zm-2 6a2 2 0 104 0 2 2 0 00-4 0z" />
                                        </svg> -->
                        </button>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="mt-2 border-l pl-2">
      <h3 class="mt-2 text-base font-semibold text-gray-900">
        No eventLog entries.
      </h3>
      <p class="text-base text-gray-500">
        No operations have been logged to your auditable eventLog yet.
      </p>
    </div>
  </CenterMain>
</template>

<script setup>
import CenterMain from "@waltid-web-wallet/components/CenterMain.vue";
import LoadingIndicator from "@waltid-web-wallet/components/loading/LoadingIndicator.vue";
import useModalStore from "@waltid-web-wallet/stores/useModalStore.ts";
import ViewEventDataModal from "@waltid-web-wallet/components/modals/ViewEventDataModal.vue";
import {useCurrentWallet} from "@waltid-web-wallet/composables/accountWallet.ts";

const store = useModalStore();

const currentWallet = useCurrentWallet();
const startingAfter = ref("-1");
console.log("Loading EventLog...");
const { data: eventLog, pending: pending } = await useLazyAsyncData(
  () =>
    $fetch(`/wallet-api/wallet/${currentWallet.value}/eventlog`, {
      params: {
        limit: 10,
        startingAfter: startingAfter.value,
      },
    }),
  {
    watch: [startingAfter],
  },
);
function viewData(title, data) {
  console.log(`View event data: ${title}`);

  store.openModal({
    component: ViewEventDataModal,
    props: {
      title: title,
      data: data,
    },
  });
}
function topPage(startingAfterParam) {
  console.log(`top: ${startingAfterParam}`);
  startingAfter.value = startingAfterParam;
}
function nextPage(startingAfterParam) {
  console.log(`next: ${startingAfterParam}`);
  startingAfter.value = startingAfterParam;
}
</script>

<style scoped></style>
