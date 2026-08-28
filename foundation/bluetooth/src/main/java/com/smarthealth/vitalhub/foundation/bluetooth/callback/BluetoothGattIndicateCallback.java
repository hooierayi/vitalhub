package com.smarthealth.vitalhub.foundation.bluetooth.callback;

public interface BluetoothGattIndicateCallback {

    public abstract void onIndicateSuccess();

    public abstract void onIndicateFailure(Exception exception);

    public abstract void onCharacteristicChanged(byte[] payload);
}
