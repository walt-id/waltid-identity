import hljs from 'highlight.js/lib/core'
import json from 'highlight.js/lib/languages/json'
import highlightJS from '@highlightjs/vue-plugin'
import 'highlight.js/styles/intellij-light.css'

export default defineNuxtPlugin((nuxtApp) => {
    hljs.registerLanguage('json', json)
    nuxtApp.vueApp.use(highlightJS)
})
