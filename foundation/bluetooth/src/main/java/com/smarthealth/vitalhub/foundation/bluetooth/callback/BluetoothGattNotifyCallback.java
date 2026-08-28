package com.smarthealth.vitalhub.foundation.bluetooth.callback;

public interface BluetoothGattNotifyCallback {

    public abstract void onNotifySuccess();

    public abstract void onNotifyFailure(Exception exception);

    public abstract void onCharacteristicChanged(byte[] payload);
}
