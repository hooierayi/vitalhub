package com.smarthealth.vitalhub.foundation.bluetooth.connect;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothConnectCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.provider.BluetoothProvider;

public abstract class BluetoothConnector {

    protected final BluetoothKitDevice device;
    protected BluetoothConnectCallback mConnectCallback;
    protected ConnectionState mConnectionState;

    protected boolean isActiveDisconnected = false;

    enum ConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED,
        FAILURE,
        DISCONNECTED
    }

    public BluetoothConnector(BluetoothKitDevice device) {
        this.device = device;
    }

    public String getKey(){
        return device.getKey();
    }

    protected void addConnectCallback(BluetoothConnectCallback bluetoothConnectCallback){
        this.mConnectCallback= bluetoothConnectCallback;
    }

    protected void removeConnectCallback(){
        this.mConnectCallback=null;
    }

    public synchronized void connect(BluetoothConnectCallback bluetoothConnectCallback){
        if (bluetoothConnectCallback == null) {
            throw new IllegalArgumentException("BluetoothConnectCallback can not be Null!");
        }

        if(!BluetoothProvider.getInstance().isBluetoothEnable()) {
            bluetoothConnectCallback.onConnectFailure(device,new Exception("Bluetooth not enable!"));
            return;
        }

        if (device == null || device.getBluetoothDevice() == null) {
            bluetoothConnectCallback.onConnectFailure(device,new Exception("Not Found Device Exception Occurred!"));
            return;
        }

        addConnectCallback(bluetoothConnectCallback);
        this.mConnectionState=ConnectionState.CONNECTING;
    }

    public synchronized void disconnect(){
        isActiveDisconnected=true;
    }

    public synchronized void destroy(){
        mConnectionState=ConnectionState.IDLE;
    }

    public abstract BluetoothEventsCenter newBluetoothEventsCenter();

    public ConnectionState getConnectionState(){
        return mConnectionState;
    }
}
