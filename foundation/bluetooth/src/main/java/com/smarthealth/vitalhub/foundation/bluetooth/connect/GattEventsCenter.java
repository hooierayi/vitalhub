package com.smarthealth.vitalhub.foundation.bluetooth.connect;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattIndicateCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattMtuCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattNotifyCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattReadCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattRssiCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;

import java.util.UUID;

@SuppressLint("MissingPermission")
public class GattEventsCenter extends BluetoothEventsCenter{

    private static final String UUID_CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";

    private final BluetoothGatt mBluetoothGatt;
    private final GattConnector mGattConnector;

    private BluetoothGattService mGattService;
    private BluetoothGattCharacteristic mCharacteristic;

    GattEventsCenter(GattConnector gattConnector){
        this.mGattConnector=gattConnector;
        this.mBluetoothGatt=gattConnector.getBluetoothGatt();
    }

    public GattEventsCenter withUUID(UUID service,UUID characteristic){
        if(service!=null&&mBluetoothGatt!=null) {
            mGattService=mBluetoothGatt.getService(service);
        }
        if(mGattService!=null&&characteristic!=null) {
            mCharacteristic=mGattService.getCharacteristic(characteristic);
        }
        return this;
    }

    public GattEventsCenter withUUIDString(String serviceUUID,String characteristicUUID){
        return withUUID(fromUUID(serviceUUID),fromUUID(characteristicUUID));
    }

    private UUID fromUUID(String uuid){
        return uuid == null ? null : UUID.fromString(uuid);
    }

    /*------------------------------- main operation ----------------------------------- */

    /**
     * notify
     */
    public void enableCharacteristicNotify(BluetoothGattNotifyCallback notifyCallback,
                                           boolean userCharacteristicDescriptor) {
        if (mCharacteristic != null
                && (mCharacteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            final String uuid_notify=mCharacteristic.getUuid().toString();
            handleCharacteristicNotifyCallback(notifyCallback, uuid_notify);
            setCharacteristicNotification(mBluetoothGatt, mCharacteristic, userCharacteristicDescriptor, true, notifyCallback);
        } else {
            if (notifyCallback != null)
                notifyCallback.onNotifyFailure(new Exception("this characteristic not support notify!"));
        }
    }

    /**
     * stop notify
     */
    public boolean disableCharacteristicNotify(boolean useCharacteristicDescriptor) {
        if (mCharacteristic != null
                && (mCharacteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            return setCharacteristicNotification(mBluetoothGatt, mCharacteristic,
                    useCharacteristicDescriptor, false, null);
        } else {
            return false;
        }
    }

    /**
     * notify setting
     */
    private boolean setCharacteristicNotification(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic,
            boolean useCharacteristicDescriptor,
            boolean enable,
            BluetoothGattNotifyCallback notifyCallback
    ) {
        if (gatt == null || characteristic == null) {
            if (notifyCallback != null)
                notifyCallback.onNotifyFailure(new Exception("gatt or characteristic equal null"));
            return false;
        }

        boolean success1 = gatt.setCharacteristicNotification(characteristic, enable);
        if (!success1) {
            if (notifyCallback != null)
                notifyCallback.onNotifyFailure(new Exception("gatt setCharacteristicNotification fail"));
            return false;
        }

        BluetoothGattDescriptor descriptor;
        if (useCharacteristicDescriptor) {
            descriptor = characteristic.getDescriptor(characteristic.getUuid());
        } else {
            descriptor = characteristic.getDescriptor(fromUUID(UUID_CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR));
        }
        if (descriptor == null) {
            if (notifyCallback != null)
                notifyCallback.onNotifyFailure(new Exception("descriptor equals null"));
            return false;
        } else {
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE :
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            boolean success2 = gatt.writeDescriptor(descriptor);
            if (!success2) {
                if (notifyCallback != null)
                    notifyCallback.onNotifyFailure(new Exception("gatt writeDescriptor fail"));
            }
            return success2;
        }
    }

    /**
     * indicate
     */
    public void enableCharacteristicIndicate(BluetoothGattIndicateCallback indicateCallback,
                                             boolean useCharacteristicDescriptor) {
        if (mCharacteristic != null
                && (mCharacteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            final String uuid_indicate=mCharacteristic.getUuid().toString();
            handleCharacteristicIndicateCallback(indicateCallback, uuid_indicate);
            setCharacteristicIndication(mBluetoothGatt, mCharacteristic,
                    useCharacteristicDescriptor, true, indicateCallback);
        } else {
            if (indicateCallback != null)
                indicateCallback.onIndicateFailure(new Exception("this characteristic not support indicate!"));
        }
    }


    /**
     * stop indicate
     */
    public boolean disableCharacteristicIndicate(boolean userCharacteristicDescriptor) {
        if (mCharacteristic != null
                && (mCharacteristic.getProperties() | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            return setCharacteristicIndication(mBluetoothGatt, mCharacteristic,
                    userCharacteristicDescriptor, false, null);
        } else {
            return false;
        }
    }

    /**
     * indicate setting
     */
    private boolean setCharacteristicIndication(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic,
            boolean useCharacteristicDescriptor,
            boolean enable,
            BluetoothGattIndicateCallback indicateCallback
    ) {
        if (gatt == null || characteristic == null) {
            if (indicateCallback != null)
                indicateCallback.onIndicateFailure(new Exception("gatt or characteristic equal null"));
            return false;
        }

        boolean success1 = gatt.setCharacteristicNotification(characteristic, enable);
        if (!success1) {
            if (indicateCallback != null)
                indicateCallback.onIndicateFailure(new Exception("gatt setCharacteristicNotification fail"));
            return false;
        }

        BluetoothGattDescriptor descriptor;
        if (useCharacteristicDescriptor) {
            descriptor = characteristic.getDescriptor(characteristic.getUuid());
        } else {
            descriptor = characteristic.getDescriptor(fromUUID(UUID_CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR));
        }
        if (descriptor == null) {
            if (indicateCallback != null)
                indicateCallback.onIndicateFailure(new Exception("descriptor equals null"));
            return false;
        } else {
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE :
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            boolean success2 = gatt.writeDescriptor(descriptor);
            if (!success2) {
                if (indicateCallback != null)
                    indicateCallback.onIndicateFailure(new Exception("gatt writeDescriptor fail"));
            }
            return success2;
        }
    }

    /**
     * write
     */
    @Override
    public void write(byte[] payload, BluetoothWriteCallback bluetoothWriteCallback) {
        this.writeCharacteristic(payload, bluetoothWriteCallback);
    }

    public void writeCharacteristic(byte[] payload, BluetoothWriteCallback writeCallback) {
        if (payload == null || payload.length <= 0) {
            if (writeCallback != null)
                writeCallback.onWriteFailure(new Exception("the data to be written is empty"));
            return;
        }

        if (mCharacteristic == null
                || (mCharacteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
            if (writeCallback != null)
                writeCallback.onWriteFailure(new Exception("this characteristic not support write!"));
            return;
        }

        final String uuid_write=mCharacteristic.getUuid().toString();
        if (mCharacteristic.setValue(payload)) {
            handleCharacteristicWriteCallback(writeCallback, uuid_write);
            if (!mBluetoothGatt.writeCharacteristic(mCharacteristic)) {
                if (writeCallback != null)
                    writeCallback.onWriteFailure(new Exception("gatt writeCharacteristic fail"));
            }
        } else {
            if (writeCallback != null)
                writeCallback.onWriteFailure(new Exception("Updates the locally stored value of this characteristic fail"));
        }
    }

    /**
     * read
     */
    public void readCharacteristic(BluetoothGattReadCallback readCallback) {
        if (mCharacteristic != null
                && (mCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {

            final String uuid_read=mCharacteristic.getUuid().toString();
            handleCharacteristicReadCallback(readCallback, uuid_read);
            if (!mBluetoothGatt.readCharacteristic(mCharacteristic)) {
                if (readCallback != null)
                    readCallback.onReadFailure(new Exception("gatt readCharacteristic fail"));
            }
        } else {
            if (readCallback != null)
                readCallback.onReadFailure(new Exception("this characteristic not support read!"));
        }
    }

    /**
     * rssi
     */
    public void readRemoteRssi(BluetoothGattRssiCallback rssiCallback) {
        handleRSSIReadCallback(rssiCallback);
        if (!mBluetoothGatt.readRemoteRssi()) {
            if (rssiCallback != null)
                rssiCallback.onRssiFailure(new Exception("gatt readRemoteRssi fail"));
        }
    }

    /**
     * set mtu
     */
    public void setMtu(int requiredMtu, BluetoothGattMtuCallback mtuChangedCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            handleSetMtuCallback(mtuChangedCallback);
            if (!mBluetoothGatt.requestMtu(requiredMtu)) {
                if (mtuChangedCallback != null)
                    mtuChangedCallback.onMtuChangeFailure(new Exception("gatt requestMtu fail"));
            }
        } else {
            if (mtuChangedCallback != null)
                mtuChangedCallback.onMtuChangeFailure(new Exception("API level lower than 21"));
        }
    }

    /**
     * requestConnectionPriority
     *
     * @param connectionPriority Request a specific connection priority. Must be one of
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED},
     *                           {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}
     *                           or {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}.
     * @throws IllegalArgumentException If the parameters are outside of their
     *                                  specified range.
     */
    public boolean requestConnectionPriority(int connectionPriority) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return mBluetoothGatt.requestConnectionPriority(connectionPriority);
        }
        return false;
    }

    //**************************************** Handle call back ******************************************//

    /**
     * notify
     */
    private void handleCharacteristicNotifyCallback(BluetoothGattNotifyCallback notifyCallback,
                                                    String uuid_notify) {
        if (notifyCallback != null) {
            mGattConnector.addGattNotifyCallback(uuid_notify, notifyCallback);
        }
    }

    /**
     * indicate
     */
    private void handleCharacteristicIndicateCallback(BluetoothGattIndicateCallback indicateCallback,
                                                      String uuid_indicate) {
        if (indicateCallback != null) {
            mGattConnector.addGattIndicateCallback(uuid_indicate, indicateCallback);
        }
    }

    /**
     * write
     */
    private void handleCharacteristicWriteCallback(BluetoothWriteCallback writeCallback,
                                                   String uuid_write) {
        if (writeCallback != null) {
            mGattConnector.addGattWriteCallback(uuid_write, writeCallback);
        }
    }

    /**
     * read
     */
    private void handleCharacteristicReadCallback(BluetoothGattReadCallback readCallback,
                                                  String uuid_read) {
        if (readCallback != null) {
            mGattConnector.addGattReadCallback(uuid_read, readCallback);
        }
    }

    /**
     * rssi
     */
    private void handleRSSIReadCallback(BluetoothGattRssiCallback rssiCallback) {
        if (rssiCallback != null) {
            mGattConnector.addGattRssiCallback(rssiCallback);
        }
    }

    /**
     * set mtu
     */
    private void handleSetMtuCallback(BluetoothGattMtuCallback mtuChangedCallback) {
        if (mtuChangedCallback != null) {
            mGattConnector.addGattMtuCallback(mtuChangedCallback);
        }
    }
}
