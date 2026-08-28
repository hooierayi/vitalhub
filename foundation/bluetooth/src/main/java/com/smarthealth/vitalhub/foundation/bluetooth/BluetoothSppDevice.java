package com.smarthealth.vitalhub.foundation.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothSppReadCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.BluetoothConnector;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.MultipleBluetoothController;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.SppConnector;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.SppEventsCenter;

import java.util.UUID;

import androidx.annotation.NonNull;

public class BluetoothSppDevice extends BluetoothKitDevice{

    public static final UUID SPP_UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private int mRssi;

    public BluetoothSppDevice(BluetoothDevice device) {
        super(device);
    }

    public BluetoothSppDevice(BluetoothDevice bluetoothDevice, int mRssi) {
        super(bluetoothDevice);
        this.mRssi = mRssi;
    }

    public UUID getSppUuid() {
        return SPP_UUID;
    }

    public void write(byte[] payload, BluetoothWriteCallback callback) {
        this.write(payload, true,true,0, callback);
    }

    public void write(
            byte[] payload,
            boolean spilt,
            boolean sendNextWhenLastSuccess,
            long intervalBetweenTwoPackage,
            BluetoothWriteCallback callback
    ){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof SppConnector) {
            final int spiltWriteSize=BluetoothKitReal.getSpiltWriteSize();
            if(spilt&&payload.length>spiltWriteSize) {
                new SplitWriter().splitWriteSpp(bluetoothConnector,payload, sendNextWhenLastSuccess,
                        intervalBetweenTwoPackage,callback);
            }else {
                SppEventsCenter sppEventsCenter=(SppEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
                sppEventsCenter.write(payload,callback);
            }
        }
    }

    public void read(BluetoothSppReadCallback callback){
        final MultipleBluetoothController multipleBluetoothController=BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        final BluetoothConnector bluetoothConnector=multipleBluetoothController.getBluetoothConnector(this);
        if(bluetoothConnector instanceof SppConnector) {
            SppEventsCenter sppEventsCenter=(SppEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
            sppEventsCenter.read(callback);
        }
    }

    @Override
    public int getConnectState() {
        final MultipleBluetoothController multipleBluetoothController = BluetoothKit.getInstance()
                .getMultipleBluetoothController();
        return multipleBluetoothController.isContainDevice(this) ?
                BluetoothProfile.STATE_CONNECTED : BluetoothProfile.STATE_DISCONNECTED;
    }

    @Override
    @NonNull
    public String toString() {
        return "BluetoothSppDevice{" +
                "sppUUID=" + SPP_UUID.toString() +
                ", mRssi=" + mRssi +
                ", bluetoothDevice=" + bluetoothDevice.toString() +
                '}';
    }
}
