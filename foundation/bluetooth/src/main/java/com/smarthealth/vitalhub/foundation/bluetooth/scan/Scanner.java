package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback;

import androidx.annotation.NonNull;

public class Scanner implements Handler.Callback{

    private static final int MSG_TIMEOUT=0x1;

    private final BluetoothScanRulesConfig config;
    private final BluetoothKitScanner.Type scanType;

    private final Handler mHandler;

    private BluetoothScanner mBluetoothScanner;

    public Scanner(BluetoothScanRulesConfig config, BluetoothKitScanner.Type scanType) {
        this.config = config;
        this.scanType = scanType;
        this.mHandler=new Handler(Looper.myLooper(),this);
    }

    public void startScan(BluetoothScanCallback callback){
        bluetoothScanner().startScan(callback);
        mHandler.sendEmptyMessageDelayed(MSG_TIMEOUT,config.getScanTimeout());
    }

    public void stopScan(){
        mHandler.removeCallbacksAndMessages(null);
        bluetoothScanner().cancelScan();
    }

    private BluetoothScanner bluetoothScanner(){
        if(mBluetoothScanner==null) {
            mBluetoothScanner= BluetoothScanner.newsInstance(scanType);
        }
        return mBluetoothScanner;
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        if(msg.what==MSG_TIMEOUT) {
            bluetoothScanner().stopScan();
        }
        return true;
    }
}
