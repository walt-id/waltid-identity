#pragma once

#import <CoreGraphics/CoreGraphics.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

#ifdef __cplusplus
extern "C" {
#endif

/// Creates a margin-free low-error-correction QR matrix for validated proximity ASCII.
CGImageRef _Nullable WalletDemoCreateProximityQRCode(
    NSString *contents
) CF_RETURNS_RETAINED;

#ifdef __cplusplus
}
#endif

NS_ASSUME_NONNULL_END
