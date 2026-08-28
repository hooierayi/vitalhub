package com.smarthealth.vitalhub.foundation.bluetooth.callback;

public interface BluetoothWriteCallback {

    public abstract void onWriteSuccess(int index, int amount, byte[] justWrite);

    public abstract void onWriteFailure(Exception exception);
}
