import type {
  IssuerCredentialConfiguration,
  IssuerCredentialDisplay,
} from "~/composables/useIssuerMetadata";
import type { IssuerProfile } from "~/composables/useProfiles";

export interface ProfileCard {
  profileId: string;
  name: string;
  credentialConfigurationId: string;
  description?: string;
  backgroundImageUri?: string;
  backgroundColor: string;
  configuration?: IssuerCredentialConfiguration;
}

function firstDisplay(
  configuration?: IssuerCredentialConfiguration,
): IssuerCredentialDisplay | undefined {
  return configuration?.credential_metadata?.display?.[0];
}

export function buildCardsFromConfigurations(
  configurations: Record<string, IssuerCredentialConfiguration>,
): ProfileCard[] {
  return Object.entries(configurations).map(([id, configuration]) => {
    const display = firstDisplay(configuration);
    return {
      profileId: id,
      credentialConfigurationId: id,
      name: display?.name || id,
      description: display?.description,
      backgroundImageUri: display?.background_image?.uri,
      backgroundColor: display?.background_color || "#0f172a",
      configuration,
    };
  });
}

export function buildProfileCards(
  profiles: IssuerProfile[],
  configurationFor: (
    credentialConfigurationId?: string,
  ) => IssuerCredentialConfiguration | undefined,
): ProfileCard[] {
  return profiles.map((profile) => {
    const credentialConfigurationId = profile.credentialConfigurationId ?? "";
    const configuration = configurationFor(credentialConfigurationId);
    const display = firstDisplay(configuration);
    return {
      profileId: profile.profileId,
      name: display?.name || profile.name || profile.profileId,
      credentialConfigurationId,
      description: display?.description,
      backgroundImageUri: display?.background_image?.uri,
      backgroundColor: display?.background_color || "#0f172a",
      configuration,
    };
  });
}
