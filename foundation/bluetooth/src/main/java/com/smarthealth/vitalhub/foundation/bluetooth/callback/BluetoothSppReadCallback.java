package com.smarthealth.vitalhub.foundation.bluetooth.callback;

public interface BluetoothSppReadCallback {

    public void onPayloadChanged(byte[] payload);

    public void onReadFailure(Exception exception);
}
