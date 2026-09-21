#import "CoreBluetoothTestDoubles.h"
#import <objc/runtime.h>

@interface WIDFakeCentral : NSObject
@property(nonatomic, weak) id<CBCentralManagerDelegate> fakeDelegate;
@property(nonatomic) NSUInteger disconnects;
@end
@implementation WIDFakeCentral
- (id<CBCentralManagerDelegate>)delegate { return self.fakeDelegate; }
- (void)setDelegate:(id<CBCentralManagerDelegate>)delegate { self.fakeDelegate = delegate; }
- (CBManagerState)state { return CBManagerStatePoweredOn; }
- (void)scanForPeripheralsWithServices:(NSArray<CBUUID *> *)services options:(NSDictionary *)options {}
- (void)stopScan {}
- (void)connectPeripheral:(CBPeripheral *)peripheral options:(NSDictionary *)options {}
- (void)cancelPeripheralConnection:(CBPeripheral *)peripheral { self.disconnects++; }
@end

@interface WIDFakePeripheral : NSObject
@property(nonatomic, weak) id<CBPeripheralDelegate> fakeDelegate;
@property(nonatomic, strong) NSArray<CBService *> *fakeServices;
@property(nonatomic, strong) NSMutableArray<NSData *> *writes;
@property(nonatomic) BOOL writable;
@property(nonatomic) BOOL backpressure;
@end
@implementation WIDFakePeripheral
- (id<CBPeripheralDelegate>)delegate { return self.fakeDelegate; }
- (void)setDelegate:(id<CBPeripheralDelegate>)delegate { self.fakeDelegate = delegate; }
- (NSArray<CBService *> *)services { return self.fakeServices; }
- (BOOL)canSendWriteWithoutResponse { return self.writable; }
- (NSUInteger)maximumWriteValueLengthForType:(CBCharacteristicWriteType)type { return 64; }
- (void)discoverServices:(NSArray<CBUUID *> *)services {}
- (void)discoverCharacteristics:(NSArray<CBUUID *> *)characteristics forService:(CBService *)service {}
- (void)readValueForCharacteristic:(CBCharacteristic *)characteristic {}
- (void)setNotifyValue:(BOOL)enabled forCharacteristic:(CBCharacteristic *)characteristic {}
- (void)writeValue:(NSData *)data forCharacteristic:(CBCharacteristic *)characteristic type:(CBCharacteristicWriteType)type {
    [self.writes addObject:[data copy]];
    if (self.backpressure) self.writable = NO;
}
@end

@interface WIDFakeCharacteristic : CBMutableCharacteristic
@property(nonatomic) BOOL fakeNotifying;
@end
@implementation WIDFakeCharacteristic
- (BOOL)isNotifying { return self.fakeNotifying; }
@end

CBCentralManager *WIDTestCentral(id<CBCentralManagerDelegate> delegate) {
    // Objective-C selector doubles keep OS init/dealloc and radio services outside these tests.
    WIDFakeCentral *central = [WIDFakeCentral new];
    central.fakeDelegate = delegate;
    return (CBCentralManager *)central;
}
CBPeripheral *WIDTestPeripheral(void) {
    WIDFakePeripheral *peer = [WIDFakePeripheral new];
    peer.writes = [NSMutableArray new];
    peer.writable = YES;
    return (CBPeripheral *)peer;
}
void WIDTestSetServices(CBPeripheral *peer, NSArray<CBService *> *services) { ((WIDFakePeripheral *)peer).fakeServices = services; }
void WIDTestSetWritable(CBPeripheral *peer, BOOL writable) { ((WIDFakePeripheral *)peer).writable = writable; }
void WIDTestSetBackpressure(CBPeripheral *peer, BOOL backpressure) { ((WIDFakePeripheral *)peer).backpressure = backpressure; }
NSArray<NSData *> *WIDTestWrites(CBPeripheral *peer) { return ((WIDFakePeripheral *)peer).writes; }
NSUInteger WIDTestDisconnects(CBCentralManager *central) { return ((WIDFakeCentral *)central).disconnects; }
CBMutableCharacteristic *WIDTestCharacteristic(CBUUID *uuid, BOOL notifying) {
    WIDFakeCharacteristic *characteristic = [[WIDFakeCharacteristic alloc] initWithType:uuid
        properties:CBCharacteristicPropertyNotify | CBCharacteristicPropertyWriteWithoutResponse
        value:nil permissions:CBAttributePermissionsReadable | CBAttributePermissionsWriteable];
    characteristic.fakeNotifying = notifying;
    return characteristic;
}

@interface WIDFakePeripheralManager : NSObject
@property(nonatomic, weak) id<CBPeripheralManagerDelegate> fakeDelegate;
@property(nonatomic, strong) CBMutableService *fakeService;
@property(nonatomic, strong) NSMutableArray<NSData *> *notifications;
@property(nonatomic) BOOL writable;
@property(nonatomic) NSUInteger removeServicesCount;
@end
@implementation WIDFakePeripheralManager
- (id<CBPeripheralManagerDelegate>)delegate { return self.fakeDelegate; }
- (void)setDelegate:(id<CBPeripheralManagerDelegate>)delegate { self.fakeDelegate = delegate; }
- (CBManagerState)state { return CBManagerStatePoweredOn; }
- (void)addService:(CBMutableService *)service {
    self.fakeService = service;
    [self.fakeDelegate peripheralManager:(CBPeripheralManager *)self didAddService:service error:nil];
}
- (void)startAdvertising:(NSDictionary *)data { [self.fakeDelegate peripheralManagerDidStartAdvertising:(CBPeripheralManager *)self error:nil]; }
- (void)stopAdvertising {}
- (void)removeAllServices { self.removeServicesCount++; }
- (void)respondToRequest:(CBATTRequest *)request withResult:(CBATTError)result {}
- (BOOL)updateValue:(NSData *)value forCharacteristic:(CBMutableCharacteristic *)characteristic onSubscribedCentrals:(NSArray<CBCentral *> *)centrals {
    if (!self.writable) return NO;
    [self.notifications addObject:[value copy]];
    return YES;
}
@end

@interface WIDFakeReaderCentral : NSObject
@property(nonatomic, strong) NSUUID *fakeIdentifier;
@end
@implementation WIDFakeReaderCentral
- (NSUUID *)identifier { return self.fakeIdentifier; }
- (NSUInteger)maximumUpdateValueLength { return 64; }
@end

@interface WIDFakeRequest : CBATTRequest
@property(nonatomic, strong) CBCentral *fakeCentral;
@property(nonatomic, strong) CBCharacteristic *fakeCharacteristic;
@property(nonatomic, strong) NSData *fakeValue;
@end
@implementation WIDFakeRequest
- (CBCentral *)central { return self.fakeCentral; }
- (CBCharacteristic *)characteristic { return self.fakeCharacteristic; }
- (NSData *)value { return self.fakeValue; }
- (void)setValue:(NSData *)value { self.fakeValue = value; }
- (NSUInteger)offset { return 0; }
@end

CBPeripheralManager *WIDTestPeripheralManager(id<CBPeripheralManagerDelegate> delegate) {
    WIDFakePeripheralManager *manager = [WIDFakePeripheralManager new];
    manager.fakeDelegate = delegate;
    manager.notifications = [NSMutableArray new];
    manager.writable = YES;
    return (CBPeripheralManager *)manager;
}
CBCentral *WIDTestReaderCentral(void) {
    WIDFakeReaderCentral *central = [WIDFakeReaderCentral new];
    central.fakeIdentifier = [NSUUID UUID];
    return (CBCentral *)central;
}
CBATTRequest *WIDTestWriteRequest(CBCentral *central, CBCharacteristic *characteristic, NSData *value) {
    WIDFakeRequest *request = class_createInstance(WIDFakeRequest.class, 0);
    request.fakeCentral = central;
    request.fakeCharacteristic = characteristic;
    request.fakeValue = value;
    return (CBATTRequest *)request;
}
void WIDTestSetUpdateReady(CBPeripheralManager *manager, BOOL writable) { ((WIDFakePeripheralManager *)manager).writable = writable; }
NSArray<NSData *> *WIDTestNotifications(CBPeripheralManager *manager) { return ((WIDFakePeripheralManager *)manager).notifications; }
NSUInteger WIDTestRemoveServicesCount(CBPeripheralManager *manager) { return ((WIDFakePeripheralManager *)manager).removeServicesCount; }
CBCharacteristic *WIDTestInput(CBPeripheralManager *manager) { return ((WIDFakePeripheralManager *)manager).fakeService.characteristics[1]; }
