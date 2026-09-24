#import <CoreBluetooth/CoreBluetooth.h>
#import <Foundation/Foundation.h>

// Simulator-test-only objects. No factory calls a CoreBluetooth manager initializer or opens a radio.
CBCentralManager *WIDTestCentral(id<CBCentralManagerDelegate> delegate);
CBPeripheral *WIDTestPeripheral(void);
void WIDTestSetServices(CBPeripheral *peer, NSArray<CBService *> *services);
void WIDTestSetWritable(CBPeripheral *peer, BOOL writable);
void WIDTestSetBackpressure(CBPeripheral *peer, BOOL backpressure);
NSArray<NSData *> *WIDTestWrites(CBPeripheral *peer);
NSUInteger WIDTestDisconnects(CBCentralManager *central);
CBMutableCharacteristic *WIDTestCharacteristic(CBUUID *uuid, BOOL notifying);

CBPeripheralManager *WIDTestPeripheralManager(id<CBPeripheralManagerDelegate> delegate);
CBCentral *WIDTestReaderCentral(void);
CBATTRequest *WIDTestWriteRequest(CBCentral *central, CBCharacteristic *characteristic, NSData *value);
void WIDTestSetUpdateReady(CBPeripheralManager *manager, BOOL writable);
NSArray<NSData *> *WIDTestNotifications(CBPeripheralManager *manager);
NSUInteger WIDTestRemoveServicesCount(CBPeripheralManager *manager);
CBCharacteristic *WIDTestInput(CBPeripheralManager *manager);
