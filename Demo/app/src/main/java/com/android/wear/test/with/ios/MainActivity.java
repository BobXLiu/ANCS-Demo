package com.android.wear.test.with.ios;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private final static int SERVICE_MODE = 1;
    private final static int CUSTOM_MODE = 2;
    private int test_mode = CUSTOM_MODE;

    private final static String TAG = "Wear";
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private ScanSettings scanSettings;
    private List<ScanFilter> filters;
    private boolean isBtAlreadyOpen = false;
    private boolean isFirstConnectGatt = true, isStartConnect = false;

    private TextView information;
    private boolean isRegisterBtOpenReceiver = false;

    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private final static String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.VIBRATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.SET_TIME_ZONE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        checkPermissions();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int i = 0;
            for (; i < PERMISSIONS.length; i++) {
                if (checkSelfPermission(PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(PERMISSIONS, REQUEST_CODE_ASK_PERMISSIONS);
                    break;
                }
            }
            if (i >= PERMISSIONS.length) {
                init();
            }
        } else {
            init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[]
            grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                            this,
                            "You should agree all of the permissions, force exit! please retry",
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            init();
        }
    }

    private void init() {
        initView();
        output("SDK version: " + Build.VERSION.SDK_INT);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG, "not supported ble");
            finish();
        }

        if (test_mode == SERVICE_MODE) {
            ((findViewById(R.id.scan_ble))).setVisibility(View.INVISIBLE);
            startService(new Intent(MainActivity.this, BLEService.class));
        } else if (test_mode == CUSTOM_MODE) {
            detectBtStatus();
        }
    }


    private void initial() {
        if (bluetoothAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

                scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
            if (bluetoothLeScanner == null) {
                output("Device not support bluetooth BLE Scanner");
            } else {
                output("Get BT Le Scanner success");
                (findViewById(R.id.scan_ble)).setEnabled(true);
            }
        }
    }

    private void detectBtStatus() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            Toast.makeText(MainActivity.this, "Device not support bluetooth", Toast.LENGTH_SHORT).show();
        }
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        } else if (bluetoothAdapter != null) {
            output("BT Already Open");
            initial();
        }
        registerBtReceiver();
    }

    private void registerBtReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(btReceiver, filter);
        isBtAlreadyOpen = false;
        isRegisterBtOpenReceiver = true;
    }

    private BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON && !isBtAlreadyOpen) {
                    output("Open BT Success");
                    initial();
                    isBtAlreadyOpen = true;
                }
            }else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int cur_bond_state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice
                        .BOND_NONE);
                int previous_bond_state = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.BOND_NONE);
                output("Bond state: " + cur_bond_state);
                if(cur_bond_state == BluetoothDevice.BOND_BONDED){
                    output("Start connect GATT");

                    isStartConnect = true;
                    //connect
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    output("Device address: " + device.getAddress());
                    bluetoothGatt = device.connectGatt(getApplicationContext(), false, bluetoothGattCallback);
                }
            }
        }
    };

    public void btnScanBLE(View view) {
        Log.i(TAG, "Start scan");
        output("Start Scan");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
////            bluetoothLeScanner.startScan(filters, scanSettings, bleScanCallback);
            bluetoothLeScanner.startScan(bleScanCallback);
        } else {
            bluetoothAdapter.startLeScan(new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
                    if (bluetoothDevice != null) {
                        Log.i(TAG, "name: " + bluetoothDevice.getName());
                        Log.i(TAG, "address: " + bluetoothDevice.getAddress());
                        output(bluetoothDevice.getName());
                    }
                }
            });
        }
    }

    public void btnGetPairDevice(View view) {
        BluetoothDevice device = null;
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices != null && bondedDevices.size() > 0) {
            for (BluetoothDevice tmp : bondedDevices) {
                output(tmp.getName() + ", " + tmp.getAddress());
                if (tmp.getName() != null && tmp.getName().equals("iPhone")) {
                    device = tmp;
                }
            }
        }
        if (device != null) {
            output("Start Connect " + device.getName() + " GATT");
            bluetoothGatt = device.connectGatt(getApplicationContext(), false, bluetoothGattCallback);
        }
    }

    private BluetoothLeAdvertiser bluetoothLeAdvertiser;

    public void btnOpenGattServer(View view) {
        if (bluetoothManager != null) {
            bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            if (bluetoothLeAdvertiser == null) {
                output("the device not support peripheral");
                return;
            }

            UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
            UUID HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
            UUID BODY_SENSOR_LOCATION_UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb");
            UUID HEART_RATE_CONTROL_POINT_UUID = UUID.fromString("00002A39-0000-1000-8000-00805f9b34fb");

            BluetoothGattService bluetoothGattService = new BluetoothGattService(
//                    HEART_RATE_SERVICE_UUID, BluetoothGattService
                    UUID.fromString("00001000-0000-1000-8000-00805F9B34FB"), BluetoothGattService
                    .SERVICE_TYPE_PRIMARY
            );

            //for heart characteristic start
            BluetoothGattCharacteristic mHeartRateMeasurementCharacteristic =
                    new BluetoothGattCharacteristic(HEART_RATE_MEASUREMENT_UUID,
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            /* No permissions */ 0);

            BluetoothGattCharacteristic mBodySensorLocationCharacteristic =
                    new BluetoothGattCharacteristic(BODY_SENSOR_LOCATION_UUID,
                            BluetoothGattCharacteristic.PROPERTY_READ,
                            BluetoothGattCharacteristic.PERMISSION_READ);

            BluetoothGattCharacteristic mHeartRateControlPoint =
                    new BluetoothGattCharacteristic(HEART_RATE_CONTROL_POINT_UUID,
                            BluetoothGattCharacteristic.PROPERTY_WRITE,
                            BluetoothGattCharacteristic.PERMISSION_WRITE);
            //for heart characteristic end

            //for ancs characteristic start
            BluetoothGattCharacteristic mNotificationSourceCharacteristic =
                    new BluetoothGattCharacteristic(UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD"),
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            /* No permissions */ 0);

            BluetoothGattCharacteristic mDataSourceCharacteristic =
                    new BluetoothGattCharacteristic(UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB"),
                            BluetoothGattCharacteristic.PROPERTY_READ,
                            BluetoothGattCharacteristic.PERMISSION_READ);

            BluetoothGattCharacteristic mControlPointCharacteristic =
                    new BluetoothGattCharacteristic(UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9"),
                            BluetoothGattCharacteristic.PROPERTY_WRITE,
                            BluetoothGattCharacteristic.PERMISSION_WRITE);
            //for ancs characteristic end

            bluetoothGattService.addCharacteristic(mNotificationSourceCharacteristic);
            bluetoothGattService.addCharacteristic(mDataSourceCharacteristic);
            bluetoothGattService.addCharacteristic(mControlPointCharacteristic);


            AdvertiseSettings mAdvSettings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(true)
                    .build();
            AdvertiseData mAdvData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .setIncludeTxPowerLevel(true)
//                    .addServiceUuid(new ParcelUuid(HEART_RATE_SERVICE_UUID))
                    .addServiceUuid(new ParcelUuid(UUID.fromString("00001000-0000-1000-8000-00805F9B34FB")))
                    .build();

            bluetoothGattServer = bluetoothManager.openGattServer(MainActivity.this,
                    bluetoothGattServerCallback);
            bluetoothGattServer.addService(bluetoothGattService);

            bluetoothLeAdvertiser.startAdvertising(mAdvSettings, mAdvData, advertiseCallback);

        }
    }

    private BluetoothGattServer bluetoothGattServer;

    private void stopAdvertise() {
        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            bluetoothLeAdvertiser = null;
        }
    }

    private void initView() {
        (findViewById(R.id.scan_ble)).setEnabled(false);
        information = (TextView) findViewById(R.id.information);
        information.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    private void output(final String content) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                information.append(content + "\n");
            }
        });
    }

    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device != null && !isStartConnect) {
                if (device.getName() != null && device.getName().length() > 0 && device.getName().equals
//                        ("Alert Notification") && isFirstConnectGatt) {
                        ("Heart Rate") && isFirstConnectGatt) {
                    isFirstConnectGatt = false;
                    output(device.getName() + "," + device.getAddress());
                    // create bond
                    output("Start createBond");
                    device.createBond();

                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.i(TAG, "onBatchScanResults: " + results.toArray().toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(TAG, "onScanFailed: " + errorCode);
        }

    };


    private PacketProcessor packet_processor;
    private boolean is_subscribed_characteristics = false;
    private byte[] uid = new byte[4];
    private final static String service_ancs = "7905f431-b5ce-4e99-a40f-4b1e122d00d0";
    private static final String characteristics_notification_source = "9fbf120d-6301-42d9-8c58-25e699a21dbd";
    private static final String characteristics_data_source = "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb";
    private static final String characteristics_control_point = "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9";
    private static final String descriptor_config = "00002902-0000-1000-8000-00805f9b34fb";

    private BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            output("onConnectionStateChange, status: " + status);
            Log.i(TAG, "Status: " + status);
//            BluetoothGatt.GATT_SUCCESS;
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i(TAG, "STATE_CONNECTED");
                    output("STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e(TAG, "STATE_DISCONNECTED");
                    output("STATE_DISCONNECTED");
//                    output("start reconnect");
//                    bluetoothGatt.connect();
                    is_subscribed_characteristics = false;
                    break;
                default:
                    Log.e(TAG, "STATE_OTHER");
                    output("STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            output("onServicesDiscovered, status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(service_ancs));
                if (service != null) {
                    output("find service");

                    BluetoothGattCharacteristic notification_characteristic = service.getCharacteristic
                            (UUID.fromString(characteristics_notification_source));  //for debug
//                            (UUID.fromString(characteristics_data_source));
                    if (notification_characteristic != null) {
                        output("notification characteristic is not null");

                        BluetoothGattDescriptor descriptor = notification_characteristic.getDescriptor(
                                UUID.fromString(descriptor_config));
                        if (descriptor == null) {
                            output("can not find notification descriptor: ");
                        } else {
                            output("find notification descriptor: " + descriptor.getUuid());
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                            boolean isSuccess = bluetoothGatt.writeDescriptor(descriptor);
                            output(isSuccess ? "write operation was initiated success" : "write " +
                                    "operation was initiated fail");

                            boolean isEnableNotification = bluetoothGatt.setCharacteristicNotification
                                    (notification_characteristic, true);
                            if (isEnableNotification) {
                                output("Notification set success: " + notification_characteristic
                                        .getUuid());
                            } else {
                                output("Notification set fail");
                            }
                        }

                    } else {
                        output("notification characteristic is null");
                    }

                    bluetoothLeScanner.stopScan(bleScanCallback);
                } else {
                    output("can not find service");
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            output("onCharacteristicRead");
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                          int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            output("Characteristic Write status:  " + status);
            output("Characteristic Write status:  " + Arrays.toString(characteristic.getValue()));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            if(characteristic.getUuid().toString().equals(characteristics_notification_source)){
                packet_processor = new PacketProcessor();
                packet_processor.init();

                byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    try {
                        if (String.format("%02X", data[0]).equals("00")) {
                            output("Already get notify");
                            // notification value setting.
                            //current, hard coding
                            uid[0] = data[4];
                            uid[1] = data[5];
                            uid[2] = data[6];
                            uid[3] = data[7];
                            byte[] get_notification_attribute = {
                                    (byte) 0x00,
                                    //UID
                                    data[4], data[5], data[6], data[7],
                                    //app id
                                    (byte) 0x00,
                                    //title
                                    (byte) 0x01, (byte) 0xff, (byte) 0xff,
                                    //message
                                    (byte) 0x03, (byte) 0xff, (byte) 0xff
                            };

                            BluetoothGattService service = gatt.getService(UUID.fromString(service_ancs));
                            if (service == null) {
                                output("onCharacteristicChanged:can't find service");
                            } else {
                                output( "onCharacteristicChanged: find service");
                               BluetoothGattCharacteristic chars = service.getCharacteristic(UUID.fromString
                                        (characteristics_control_point));
                                if (chars == null) {
                                    output("onCharacteristicChanged:cant find chara");
                                } else {
                                    output("onCharacteristicChanged:find chara");
                                    chars.setValue(get_notification_attribute);
                                    gatt.writeCharacteristic(chars);
                                }
                            }
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        output("onCharacteristicChanged:error");
                        e.printStackTrace();
                    }
                }
            }

            if(characteristic.getUuid().toString().equals(characteristics_data_source)){
                byte[] get_data = characteristic.getValue();
                packet_processor.processing(get_data);

                if (packet_processor.is_finish_processing()) {
                    output("Packet Processor, app id: " + packet_processor.get_ds_app_id());
                    output("Packet Processor, title: " + packet_processor.get_ds_title());
                    output("Packet Processor, message: " + packet_processor.get_ds_message());
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            output("onDescriptorRead");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            output("onDescriptorWrite &&&");
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(!is_subscribed_characteristics) {
                    BluetoothGattService service = gatt.getService(UUID.fromString(service_ancs));
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString
                            (characteristics_notification_source));

                    if (characteristic == null) {
                        output("onDescriptorWrite &&&: cant find chara");
                    } else {
                        output("onDescriptorWrite &&&: ** find chara :: " + characteristic.getUuid());
                        if (characteristics_notification_source.equals(characteristic.getUuid().toString())) {
                            output("onDescriptorWrite &&&: set notify:: " + characteristic.getUuid());
                            bluetoothGatt.setCharacteristicNotification(characteristic, true);
                            BluetoothGattDescriptor notify_descriptor = characteristic.getDescriptor(
                                    UUID.fromString(descriptor_config));
                            if (descriptor == null) {
                                output("onDescriptorWrite &&&: ** not find desc :: " + notify_descriptor.getUuid());

                            } else {
                                output("onDescriptorWrite &&&: ** find desc :: " + notify_descriptor
                                        .getUuid());
                                notify_descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                bluetoothGatt.writeDescriptor(notify_descriptor);
                                is_subscribed_characteristics = true;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            output("onReliableWriteCompleted");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            output("onReadRemoteRssi");
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            output("onMtuChanged");
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (test_mode == SERVICE_MODE) {
            Log.d(TAG, "-=-=-=-=-=-=-=-= onDestroy -=-=-=-=-=-=-=-=-=");
            stopService(new Intent(MainActivity.this, BLEService.class));
        } else if (test_mode == CUSTOM_MODE) {
            if (bluetoothLeScanner != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    bluetoothLeScanner.stopScan(bleScanCallback);
                } else {
                    bluetoothLeScanner.stopScan(bleScanCallback);
                }
            }

            if (bluetoothGatt != null) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
            if (isRegisterBtOpenReceiver) {
                unregisterReceiver(btReceiver);
            }
            stopAdvertise();
        }

        unPairBluetoothDevice();
    }


    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            output("Bluetooth Le Advertiser onStartSuccess");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    break;
            }
            output("Bluetooth Le Advertiser onStartFailure");
        }
    };

    private BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int
                newState) {
            super.onConnectionStateChange(device, status, newState);
            output("Server: onConnectionStateChange, status: " + status);//need test
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    output("Server: STATE_CONNECTED");
                    if (bluetoothGattServer != null) {
                        List<BluetoothGattService> gattServices = bluetoothGattServer.getServices();
                        for (BluetoothGattService gattService : gattServices) {
                            output("Service UUID Found: " + gattService.getUuid().toString());
                        }
                    }
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    output("Server: STATE_DISCONNECTED");
                    break;
                default:
                    output("Server: STATE_OTHER");
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            output("Server: onServiceAdded");
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int
                offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            output("Server: onCharacteristicReadRequest");

            output("Device tried to read characteristic: " + characteristic.getUuid());
            output("Value: " + Arrays.toString(characteristic.getValue()));
            if (offset != 0) {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET,
                        offset,             /* value (optional) */ null);
                return;
            }
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, characteristic.getValue());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean
                                                         responseNeeded, int
                                                         offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic,
                    preparedWrite,
                    responseNeeded, offset, value);
            output("Server: onCharacteristicWriteRequest");
            output("Server: Characteristic Write request:  " + Arrays.toString(value));
            int status = writeCharacteristic(characteristic, offset, value);
            bluetoothGattServer.sendResponse(device, requestId, status, 0, null);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            output("Server: onDescriptorReadRequest");
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean
                                                     preparedWrite, boolean responseNeeded,
                                             int
                                                     offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite,
                    responseNeeded, offset, value);
            output("Server: onDescriptorWriteRequest");
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
            output("Server: onExecuteWrite");
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            output("Server: onNotificationSent");
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            output("Server: onMtuChanged");
        }
    };

    public int writeCharacteristic(BluetoothGattCharacteristic characteristic, int offset, byte[] value) {
        if (offset != 0) {
            return BluetoothGatt.GATT_INVALID_OFFSET;
        }
        // Heart Rate control point is a 8bit characteristic
        if (value.length != 1) {
            return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
        }
        if ((value[0] & 1) == 1) {
//            getActivity().runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    mHeartRateMeasurementCharacteristic.setValue(INITIAL_EXPENDED_ENERGY,
//                            EXPENDED_ENERGY_FORMAT, /* offset */ 2);
//                    mEditTextEnergyExpended.setText(Integer.toString(INITIAL_EXPENDED_ENERGY));
//                }
//            });
        }
        return BluetoothGatt.GATT_SUCCESS;
    }

    private void unPairBluetoothDevice(){
       Set<BluetoothDevice> pairDevices =  bluetoothAdapter.getBondedDevices();
        if(pairDevices != null && pairDevices.size() > 0){
            for(BluetoothDevice device:pairDevices){
                try {
                    removeBond(device.getClass(),device);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public boolean removeBond(Class btClass, BluetoothDevice btDevice) throws Exception {
        Method removeBondMethod = btClass.getMethod("removeBond");
        Boolean returnValue = (Boolean) removeBondMethod.invoke(btDevice);
        return returnValue.booleanValue();
    }
}
