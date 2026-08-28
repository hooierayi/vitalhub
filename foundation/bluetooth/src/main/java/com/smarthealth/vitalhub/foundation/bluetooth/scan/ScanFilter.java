package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;

public interface ScanFilter {
    boolean accept(BluetoothKitDevice bluetoothKitDevice);
}
