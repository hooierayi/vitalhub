package com.smarthealth.vitalhub.foundation.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattIndicateCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattMtuCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattNotifyCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattReadCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothGattRssiCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.BluetoothConnector;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.GattConnector;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.GattEventsCenter;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.MultipleBluetoothController;
import com.smarthealth.vitalhub.foundation.bluetooth.provider.BluetoothProvider;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressLint("MissingPermission")
public class BluetoothGattDevice extends BluetoothKitDevice{

    private byte[] mScanRecord;
    private int mRssi;
    private long mTimestampNanos;

    public BluetoothGattDevice(BluetoothDevice device) {
        super(device);
    }

    public BluetoothGattDevice(
            BluetoothDevice bluetoothDevice,
            byte[] mScanRecord,
            int mRssi,
            long mTimestampNanos
    ) {
        super(bluetoothDevice);
        this.mScanRecord = mScanRecord;
        this.mRssi = mRssi;
        this.mTimestampNanos = mTimestampNanos;
    }

    public byte[] getScanRecord() {
        return mScanRecord;
    }

    public int getRssi() {
        return mRssi;
    }

    public long getTimestampNanos() {
        return mTimestampNanos;
    }

    @Override
    public int getConnectState() {
        final BluetoothManager bluetoothManager= BluetoothProvider.getInstance()
                .getBluetoothManager();
        return bluetoothManager.getConnectionState(bluetoothDevice, BluetoothProfile.GATT);
    }

    public void write(
            UUID service,
            UUID writeCharacteristic,
            byte[] payload,
            BluetoothWriteCallback callback
    ){
        this.write(service, writeCharacteristic, payload,
                true,true,0,
                callback
        );
    }

    public void write(
            UUID service,
            UUID writeCharacteristic,
            byte[] payload,
            boolean spilt,
            boolean sendNextWhenLastSuccess,
            long intervalBetweenTwoPackage,
            BluetoothWriteCallback callback
    ){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
            final int spiltWriteSize=BluetoothKitReal.getSpiltWriteSize();
            if(spilt&&payload.length>spiltWriteSize) {
                new SplitWriter().splitWriteGatt(bluetoothConnector,service.toString(),writeCharacteristic.toString(),payload,
                        sendNextWhenLastSuccess,intervalBetweenTwoPackage,callback);
            }else {
                GattEventsCenter gattEventsCenter=(GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
                gattEventsCenter.withUUID(service,writeCharacteristic)
                        .write(payload,callback);
            }
        }
    }

    public void read(
            UUID service,
            UUID readCharacteristic,
            BluetoothGattReadCallback callback
    ){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
            GattEventsCenter gattEventsCenter=(GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
            gattEventsCenter.withUUID(service,readCharacteristic)
                    .readCharacteristic(callback);
        }
    }

    public void enableNotification(
            UUID service,
            UUID notifyCharacteristic,
            boolean useCharacteristicDescriptor,
            BluetoothGattNotifyCallback callback
    ){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
            GattEventsCenter gattEventsCenter=(GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
            gattEventsCenter.withUUID(service,notifyCharacteristic)
                    .enableCharacteristicNotify(callback,useCharacteristicDescriptor);
        }
    }

    public void enableIndication(
            UUID service,
            UUID indicateCharacteristic,
            boolean useCharacteristicDescriptor,
            BluetoothGattIndicateCallback callback
    ){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
            GattEventsCenter gattEventsCenter=(GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
            gattEventsCenter.withUUID(service,indicateCharacteristic)
                    .enableCharacteristicIndicate(callback,useCharacteristicDescriptor);
        }
    }

    public void requestMtu(int mtu, BluetoothGattMtuCallback callback){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
            GattEventsCenter gattEventsCenter=(GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
            gattEventsCenter.setMtu(mtu, callback);
        }
    }

    public void readRssi(BluetoothGattRssiCallback callback){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
            GattEventsCenter gattEventsCenter=(GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
            gattEventsCenter.readRemoteRssi(callback);
        }
    }

    public boolean requestConnectionPriority(int connectionPriority) {
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
            GattEventsCenter gattEventsCenter=(GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
            return gattEventsCenter.requestConnectionPriority(connectionPriority);
        }
        return false;
    }

    public BluetoothGatt getGatt(){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof GattConnector) {
           return ((GattConnector) bluetoothConnector).getBluetoothGatt();
        }
        return null;
    }

    public @Nullable List<BluetoothGattService> getGattServices(){
        final BluetoothGatt bluetoothGatt=getGatt();
        if(bluetoothGatt==null) {
            return null;
        }
        return bluetoothGatt.getServices();
    }

    public List<BluetoothGattCharacteristic> getGattCharacteristics(BluetoothGattService service){
        return service.getCharacteristics();
    }

    @Override
    @NonNull
    public String toString() {
        return "BluetoothGattDevice{" +
                "mScanRecord=" + Arrays.toString(mScanRecord) +
                ", mRssi=" + mRssi +
                ", mTimestampNanos=" + mTimestampNanos +
                ", bluetoothDevice=" + bluetoothDevice.toString() +
                '}';
    }
}
