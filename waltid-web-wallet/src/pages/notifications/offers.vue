<template>
    <CenterMain>
        <h1 class="semibold text-lg">Pending offers</h1>

        <ul class="divide-y divide-gray-100" role="list">
            <li v-for="project in projects" :key="project.id" class="flex items-center justify-between gap-x-6 py-5">
                <div class="min-w-0">
                    <div class="flex items-start gap-x-3">
                        <p class="text-sm leading-6 text-gray-900 font-semibold">
                            Offer from <span class="underline">{{ project.name }}</span>
                        </p>
                        <p :class="[statuses[project.status], 'rounded-md whitespace-nowrap mt-0.5 px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset']">
                            {{ project.status }}
                        </p>
                    </div>
                    <div class="mt-1 flex items-center gap-x-2 text-xs leading-5 text-gray-500">
                        <p class="whitespace-nowrap">
                            Due on
                            <time :datetime="project.dueDateTime">{{ project.dueDate }}</time>
                        </p>
                        <svg class="h-0.5 w-0.5 fill-current" viewBox="0 0 2 2">
                            <circle cx="1" cy="1" r="1" />
                        </svg>
                        <p class="truncate">Issued to {{ project.issuedTo }}</p>
                    </div>
                </div>
                <div class="flex flex-none items-center gap-x-4">
                    <a :href="project.href" class="hidden rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 sm:block"
                        >View offer<span class="sr-only">, {{ project.name }}</span></a
                    >
                    <Menu as="div" class="relative flex-none">
                        <MenuButton class="-m-2.5 block p-2.5 text-gray-500 hover:text-gray-900">
                            <span class="sr-only">Open options</span>
                            <EllipsisVerticalIcon aria-hidden="true" class="h-5 w-5" />
                        </MenuButton>
                        <transition
                            enter-active-class="transition ease-out duration-100"
                            enter-from-class="transform opacity-0 scale-95"
                            enter-to-class="transform opacity-100 scale-100"
                            leave-active-class="transition ease-in duration-75"
                            leave-from-class="transform opacity-100 scale-100"
                            leave-to-class="transform opacity-0 scale-95"
                        >
                            <MenuItems class="absolute right-0 z-10 mt-2 w-32 origin-top-right rounded-md bg-white py-2 shadow-lg ring-1 ring-gray-900/5 focus:outline-none">
                                <MenuItem v-slot="{ active }">
                                    <a :class="[active ? 'bg-gray-50' : '', 'block px-3 py-1 text-sm leading-6 text-gray-900']" href="#"
                                        >Edit<span class="sr-only">, {{ project.name }}</span></a
                                    >
                                </MenuItem>
                                <MenuItem v-slot="{ active }">
                                    <a :class="[active ? 'bg-gray-50' : '', 'block px-3 py-1 text-sm leading-6 text-gray-900']" href="#"
                                        >Move<span class="sr-only">, {{ project.name }}</span></a
                                    >
                                </MenuItem>
                                <MenuItem v-slot="{ active }">
                                    <a :class="[active ? 'bg-gray-50' : '', 'block px-3 py-1 text-sm leading-6 text-gray-900']" href="#"
                                        >Delete<span class="sr-only">, {{ project.name }}</span></a
                                    >
                                </MenuItem>
                            </MenuItems>
                        </transition>
                    </Menu>
                </div>
            </li>
        </ul>
    </CenterMain>
</template>

<script setup>
import CenterMain from "~/components/CenterMain.vue";

import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/vue";
import { EllipsisVerticalIcon } from "@heroicons/vue/20/solid";

const statuses = {
    Complete: "text-green-700 bg-green-50 ring-green-600/20",
    Pending: "text-gray-600 bg-gray-50 ring-gray-500/10",
    Archived: "text-yellow-800 bg-yellow-50 ring-yellow-600/20",
};
const projects = [
    {
        id: 1,
        name: "issuer.walt-test.cloud",
        href: "#",
        status: "Pending",
        issuedTo: "did:key:z6Mkipa1mwZTvUaTCPkHsdKGWNWteQbpEmvcr9HFed9gS4Ye",
        dueDate: "May 04, 2023",
        dueDateTime: "2023-05-04T00:00Z",
    },
    {
        id: 2,
        name: "issuer.walt-test.cloud",
        href: "#",
        status: "Complete",
        issuedTo: "did:key:z6Mkipa1mwZTvUaTCPkHsdKGWNWteQbpEmvcr9HFed9gS4Ye",
        dueDate: "May 04, 2023",
        dueDateTime: "2023-05-04T00:00Z",
    },
    {
        id: 3,
        name: "howest.neom.walt-test.cloud",
        href: "#",
        status: "Complete",
        issuedTo: "did:key:z6Mkipa1mwZTvUaTCPkHsdKGWNWteQbpEmvcr9HFed9gS4Ye",
        dueDate: "May 03, 2023",
        dueDateTime: "2023-05-03T00:00Z",
    },
    {
        id: 4,
        name: "issuer.walt.id",
        href: "#",
        status: "Complete",
        issuedTo: "did:key:z6Mkipa1mwZTvUaTCPkHsdKGWNWteQbpEmvcr9HFed9gS4Ye",
        dueDate: "May 03, 2023",
        dueDateTime: "2023-05-03T00:00Z",
    },
    {
        id: 5,
        name: "issuer.walt-test.cloud",
        href: "#",
        status: "Archived",
        issuedTo: "did:key:z6Mkipa1mwZTvUaTCPkHsdKGWNWteQbpEmvcr9HFed9gS4Ye",
        dueDate: "May 03, 2023",
        dueDateTime: "2023-05-03T00:00Z",
    },
];
</script>

<style scoped></style>
