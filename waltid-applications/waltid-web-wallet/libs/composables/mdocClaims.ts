import { computed, type Ref } from "vue";

export type MDocClaim = {
  key: string;
  value: any;
  displayValue: string;
  isImage: boolean;
  isObject: boolean;
};

export function useMdocClaims(jwtJson: Ref<any>) {
  const mdocNamespaces = computed(() => {
    if (!jwtJson.value?.issuerSigned?.nameSpaces) return [];
    return Object.keys(jwtJson.value.issuerSigned.nameSpaces);
  });

  const mdocClaims = computed((): MDocClaim[] => {
    if (!jwtJson.value?.issuerSigned?.nameSpaces) return [];
    const namespace = mdocNamespaces.value[0];
    if (!namespace) return [];

    const claims = jwtJson.value.issuerSigned.nameSpaces[namespace];
    return Object.entries(claims).map(([key, value]) => {
      const isImage = typeof value === "string" && value.startsWith("data:image/");
      const isObject = typeof value === "object" && value !== null && !Array.isArray(value);
      const isArrayOfObjects = Array.isArray(value) && value.length > 0 && typeof value[0] === "object";

      let displayValue: string;
      if (isImage) {
        displayValue = "[Image]";
      } else if (isArrayOfObjects) {
        // Format array of objects nicely
        displayValue = JSON.stringify(value, null, 2);
      } else if (isObject) {
        displayValue = JSON.stringify(value, null, 2);
      } else if (Array.isArray(value)) {
        // Handle primitive arrays
        displayValue = value.join(", ");
      } else {
        displayValue = String(value);
      }

      return {
        key,
        value,
        displayValue,
        isImage,
        isObject: isObject || isArrayOfObjects,
      };
    });
  });

  const formatClaimKey = (key: string): string => {
    return key
      .replace(/_/g, " ")
      .replace(/\b\w/g, (char) => char.toUpperCase());
  };

  return {
    mdocNamespaces,
    mdocClaims,
    formatClaimKey,
  };
}
