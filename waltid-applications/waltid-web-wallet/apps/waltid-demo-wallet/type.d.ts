import en from "../../libs/locales/en-US.json";

type MessageSchema = typeof en;

declare module "vue-i18n" {
    export interface DefineLocaleMessage extends MessageSchema { }
}
