package com.smarthealth.vitalhub.foundation.bluetooth.callback;

public interface BluetoothGattReadCallback {

    public abstract void onReadSuccess(byte[] payload);

    public abstract void onReadFailure(Exception exception);
}
