package com.smarthealth.vitalhub.foundation.bluetooth.connect;

import android.bluetooth.BluetoothSocket;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothSppReadCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;

import java.io.IOException;
import java.io.OutputStream;

public class SppEventsCenter extends BluetoothEventsCenter{

    private final SppConnector mSppConnector;
    private final BluetoothSocket mBluetoothSocket;

    SppEventsCenter(SppConnector mSppConnector) {
        this.mSppConnector =mSppConnector;
        this.mBluetoothSocket = mSppConnector.getBluetoothSocket();
    }

    @Override
    public void write(byte[] payload, BluetoothWriteCallback bluetoothWriteCallback) {
        try {
            if(mBluetoothSocket==null) {
                if (bluetoothWriteCallback != null) {
                    bluetoothWriteCallback.onWriteFailure(new Exception("this device not connected!"));
                }
                return;
            }
            final OutputStream os = mBluetoothSocket.getOutputStream();
            if (os != null) {
                os.write(payload);
            }
            if (bluetoothWriteCallback != null) {
                bluetoothWriteCallback.onWriteSuccess(1, 1, payload);
            }
        } catch (IOException e) {
            if (bluetoothWriteCallback != null) {
                bluetoothWriteCallback.onWriteFailure(e);
            }
        }
    }

    public void read(BluetoothSppReadCallback sppReadCallback){
        if(mBluetoothSocket==null) {
            if (sppReadCallback != null) {
                sppReadCallback.onReadFailure(new Exception("this device not connected!"));
            }
            return;
        }
        mSppConnector.addSppReadCallback(sppReadCallback);
    }

}
