package com.smarthealth.vitalhub.foundation.bluetooth.callback;

public interface BluetoothGattRssiCallback {
    public void onRssiSuccess(int rssi);
    public void onRssiFailure(Exception e);
}
