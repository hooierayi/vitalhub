package com.smarthealth.vitalhub.foundation.bluetooth.connect;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;

public abstract class BluetoothEventsCenter {

    public abstract void write(byte[] payload, BluetoothWriteCallback bluetoothWriteCallback);

}
