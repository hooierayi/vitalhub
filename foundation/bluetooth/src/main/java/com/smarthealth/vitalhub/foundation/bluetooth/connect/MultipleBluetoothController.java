package com.smarthealth.vitalhub.foundation.bluetooth.connect;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothGattDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothSppDevice;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MultipleBluetoothController {

    private final static int MAX_CONNECT_SIZE=5;

    /**
     * 管理已连接的设备列表
     * 当设备达到最大值时，自动断开最先连接的设备。维持在一个阈值
     */
    private final LruHashMap<String,BluetoothConnector> mConnectedPool;

    /**
     * 管理需要连接的设备列表
     */
    private final HashMap<String,BluetoothConnector> mConnectingPool;

    public MultipleBluetoothController() {
        this.mConnectedPool=new LruHashMap<>(MAX_CONNECT_SIZE);
        this.mConnectingPool=new HashMap<>();
    }

    public synchronized @Nullable BluetoothConnector buildConnector(BluetoothKitDevice device){
        if(isContainDevice(device)||isConnecting(device)) {
            return null;
        }
        final BluetoothConnector bluetoothConnector=newConnector(device);
        if(bluetoothConnector==null) {
            return null;
        }

        if(!isConnecting(device)) {
            mConnectingPool.put(device.getKey(),bluetoothConnector);
        }
        return bluetoothConnector;
    }

    public synchronized void removeConnector(BluetoothConnector bluetoothConnector){
        if(bluetoothConnector==null) {
            return;
        }
        final String key=bluetoothConnector.getKey();
        Log.d("BluetoothKit", "removeConnector: "+key);
        mConnectingPool.remove(key);
    }

    public synchronized void putConnectedPool(BluetoothConnector bluetoothConnector){
        if(bluetoothConnector==null) {
            return;
        }
        final String key=bluetoothConnector.getKey();
        Log.d("BluetoothKit", "putConnectedPool: "+key);
        if(!mConnectedPool.containsKey(key)) {
            mConnectedPool.put(key,bluetoothConnector);
        }
    }

    public synchronized void removeConnectedPool(BluetoothConnector bluetoothConnector){
        if(bluetoothConnector==null) {
            return;
        }
        final String key=bluetoothConnector.getKey();
        Log.d("BluetoothKit", "removeConnectedPool: "+key);
        mConnectedPool.remove(key);
    }

    public synchronized @Nullable BluetoothConnector getBluetoothConnector(BluetoothKitDevice device){
        if(device!=null) {
            final String key=device.getKey();
            if(mConnectedPool.containsKey(key)) {
                return mConnectedPool.get(key);
            }
        }
        return null;
    }

    public synchronized void disconnect(BluetoothKitDevice device){
        if(isContainDevice(device)) {
            final BluetoothConnector bluetoothConnector=getBluetoothConnector(device);
            if(bluetoothConnector!=null) {
                bluetoothConnector.disconnect();
            }
        }
    }

    public synchronized void disconnects(){
        for (Map.Entry<String, BluetoothConnector> bluetoothEntry : mConnectedPool.entrySet()) {
            bluetoothEntry.getValue().disconnect();
        }
        mConnectedPool.clear();
    }

    public synchronized boolean isContainDevice(BluetoothKitDevice device) {
        if(device!=null) {
            Log.d("BluetoothKit", "isContainDevice: "+device.getKey());
        }
        return device != null && mConnectedPool.containsKey(device.getKey());
    }

    public synchronized boolean isContainDevice(BluetoothDevice bluetoothDevice) {
        return bluetoothDevice != null && mConnectedPool.containsKey(bluetoothDevice.getAddress());
    }

    private boolean isConnecting(BluetoothKitDevice device){
        return device != null && mConnectingPool.containsKey(device.getKey());
    }

    private @Nullable BluetoothConnector newConnector(BluetoothKitDevice device){
        if(device instanceof BluetoothGattDevice) {
            return new GattConnector(device);
        }else if(device instanceof BluetoothSppDevice) {
            return new SppConnector(device);
        }
        return null;
    }

    public synchronized void destroy() {
        for (Map.Entry<String, BluetoothConnector> bluetoothEntry : mConnectedPool.entrySet()) {
            bluetoothEntry.getValue().destroy();
        }
        mConnectedPool.clear();
        for (Map.Entry<String, BluetoothConnector> bluetoothEntry : mConnectingPool.entrySet()) {
            bluetoothEntry.getValue().destroy();
        }
        mConnectingPool.clear();
    }

    public static class LruHashMap<K,V> extends LinkedHashMap<K,V>{
        private final int MAX_SIZE;

        public LruHashMap(int saveSize) {
            super((int) Math.ceil(saveSize / 0.75) + 1, 0.75f, true);
            MAX_SIZE = saveSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry eldest) {
            if (size() > MAX_SIZE && eldest.getValue() instanceof BluetoothConnector) {
                ((BluetoothConnector) eldest.getValue()).disconnect();
            }
            return size() > MAX_SIZE;
        }

        @Override
        @NonNull
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Entry<K, V> entry : entrySet()) {
                sb.append(String.format("%s:%s ", entry.getKey(), entry.getValue()));
            }
            return sb.toString();
        }
    }
}
