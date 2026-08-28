package com.smarthealth.vitalhub.foundation.bluetooth.callback;

public interface BluetoothGattMtuCallback {
    public void onMtuChanged(int mtu);
    public void onMtuChangeFailure(Exception e);
}
