export default defineNuxtRouteMiddleware((to, from) => {
  const cookie = useCookie("login");
  if (!cookie.value && to.path !== "/login" && to.path !== "/signup") {
    return navigateTo("/login");
  }
});
