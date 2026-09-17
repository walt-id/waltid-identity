#import "WalletDemoQRCodeCore.h"

#include "CreateBarcode.h"
#include "WriteBarcode.h"

#include <algorithm>
#include <exception>
#include <string_view>

CGImageRef _Nullable WalletDemoCreateProximityQRCode(NSString *contents) {
    try {
        const char *utf8 = contents.UTF8String;
        if (utf8 == nullptr) return nil;
        const std::string_view ascii(utf8, [contents lengthOfBytesUsingEncoding:NSUTF8StringEncoding]);
        if (ascii.size() > 2953 || ascii.substr(0, 5) != "mdoc:" ||
            std::any_of(ascii.begin(), ascii.end(), [](unsigned char byte) { return byte < 0x21 || byte > 0x7e; })) {
            return nil;
        }
        const auto barcode = ZXing::CreateBarcodeFromText(ascii, ZXing::CreatorOptions(ZXing::BarcodeFormat::QRCode, "ecLevel=1"));
        const auto image = ZXing::WriteBarcodeToImage(barcode, ZXing::WriterOptions().scale(1).addQuietZones(false));
        const int width = image.width();
        const int height = image.height();
        if (width <= 0 || height <= 0) return nil;
        NSMutableData *pixels = [[NSMutableData alloc] initWithLength:width * height];
        auto *bytes = static_cast<uint8_t *>(pixels.mutableBytes);
        if (bytes == nullptr) return nil;
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) bytes[y * width + x] = *image.data(x, y);
        }
        CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceGray();
        CGDataProviderRef provider = CGDataProviderCreateWithCFData((__bridge CFDataRef)pixels);
        CGImageRef result = CGImageCreate(width, height, 8, 8, width, colorSpace,
            kCGBitmapByteOrderDefault, provider, nullptr, false, kCGRenderingIntentDefault);
        CGDataProviderRelease(provider);
        CGColorSpaceRelease(colorSpace);
        return result;
    } catch (const std::exception &) {
        return nil;
    }
}
