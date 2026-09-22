# Android Block Store recovery

Optional integration for the mobile wallet identity lifecycle. The base SDK does not depend on or register this provider.

Register `BlockStoreIdentityRecovery(context, namespace)` in `SigningIdentityConfiguration.recoveryProviders`. The default mode requires OS end-to-end encryption availability before cloud submission; `DeviceTransfer` disables cloud backup.

Local OS acceptance does not prove cloud delivery or availability on another device.
See the [identity recovery guide](../waltid-openid4vc-wallet-mobile/docs/identity-recovery.md) for configuration, security boundaries and the replaceable provider contract.
