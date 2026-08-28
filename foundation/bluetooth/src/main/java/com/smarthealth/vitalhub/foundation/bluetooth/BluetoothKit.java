package com.smarthealth.vitalhub.foundation.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothConnectCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.MultipleBluetoothController;
import com.smarthealth.vitalhub.foundation.bluetooth.scan.BluetoothKitScanner;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.provider.BluetoothProvider;
import com.smarthealth.vitalhub.foundation.bluetooth.scan.BluetoothScanRulesConfig;

import androidx.annotation.Nullable;

public class BluetoothKit {

    public static BluetoothKit getInstance() {
        return BluetoothKitHolder._instance;
    }

    private static class BluetoothKitHolder {
        private static final BluetoothKit _instance = new BluetoothKit();
    }

    private final MultipleBluetoothController multipleBluetoothController;
    private final BluetoothProvider bluetoothProvider;

    private BluetoothKit() {
        this.bluetoothProvider = BluetoothProvider.getInstance();
        this.multipleBluetoothController = new MultipleBluetoothController();
    }

    public BluetoothManager getBluetoothManager() {
        return bluetoothProvider.getBluetoothManager();
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothProvider.getBluetoothAdapter();
    }

    public boolean isSupportLe() {
        return bluetoothProvider.isSupportLe();
    }

    public boolean enableBluetooth() {
        return bluetoothProvider.enableBluetooth();
    }

    public boolean disableBluetooth() {
        return bluetoothProvider.disableBluetooth();
    }

    public boolean isBluetoothEnable() {
        return bluetoothProvider.isBluetoothEnable();
    }

    public void startLeCan(BluetoothScanCallback callback) {
        BluetoothKitScanner.getInstance().startLeScan(callback);
    }

    public void startDiscovery(BluetoothScanCallback callback) {
        BluetoothKitScanner.getInstance().startDiscovery(callback);
    }

    public void startAlternateScan(BluetoothScanCallback callback) {
        BluetoothKitScanner.getInstance().startAlternateScan(callback);
    }

    public void stopScan() {
        BluetoothKitScanner.getInstance().stopScan();
    }

    public void connect(String mac, BluetoothConnectCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("BluetoothConnectCallback can not be Null!");
        }

        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            callback.onConnectFailure(null, new Exception("Not Found Device Exception Occurred! address:" + mac));
            return;
        }

        final boolean isBluetoothEnable = bluetoothProvider.isBluetoothEnable();
        if (!isBluetoothEnable) {
            callback.onConnectFailure(null, new Exception("Bluetooth not enable!"));
            return;
        }

        final BluetoothKitDevice bluetoothKitDevice = bluetoothProvider.convertBluetoothKitDevice(mac);
        if (bluetoothKitDevice != null) {
            bluetoothKitDevice.connect(callback);
        } else {
            scanConnect(mac, callback);
        }
    }

    public void scanConnect(String mac, BluetoothConnectCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("BluetoothConnectCallback can not be Null!");
        }

        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            callback.onConnectFailure(null, new Exception("Not Found Device Exception Occurred! address:" + mac));
            return;
        }

        final boolean isBluetoothEnable = bluetoothProvider.isBluetoothEnable();
        if (!isBluetoothEnable) {
            callback.onConnectFailure(null, new Exception("Bluetooth not enable!"));
            return;
        }

        this.startAlternateScan(new BluetoothScanCallback() {
            @Override
            public void onScanResult(BluetoothKitDevice bluetoothKitDevice) {
                final String address = bluetoothKitDevice.bluetoothDevice
                        .getAddress();
                if (address.equalsIgnoreCase(mac)) {
                    stopScan();
                    bluetoothKitDevice.connect(callback);
                }
            }

            @Override
            public void onScanComplete() {
                callback.onConnectFailure(null, new Exception("connect failure,because of not found the device"));
            }

        });
    }

    public void disconnect(BluetoothKitDevice bluetoothKitDevice){
        if(multipleBluetoothController!=null) {
            multipleBluetoothController.disconnect(bluetoothKitDevice);
        }
    }

    public void disconnects(){
        if(multipleBluetoothController!=null) {
            multipleBluetoothController.disconnects();
        }
    }

    /**
     * @param bluetoothKitDevice 需查询连接状态的设备
     *
     * @return State of the profile connection. One of
     * {@link BluetoothProfile#STATE_CONNECTED},
     * {@link BluetoothProfile#STATE_CONNECTING},
     * {@link BluetoothProfile#STATE_DISCONNECTED},
     * {@link BluetoothProfile#STATE_DISCONNECTING}
     */
    public int getConnectState(BluetoothKitDevice bluetoothKitDevice) {
        if (bluetoothKitDevice != null) {
            return bluetoothKitDevice.getConnectState();
        } else {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    public boolean isConnected(BluetoothKitDevice bluetoothKitDevice) {
        return getConnectState(bluetoothKitDevice) == BluetoothProfile.STATE_CONNECTED;
    }

    public MultipleBluetoothController getMultipleBluetoothController() {
        return multipleBluetoothController;
    }

    public void destroy() {
        if (multipleBluetoothController != null) {
            multipleBluetoothController.destroy();
        }

        BluetoothKitScanner.getInstance().stopScan();
    }

    public static class Builder {

        public Builder setScanRulesConfig(BluetoothScanRulesConfig scanRulesConfig) {
            if(scanRulesConfig==null) {
                throw new IllegalArgumentException();
            }
            BluetoothKitReal.setScanRulesConfig(scanRulesConfig);
            return this;
        }

        public Builder setSpiltWriteSize(int size) {
            if (size > 0) {
                BluetoothKitReal.setSpiltWriteSize(size);
            }
            return this;
        }

        public Builder setConnectTimeout(long connectTimeout) {
            if (connectTimeout < 0) {
                connectTimeout = 100;
            }
            BluetoothKitReal.setConnectTimeout(connectTimeout);
            return this;
        }

        public Builder delayDisconnectCallback(long delayMills) {
            if (delayMills < 0) {
                delayMills = 0;
            }
            BluetoothKitReal.delayDisconnectCallback(delayMills);
            return this;
        }

        public void init() {
            BluetoothKitReal.init();
        }
    }
}
