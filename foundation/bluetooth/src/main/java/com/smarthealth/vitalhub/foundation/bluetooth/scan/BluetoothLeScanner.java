package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback;

public class BluetoothLeScanner extends BluetoothScanner implements BluetoothAdapter.LeScanCallback {

    private BluetoothLeScanner() {
    }

    public static BluetoothLeScanner getInstance() {
        return BluetoothLeScannerHolder.instance;
    }

    private static class BluetoothLeScannerHolder {
        private static final BluetoothLeScanner instance = new BluetoothLeScanner();
    }

    @Override
    public void startScan(BluetoothScanCallback callback) {
        super.startScan(callback);
        mBluetoothProvider.startLeScan(this);
    }

    @Override
    public void stopScan() {
        mBluetoothProvider.stopScanLe(this);
        super.stopScan();
    }

    @Override
    public void cancelScan() {
        mBluetoothProvider.stopScanLe(this);
        super.cancelScan();
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        notifyDeviceFounded(new BluetoothGattDevice(device,scanRecord,rssi,System.currentTimeMillis()));
    }
}
