package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitEnv;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothSppDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback;

@SuppressLint("MissingPermission")
public class BluetoothClassicScanner extends BluetoothScanner{

    private BluetoothDiscoveryReceiver mReceiver;

    private BluetoothClassicScanner() {
    }

    public static BluetoothClassicScanner getInstance() {
        return BluetoothClassicScannerHolder._instance;
    }

    private static class BluetoothClassicScannerHolder {
        private static final BluetoothClassicScanner _instance = new BluetoothClassicScanner();
    }

    @Override
    public void startScan(BluetoothScanCallback callback) {
        super.startScan(callback);
        registerDiscoveryReceiver();
        mBluetoothProvider.startDiscovery();
    }

    @Override
    public void stopScan() {
        unregisterDiscoveryReceiver();
        mBluetoothProvider.cancelDiscover();
        super.stopScan();
    }

    @Override
    public void cancelScan() {
        unregisterDiscoveryReceiver();
        mBluetoothProvider.cancelDiscover();
        super.cancelScan();
    }

    private void registerDiscoveryReceiver(){
        unregisterDiscoveryReceiver();
        if(mReceiver == null) {
            mReceiver = new BluetoothDiscoveryReceiver();
            final Context context=BluetoothKitEnv.requireApp();
            IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_FOUND);
            context.registerReceiver(mReceiver, filter);
        }
    }

    private void unregisterDiscoveryReceiver(){
        if(mReceiver != null) {
            final Context context=BluetoothKitEnv.requireApp();
            context.unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private final class BluetoothDiscoveryReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,
                        Short.MIN_VALUE);
                Log.d("scanner", "onReceive: "+device.toString());
                int type=device.getType();
                if(type!=BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                    BluetoothKitDevice bluetoothKitDevice=null;
                    if(type==BluetoothDevice.DEVICE_TYPE_LE) {
                        bluetoothKitDevice=new BluetoothGattDevice(device,null,rssi,System.currentTimeMillis());
                    }else {
                        bluetoothKitDevice=new BluetoothSppDevice(device,rssi);
                    }
                    notifyDeviceFounded(bluetoothKitDevice);
                }
            }
        }
    }

}
