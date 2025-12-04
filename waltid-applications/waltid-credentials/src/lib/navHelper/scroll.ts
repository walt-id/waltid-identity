export function scroll(id: string, path: string) {
  const scrollMargin = 100;

  const element = document.getElementById(id)!;

  window.scrollTo({
    top: element.getBoundingClientRect().top + window.scrollY - scrollMargin,
    behavior: "smooth",
  });

  history.pushState({}, "", `${path}#${id}`);
}
