package com.smarthealth.vitalhub.foundation.bluetooth;

import android.bluetooth.BluetoothDevice;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothConnectCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.BluetoothConnector;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.MultipleBluetoothController;

public abstract class BluetoothKitDevice {

    protected final BluetoothDevice bluetoothDevice;

    public BluetoothKitDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public String getKey(){
        if(bluetoothDevice!=null) {
            return bluetoothDevice.getAddress();
        }
        return "";
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public void connect(BluetoothConnectCallback bluetoothConnectCallback) {
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        if(multipleBluetoothController.isContainDevice(this)) {
            bluetoothConnectCallback.onConnectFailure(this,new Exception("the device already connected!"));
            return;
        }
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.buildConnector(this);
        if(bluetoothConnector!=null) {
            bluetoothConnector.connect(bluetoothConnectCallback);
        }
    }

    public void disconnect() {
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        multipleBluetoothController.disconnect(this);
    }

    public abstract int getConnectState();
}
