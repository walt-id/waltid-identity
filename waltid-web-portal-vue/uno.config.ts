import { defineConfig, presetUno } from "unocss";
import { presetForms } from '@julr/unocss-preset-forms'

export default defineConfig({
    presets: [
        presetUno(),
        presetForms(), // Add preflights and new rules likes `.form-input`

    /*    presetForms({
            strategy: 'class', // Only add new rules likes `.form-input` and not preflights
        }),

        presetForms({
            strategy: 'base', // Only add preflights and not new rules
        }),*/

    ],
})
