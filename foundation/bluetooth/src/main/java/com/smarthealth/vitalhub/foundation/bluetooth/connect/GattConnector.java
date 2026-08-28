package com.smarthealth.vitalhub.foundation.bluetooth.connect;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitEnv;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitReal;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothConnectCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattIndicateCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattMtuCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattNotifyCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattReadCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattRssiCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.constants.BluetoothConstants;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@SuppressLint("MissingPermission")
public class GattConnector extends BluetoothConnector {

    private final MainHandler mainHandler = new MainHandler(Looper.getMainLooper());

    private BluetoothGattRssiCallback gattRssiCallback;
    private BluetoothGattMtuCallback gattMtuCallback;
    private final Map<String, BluetoothGattNotifyCallback> gattNotifyCallbacks = new ConcurrentHashMap<>();
    private final Map<String, BluetoothGattIndicateCallback> gattIndicateCallbacks = new ConcurrentHashMap<>();
    private final Map<String, BluetoothWriteCallback> gattWriteCallbacks = new ConcurrentHashMap<>();
    private final Map<String, BluetoothGattReadCallback> gattReadCallbacks = new ConcurrentHashMap<>();

    private BluetoothGatt bluetoothGatt;

    public GattConnector(BluetoothKitDevice device) {
        super(device);
    }

    @Override
    public BluetoothEventsCenter newBluetoothEventsCenter() {
        return new GattEventsCenter(this);
    }

    public void addGattRssiCallback(BluetoothGattRssiCallback bluetoothGattRssiCallback) {
        this.gattRssiCallback = bluetoothGattRssiCallback;
    }

    public void addGattMtuCallback(BluetoothGattMtuCallback bluetoothGattMtuCallback) {
        this.gattMtuCallback = bluetoothGattMtuCallback;
    }

    public void addGattNotifyCallback(String uuid, BluetoothGattNotifyCallback bluetoothGattNotifyCallback) {
        this.gattNotifyCallbacks.put(uuid, bluetoothGattNotifyCallback);
    }

    public void addGattIndicateCallback(String uuid, BluetoothGattIndicateCallback bluetoothGattNotifyCallback) {
        this.gattIndicateCallbacks.put(uuid, bluetoothGattNotifyCallback);
    }

    public void addGattWriteCallback(String uuid, BluetoothWriteCallback bluetoothGattNotifyCallback) {
        this.gattWriteCallbacks.put(uuid, bluetoothGattNotifyCallback);
    }

    public void addGattReadCallback(String uuid, BluetoothGattReadCallback bluetoothGattNotifyCallback) {
        this.gattReadCallbacks.put(uuid, bluetoothGattNotifyCallback);
    }

    public void removeGattRssiCallback() {
        this.gattRssiCallback = null;
    }

    public void removeGattMtuCallback() {
        this.gattMtuCallback = null;
    }

    public void removeGattNotifyCallback(String uuid) {
        this.gattNotifyCallbacks.remove(uuid);
    }

    public void removeGattIndicateCallback(String uuid) {
        this.gattIndicateCallbacks.remove(uuid);
    }

    public void removeGattWriteCallback(String uuid) {
        this.gattWriteCallbacks.remove(uuid);
    }

    public void removeGattReadCallback(String uuid) {
        this.gattReadCallbacks.remove(uuid);
    }

    public void clearCharacteristicCallbacks() {
        gattNotifyCallbacks.clear();
        gattIndicateCallbacks.clear();
        gattWriteCallbacks.clear();
        gattReadCallbacks.clear();
    }

    private final class MainHandler extends Handler {

        public MainHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BluetoothConstants.MSG_CONNECT_FAIL: {
                    disconnectGatt();
                    refreshDeviceCache();
                    closeBluetoothGatt();

                    Log.d("BluetoothKit", "MSG_CONNECT_FAIL");
                    //todo reconnect
                    mConnectionState = ConnectionState.FAILURE;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(GattConnector.this);

                    GattConnectStateParameter parameter = (GattConnectStateParameter) msg.obj;
                    int status = parameter.status;
                    if (mConnectCallback != null) {
                        mConnectCallback.onConnectFailure(device, new Exception("gatt error,status->"+status));
                    }
                }
                break;

                case BluetoothConstants.MSG_DISCONNECTED: {
                    mConnectionState = ConnectionState.DISCONNECTED;
                    Log.d("BluetoothKit", "MSG_DISCONNECTED");
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnectedPool(GattConnector.this);

                    disconnectGatt();
                    refreshDeviceCache();
                    closeBluetoothGatt();
                    removeGattRssiCallback();
                    removeGattMtuCallback();
                    clearCharacteristicCallbacks();
                    mainHandler.removeCallbacksAndMessages(null);

                    GattConnectStateParameter parameter = (GattConnectStateParameter) msg.obj;
                    int status = parameter.status;
                    boolean isActive = parameter.isActive();
                    if (mConnectCallback != null)
                        mConnectCallback.onDisConnected(device, isActive);
                }
                break;

                case BluetoothConstants.MSG_CONNECT_TIMEOUT: {
                    disconnectGatt();
                    refreshDeviceCache();
                    closeBluetoothGatt();
                    Log.d("BluetoothKit", "MSG_CONNECT_TIMEOUT");
                    mConnectionState = ConnectionState.FAILURE;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(GattConnector.this);

                    if (mConnectCallback != null)
                        mConnectCallback.onConnectFailure(device, new Exception("connect timeout"));
                }
                break;

                case BluetoothConstants.MSG_DISCOVER_SERVICES: {
                    if (bluetoothGatt != null) {
                        boolean discoverServiceResult = bluetoothGatt.discoverServices();
                        if (!discoverServiceResult) {
                            Message message = mainHandler.obtainMessage();
                            message.what = BluetoothConstants.MSG_DISCOVER_FAIL;
                            mainHandler.sendMessage(message);
                        }
                    } else {
                        Message message = mainHandler.obtainMessage();
                        message.what = BluetoothConstants.MSG_DISCOVER_FAIL;
                        mainHandler.sendMessage(message);
                    }
                }
                break;

                case BluetoothConstants.MSG_DISCOVER_FAIL: {
                    disconnectGatt();
                    refreshDeviceCache();
                    closeBluetoothGatt();

                    Log.d("BluetoothKit", "MSG_DISCOVER_FAIL");
                    mConnectionState = ConnectionState.FAILURE;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(GattConnector.this);

                    if (mConnectCallback != null)
                        mConnectCallback.onConnectFailure(device, new Exception("GATT discover services exception occurred!"));
                }
                break;

                case BluetoothConstants.MSG_DISCOVER_SUCCESS: {
                    mConnectionState = ConnectionState.CONNECTED;
                    isActiveDisconnected = false;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(GattConnector.this);
                    BluetoothKit.getInstance().getMultipleBluetoothController().putConnectedPool(GattConnector.this);

                    Log.d("BluetoothKit", "MSG_DISCOVER_SUCCESS");
                    GattConnectStateParameter para = (GattConnectStateParameter) msg.obj;
                    int status = para.getStatus();
                    if (mConnectCallback != null)
                        mConnectCallback.onConnectSuccess(device);
                }
                break;

                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    @Override
    public synchronized void connect(BluetoothConnectCallback bluetoothConnectCallback) {
        super.connect(bluetoothConnectCallback);
        final Context context = BluetoothKitEnv.requireApp();
        final BluetoothDevice remote = device.getBluetoothDevice();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            bluetoothGatt = remote.connectGatt(context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_2M, mainHandler);
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
            // A variant of connectGatt with Handled can't be used here.
            // Check https://github.com/NordicSemiconductor/Android-BLE-Library/issues/54
            // This bug specifically occurs in SDK 26 and is fixed in SDK 27
            bluetoothGatt = remote.connectGatt(context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_2M/*, handler*/);
        } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            bluetoothGatt = remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = remote.connectGatt(context, false, gattCallback);
        }

        if (bluetoothGatt != null) {
            if (bluetoothConnectCallback != null) {
                bluetoothConnectCallback.onConnectStart();
            }
            Message message = mainHandler.obtainMessage();
            message.what = BluetoothConstants.MSG_CONNECT_TIMEOUT;
            final long timeout = BluetoothKitReal.getConnectTimeout();
            mainHandler.sendMessageDelayed(message, timeout);
        } else {
            disconnectGatt();
            refreshDeviceCache();
            closeBluetoothGatt();
            mConnectionState = ConnectionState.FAILURE;
            BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(this);
            if (bluetoothConnectCallback != null) {
                bluetoothConnectCallback.onConnectFailure(device, new Exception("GATT connect exception occurred!"));
            }
        }
    }

    @Override
    public synchronized void disconnect() {
        super.disconnect();
        disconnectGatt();
    }

    @Override
    public synchronized void destroy() {
        super.destroy();
        disconnectGatt();
        refreshDeviceCache();
        closeBluetoothGatt();
        removeConnectCallback();
        removeGattRssiCallback();
        removeGattMtuCallback();
        clearCharacteristicCallbacks();
        mainHandler.removeCallbacksAndMessages(null);
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    public BluetoothKitDevice getDevice() {
        return device;
    }

    private void disconnectGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    private void refreshDeviceCache() {
        try {
            BluetoothGatt localBlueToothGatt = bluetoothGatt;
            if (localBlueToothGatt == null) {
                return;
            }
            Method localMethod = localBlueToothGatt.getClass().getMethod("refresh");
            localMethod.setAccessible(true);
            localMethod.invoke(localBlueToothGatt);
            localMethod.setAccessible(false);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeBluetoothGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("BluetoothKit", "onConnectionStateChange: status->"+status
                    +",newState->"+newState+",connectionState->"+mConnectionState);
            //取消超时
            mainHandler.removeMessages(BluetoothConstants.MSG_CONNECT_TIMEOUT);
            //处理连接逻辑
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Message message = mainHandler.obtainMessage();
                message.what = BluetoothConstants.MSG_DISCOVER_SERVICES;
                mainHandler.sendMessageDelayed(message, 300);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (mConnectionState == ConnectionState.CONNECTING) {
                    Message message = Message.obtain();
                    message.what = BluetoothConstants.MSG_CONNECT_FAIL;
                    message.obj = new GattConnectStateParameter(status);
                    mainHandler.sendMessage(message);

                } else if (mConnectionState == ConnectionState.CONNECTED) {
                    Message message = Message.obtain();
                    message.what = BluetoothConstants.MSG_DISCONNECTED;
                    GattConnectStateParameter parameter = new GattConnector.GattConnectStateParameter(status);
                    parameter.setActive(isActiveDisconnected);
                    message.obj = parameter;
                    mainHandler.sendMessageDelayed(message,BluetoothKitReal.getDisconnectCallbackDelay());

                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Message message = mainHandler.obtainMessage();
                message.what = BluetoothConstants.MSG_DISCOVER_SUCCESS;
                message.obj = new GattConnectStateParameter(status);
                mainHandler.sendMessage(message);

            } else {
                Message message = mainHandler.obtainMessage();
                message.what = BluetoothConstants.MSG_DISCOVER_FAIL;
                mainHandler.sendMessage(message);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            for (Map.Entry<String, BluetoothGattReadCallback> entry : gattReadCallbacks.entrySet()) {
                final BluetoothGattReadCallback callback = entry.getValue();
                final String callbackKey = entry.getKey();
                if (characteristic.getUuid().toString().equalsIgnoreCase(callbackKey)) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        final byte[] payload = characteristic.getValue();
                        callback.onReadSuccess(payload);
                    } else {
                        callback.onReadFailure(new Exception("gatt error,status->" + status));
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            for (Map.Entry<String, BluetoothWriteCallback> entry : gattWriteCallbacks.entrySet()) {
                final BluetoothWriteCallback callback = entry.getValue();
                final String callbackKey = entry.getKey();
                if (characteristic.getUuid().toString().equalsIgnoreCase(callbackKey)) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        final byte[] payload = characteristic.getValue();
                        callback.onWriteSuccess(1, 1, payload);
                    } else {
                        callback.onWriteFailure(new Exception("gatt error,status->" + status));
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            for (Map.Entry<String, BluetoothGattNotifyCallback> entry : gattNotifyCallbacks.entrySet()) {
                final BluetoothGattNotifyCallback callback = entry.getValue();
                final String callbackKey = entry.getKey();
                if (characteristic.getUuid().toString().equalsIgnoreCase(callbackKey)) {
                    final byte[] payload = characteristic.getValue();
                    callback.onCharacteristicChanged(payload);
                }
            }

            for (Map.Entry<String, BluetoothGattIndicateCallback> entry : gattIndicateCallbacks.entrySet()) {
                final BluetoothGattIndicateCallback callback = entry.getValue();
                final String callbackKey = entry.getKey();
                if (characteristic.getUuid().toString().equalsIgnoreCase(callbackKey)) {
                    final byte[] payload = characteristic.getValue();
                    callback.onCharacteristicChanged(payload);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            for (Map.Entry<String, BluetoothGattNotifyCallback> entry : gattNotifyCallbacks.entrySet()) {
                final BluetoothGattNotifyCallback callback = entry.getValue();
                final String callbackKey = entry.getKey();
                if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(callbackKey)) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        callback.onNotifySuccess();
                    } else {
                        callback.onNotifyFailure(new Exception("gatt error,status->" + status));
                    }
                }
            }

            for (Map.Entry<String, BluetoothGattIndicateCallback> entry : gattIndicateCallbacks.entrySet()) {
                final BluetoothGattIndicateCallback callback = entry.getValue();
                final String callbackKey = entry.getKey();
                if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(callbackKey)) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        callback.onIndicateSuccess();
                    } else {
                        callback.onIndicateFailure(new Exception("gatt error,status->" + status));
                    }
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);

            if (gattRssiCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gattRssiCallback.onRssiSuccess(rssi);
                } else {
                    gattRssiCallback.onRssiFailure(new Exception("gatt error,status->" + status));
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);

            if (gattMtuCallback != null) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gattMtuCallback.onMtuChanged(mtu);
                } else {
                    gattMtuCallback.onMtuChangeFailure(new Exception("gatt error,status->" + status));
                }
            }
        }

        @Keep
        public void onConnectionUpdated(BluetoothGatt gatt, int interval, int latency, int timeout, int status) {
            Log.d("BluetoothKit", "status->" + status +
                    ",Connection parameters updated " +
                    "(interval: " + (interval * 1.25) + "ms," +
                    " latency: " + latency + ", timeout: " + (timeout * 10) + "ms)");
        }

    };

    private static final class GattConnectStateParameter {
        private int status;
        private boolean isActive;

        public GattConnectStateParameter(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public boolean isActive() {
            return isActive;
        }

        public void setActive(boolean active) {
            isActive = active;
        }
    }
}
