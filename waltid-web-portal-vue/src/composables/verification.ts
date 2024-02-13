export type VerificationPolicyInformation = {
    name: string
    description: string,
    policyType: string,
    argumentType: string[]
}

export function hasArguments(argumentType: string[] | null | undefined) {
    return argumentType && argumentType.length > 0 && !(argumentType.length == 1 && argumentType[0] == "NONE");
    // return !(!props.information.argumentType
    //     || props.information.argumentType.length == 0
    //     || props.information.argumentType[0] == "NONE" && props.information.argumentType.length == 1);
}
