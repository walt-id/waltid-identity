export const addQueryParamToCurrentURL = (key: any, value: any) => {
  if (typeof window === 'undefined') {
    // This function should only be used in the browser, it won't do anything server-side.
    return;
  }

  const URLObject = new URL(window.location.href);
  const params = new URLSearchParams(URLObject.search);

  if (key) {
    params.set(key, value);
  }

  URLObject.search = params.toString();

  // update the current URL without navigating
  window.history.pushState({}, '', URLObject.toString());
};
