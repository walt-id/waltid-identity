#include <CoreFoundation/CoreFoundation.h>

static inline bool waltCfEqual(CFTypeRef left, CFTypeRef right) {
    return CFEqual(left, right);
}
