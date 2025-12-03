<template>
    <section>
        <div class="border border-0.2 border-slate-700 bg-slate-900 rounded-md">
            <div class="flex gap-x-2 py-2 px-2 border-b-0.2 border-slate-800 bg-slate-800 rounded-t-md">
                <div @click="onActiveTabChange(i)"
                     :class="tabs[activeTab].tabName === tabName ? 'bg-slate-700' : ''"
                     class="py-1 px-3 text-slate-50 rounded-md cursor-pointer" v-for="({tabName, slot}, i) in tabs"
                     :key="tabName">
                    <span :class="tabs[activeTab].tabName === tabName ? 'text-primary-300' : ''" class="text-slate-50">{{ tabName }}</span>
                </div>
            </div>
            <div class="py-4 px-4">
                <slot v-if="activeTab === 0" name="tab1" />
                <slot v-if="activeTab === 1" name="tab2" />
                <slot v-if="activeTab === 2" name="tab3" />
                <slot v-if="activeTab === 3" name="tab4" />
                <slot v-if="activeTab === 4" name="tab5" />
                <slot v-if="activeTab === 5" name="tab6" />
            </div>
        </div>

    </section>
</template>


<script setup lang="ts">
import { ref } from "vue";

const props = defineProps({
    tabNames: {
        type: Array,
        required: true,
        validator: (tabs) => Array.isArray(tabs) && tabs.length > 0,
    }
});

const tabs = props.tabNames.map((tab, i) => ({ slot: `tab${i + 1}`, tabName: tab }));
const activeTab = ref(0);

const onActiveTabChange = (i: number) => {
    activeTab.value = i;
};


</script>
