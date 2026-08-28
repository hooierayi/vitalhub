package com.smarthealth.vitalhub.foundation.bluetooth.provider;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitEnv;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothSppDevice;

import java.util.Set;
import java.util.UUID;

import androidx.annotation.Nullable;

@SuppressLint("MissingPermission")
public class BluetoothProvider {

    public static BluetoothProvider getInstance() {
        return BluetoothProviderHolder._instance;
    }

    private static class BluetoothProviderHolder {
        private static final BluetoothProvider _instance = new BluetoothProvider();
    }

    private BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;

    private BluetoothProvider() {
        final Context context = BluetoothKitEnv.requireApp();
        if (isSupportLe()) {
            this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        }
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public boolean isSupportLe() {
        final Context context = BluetoothKitEnv.requireApp();
        return context.getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public boolean enableBluetooth() {
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.enable();
        }
        return false;
    }

    public boolean disableBluetooth() {
        if (bluetoothAdapter != null) {
            final boolean isEnabled = bluetoothAdapter.isEnabled();
            if (!isEnabled) {
                return true;
            }
            return bluetoothAdapter.disable();
        }
        return false;
    }

    public boolean isBluetoothEnable() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean startLeScan(BluetoothAdapter.LeScanCallback leScanCallback){
        if(bluetoothAdapter == null) {
            return false;
        }

        if(!isSupportLe()) {
            return false;
        }

        return bluetoothAdapter.startLeScan(leScanCallback);
    }

    public boolean startLeScan(UUID[] serviceUuids,BluetoothAdapter.LeScanCallback leScanCallback){
        if(bluetoothAdapter == null) {
            return false;
        }

        if(!isSupportLe()) {
            return false;
        }

        return bluetoothAdapter.startLeScan(serviceUuids, leScanCallback);
    }

    public void stopScanLe(BluetoothAdapter.LeScanCallback leScanCallback){
        bluetoothAdapter.stopLeScan(leScanCallback);
    }

    public boolean startDiscovery(){
        if(bluetoothAdapter == null) {
            return false;
        }
        if(bluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
            return false;
        }

        if(isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();
        return true;
    }

    public boolean isDiscovering(){
        if(bluetoothAdapter == null) {
            return false;
        }
        return bluetoothAdapter.isDiscovering();
    }

    public boolean cancelDiscover(){
        boolean status = false;

        if(bluetoothAdapter == null) {
            return false;
        }

        if(isDiscovering()) {
            status = bluetoothAdapter.cancelDiscovery();
        }

        return status;
    }

    public Set<BluetoothDevice> getBondedDevices(){
        return bluetoothAdapter.getBondedDevices();
    }

    public  @Nullable
    BluetoothKitDevice convertBluetoothKitDevice(String mac) {
        final BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
        final BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(mac);
        return convertBluetoothKitDevice(bluetoothDevice);
    }

    public  @Nullable
    BluetoothKitDevice convertBluetoothKitDevice(BluetoothDevice bluetoothDevice) {
        final int type = bluetoothDevice.getType();
        if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC || type == BluetoothDevice.DEVICE_TYPE_DUAL) {
            return new BluetoothSppDevice(bluetoothDevice);
        } else if (type == BluetoothDevice.DEVICE_TYPE_LE) {
            return new BluetoothGattDevice(bluetoothDevice);
        }
        return null;
    }
}
