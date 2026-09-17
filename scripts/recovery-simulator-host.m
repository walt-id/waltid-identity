// Give Kotlin/Native tests a UIKit lifecycle and an explicit, per-launch completion record.
#import <UIKit/UIKit.h>
#import <unistd.h>
#include "recovery-host-output.h"

extern int main(int argc, char **argv);
extern int32_t waltRecoveryKeychainExchange(const char *namespace);
static int testArgc;
static char **testArgv;

@interface RecoveryTestDelegate : UIResponder <UIApplicationDelegate>
@property(strong, nonatomic) UIWindow *window;
@end

@implementation RecoveryTestDelegate
- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)options {
    self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    self.window.rootViewController = [UIViewController new];
    [self.window makeKeyAndVisible];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        NSString *run = NSProcessInfo.processInfo.environment[@"RECOVERY_HOST_RUN"];
        if (![[NSUUID alloc] initWithUUIDString:run]) exit(2);
        NSString *documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject;
        NSString *path = [documents stringByAppendingPathComponent:[run stringByAppendingString:@".log"]];
        if (redirectRecoveryOutput(path.UTF8String) < 0) exit(2);
        int result = main(testArgc, testArgv);
        if (result == 0) {
            for (NSString *argument in NSProcessInfo.processInfo.arguments) {
                if ([argument hasPrefix:@"--swiftRecoveryExchange="]) {
                    result = waltRecoveryKeychainExchange([argument substringFromIndex:24].UTF8String);
                    printf("\nRECOVERY_INTEROP_EXIT=%d\n", result);
                }
            }
        }
        fflush(NULL);
        printf("\nRECOVERY_TEST_EXIT=%d\n", result);
        fflush(stdout);
        // Let the runner collect the result and terminate the app after launch is acknowledged.
    });
    return YES;
}
@end

int waltTestMain(int argc, char **argv) {
    testArgc = argc;
    testArgv = argv;
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass(RecoveryTestDelegate.class));
    }
}
