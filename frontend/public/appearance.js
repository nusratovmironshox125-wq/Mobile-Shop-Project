(() => {
  const root = document.documentElement;
  let theme;
  let language;
  try {
    theme = localStorage.getItem('rms-theme');
    language = localStorage.getItem('rms-language');
  } catch {}
  root.dataset.theme = theme === 'dark' || theme === 'light' ? theme
    : matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  root.lang = language === 'uz' || language === 'ru' ? language : navigator.language.startsWith('uz') ? 'uz' : 'ru';
})();
