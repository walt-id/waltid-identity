export type CredentialAction = {
    name: string,
    icon: string,
    action: (template: string) => Promise<void>
}
