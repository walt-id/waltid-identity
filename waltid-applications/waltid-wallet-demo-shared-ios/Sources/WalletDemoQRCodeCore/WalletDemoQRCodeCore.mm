#import "WalletDemoQRCodeCore.h"

#include "BitMatrix.h"
#include "MultiFormatWriter.h"

#include <algorithm>
#include <exception>
#include <string>

CGImageRef _Nullable WalletDemoCreateProximityQRCode(NSString *contents) {
    constexpr int lowErrorCorrectionLevel = 1;
    ZXing::MultiFormatWriter writer { ZXing::BarcodeFormat::QRCode };
    writer.setMargin(0);
    writer.setEccLevel(lowErrorCorrectionLevel);

    ZXing::BitMatrix matrix;
    try {
        const char *utf8 = contents.UTF8String;
        if (utf8 == nullptr) {
            return nil;
        }
        const std::string ascii(utf8);
        if (std::any_of(ascii.begin(), ascii.end(), [](unsigned char byte) { return byte > 0x7f; })) {
            return nil;
        }
        matrix = writer.encode(ascii, 0, 0);
    } catch (const std::exception &) {
        return nil;
    }

    const int width = matrix.width();
    const int height = matrix.height();
    NSMutableData *pixels = [[NSMutableData alloc] initWithLength:width * height];
    auto *bytes = static_cast<uint8_t *>(pixels.mutableBytes);
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            bytes[y * width + x] = matrix.get(x, y) ? 0 : 255;
        }
    }

    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceGray();
    CGDataProviderRef provider = CGDataProviderCreateWithCFData((__bridge CFDataRef)pixels);
    CGImageRef image = CGImageCreate(
        width,
        height,
        8,
        8,
        width,
        colorSpace,
        kCGBitmapByteOrderDefault,
        provider,
        nullptr,
        true,
        kCGRenderingIntentDefault
    );
    CGDataProviderRelease(provider);
    CGColorSpaceRelease(colorSpace);
    return image;
}
