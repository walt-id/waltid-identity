// Give Kotlin/Native tests a UIKit lifecycle and an explicit, per-launch completion record.
#import <UIKit/UIKit.h>
#import <unistd.h>

extern int main(int argc, char **argv);
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
        if (!freopen(path.UTF8String, "w", stdout) || dup2(fileno(stdout), fileno(stderr)) < 0) exit(2);
        setbuf(stdout, NULL);
        setbuf(stderr, NULL);
        int result = main(testArgc, testArgv);
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
