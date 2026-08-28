package com.smarthealth.vitalhub.foundation.bluetooth.callback;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;

public interface BluetoothConnectCallback {
    public void onConnectStart();
    public void onConnectFailure(BluetoothKitDevice device,Exception e);
    public void onConnectSuccess(BluetoothKitDevice device);
    public void onDisConnected(BluetoothKitDevice device,boolean active);
}
