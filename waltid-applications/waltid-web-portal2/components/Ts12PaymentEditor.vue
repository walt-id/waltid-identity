<script setup lang="ts">
import { computed, ref, watch, useId } from "vue";
import { PAYMENT_FIELDS, PAYMENT_FREQUENCIES, paymentDraft, paymentPayload, paymentExample,
  type PaymentMode } from "~/utils/ts12Payment";
const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string]; validity: [error: string] }>();
const fieldPrefix = useId();
const draft = ref(paymentDraft(paymentExample("unscheduled")));
const importError = ref(""); const formError = ref(""); let lastEmitted = "";
const raw = computed({
  get: () => props.modelValue,
  set: value => { lastEmitted = ""; emit("update:modelValue", value); },
});
watch(() => props.modelValue, value => {
  if (value === lastEmitted) return;
  try { draft.value = paymentDraft(value); importError.value = "";
    try { paymentPayload(draft.value); formError.value = ""; }
    catch (error) { formError.value = (error as Error).message; }
    emit("validity", formError.value); }
  catch (error) { importError.value = (error as Error).message; emit("validity", importError.value); }
}, { immediate: true });
function update() {
  try {
    const value = paymentPayload(draft.value);
    lastEmitted = value; formError.value = ""; emit("validity", ""); emit("update:modelValue", value);
  } catch (error) { formError.value = (error as Error).message; emit("validity", formError.value); }
}
function example(mode: PaymentMode) { raw.value = paymentExample(mode); }
function fields(group: string) {
  return PAYMENT_FIELDS.filter(field =>
    group === "required" ? !field.path.includes(".") && field.required && field.path !== "execution_date" || field.path === "payee.name" || field.path === "payee.id" :
    group === "optional" ? !field.required && !field.path.startsWith("recurrence.") :
    group === "pisp" ? field.path.startsWith("pisp.") :
    group === "schedule" ? field.path === "execution_date" :
    group === "recurrence" ? field.path.startsWith("recurrence.") && !field.path.startsWith("recurrence.mit_options.") :
    field.path.startsWith("recurrence.mit_options."));
}
const groups = computed(() => [
  { key: "required", title: "Payment" }, { key: "optional", title: "Optional payment details" },
  ...(draft.value.pisp ? [{ key: "pisp", title: "Payment initiation service provider" }] : []),
  ...(draft.value.mode === "scheduled" ? [{ key: "schedule", title: "Scheduled execution" }] : []),
  ...(draft.value.mode === "recurring" ? [{ key: "recurrence", title: "Recurring payments" }] : []),
  ...(draft.value.mode === "recurring" && draft.value.mit ? [{ key: "mit", title: "Merchant-initiated payment terms" }] : []),
]);
</script>
<template>
  <div class="space-y-3 border rounded-md p-3" data-testid="ts12-payment-editor">
    <div class="flex flex-wrap gap-2">
      <button type="button" class="border rounded px-2 py-1 text-sm" @click="example('unscheduled')">Minimal example</button>
      <button type="button" class="border rounded px-2 py-1 text-sm" @click="example('scheduled')">Scheduled example</button>
      <button type="button" class="border rounded px-2 py-1 text-sm" @click="example('recurring')">Recurring example</button>
    </div>
    <p v-if="importError" role="alert" class="text-red-600">{{ importError }}</p>
    <div v-else class="space-y-3">
      <label :for="fieldPrefix + '-timing'" class="block">Payment timing</label>
      <select :id="fieldPrefix + '-timing'" v-model="draft.mode" @change="update" class="border rounded p-2 w-full">
        <option value="unscheduled">Unscheduled</option><option value="scheduled">Scheduled</option><option value="recurring">Recurring</option>
      </select>
      <p class="text-xs">Without scheduling, a customer-initiated payment executes immediately; a merchant-initiated payment has an unknown execution time.</p>
      <label class="block"><input v-model="draft.pisp" type="checkbox" @change="update" /> Include payment initiation service provider</label>
      <label v-if="draft.mode === 'recurring'" class="block"><input v-model="draft.mit" type="checkbox" @change="update" /> Include merchant-initiated payment terms</label>
      <details v-for="group in groups" :key="group.key" :open="group.key !== 'optional'">
        <summary>{{ group.title }}</summary>
        <div class="payment-fields mt-2">
          <div v-for="field in fields(group.key)" :key="field.path">
            <label :for="fieldPrefix + field.path" class="block text-sm">
              {{ field.label }} <span v-if="field.required">(required)</span>
            </label>
            <label v-if="!field.required" class="inline-flex gap-2 text-xs">
              <input v-model="draft.enabled[field.path]" type="checkbox" :aria-label="'Include ' + field.label" @change="update" /> Include
            </label>
              <select v-if="field.kind === 'boolean'" v-model="draft.values[field.path]" :disabled="!field.required && !draft.enabled[field.path]" @change="update" class="border rounded p-2 w-full" :name="field.path" :id="fieldPrefix + field.path">
                <option value="">Choose…</option><option value="true">Yes</option><option value="false">No</option>
              </select>
              <select v-else-if="field.kind === 'frequency'" v-model="draft.values[field.path]" @change="update" class="border rounded p-2 w-full" :name="field.path" :id="fieldPrefix + field.path">
                <option value="">Choose…</option><option v-for="(label, code) in PAYMENT_FREQUENCIES" :key="code" :value="code">{{ label }} ({{ code }})</option>
              </select>
              <input v-else v-model="draft.values[field.path]" @input="update" type="text"
                :inputmode="['money','number','integer'].includes(field.kind) ? 'decimal' : 'text'"
                :disabled="!field.required && !draft.enabled[field.path]" :name="field.path" :id="fieldPrefix + field.path"
                :placeholder="field.kind === 'timestamp' ? '2027-01-01T12:00:00+01:00' : ''" class="border rounded p-2 w-full" />
            <p v-if="field.kind === 'money'" class="text-xs">Major currency units; ISO 4217 decimal precision applies.</p>
          </div>
        </div>
      </details>
      <p v-if="formError" role="alert" class="text-red-600">{{ formError }}</p>
    </div>
    <details><summary>Edit payment JSON</summary><textarea v-model="raw" spellcheck="false" aria-label="Payment payload JSON" class="border rounded p-2 w-full min-h-[180px] font-mono text-xs" /></details>
  </div>
</template>

<style scoped>
.payment-fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 16rem), 1fr));
  gap: 0.75rem;
}
</style>
