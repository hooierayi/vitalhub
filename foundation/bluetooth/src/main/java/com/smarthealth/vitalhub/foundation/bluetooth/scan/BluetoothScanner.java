package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.provider.BluetoothProvider;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback;

public abstract class BluetoothScanner {

    private BluetoothScanCallback callback;

    protected BluetoothProvider mBluetoothProvider;

    public BluetoothScanner() {
        this.mBluetoothProvider=BluetoothProvider.getInstance();
    }

    public static BluetoothScanner newsInstance(BluetoothKitScanner.Type type){
        switch (type) {
            case CLASSIC:
                return createClassicBluetoothScanner();
            case LE:
                return createLeBluetoothScanner();
            default:
                throw new IllegalStateException("unknown search type" + type);
        }
    }

    private static BluetoothScanner createClassicBluetoothScanner(){
        return BluetoothClassicScanner.getInstance();
    }

    private static BluetoothScanner createLeBluetoothScanner(){
        return BluetoothLeScanner.getInstance();
    }

    public void startScan(BluetoothScanCallback callback){
        this.callback =callback;
        if(callback!=null) {
            callback.onScanStart();
        }
    }

    public void stopScan(){
        if(callback!=null) {
            callback.onScanComplete();
        }
        callback=null;
    }

    public void cancelScan(){
        if(callback!=null) {
            callback.onScanCancel();
        }
        callback=null;
    }

    void notifyDeviceFounded(BluetoothKitDevice bluetoothKitDevice){
        if(callback != null) {
            callback.onScanResult(bluetoothKitDevice);
        }
    }
}
