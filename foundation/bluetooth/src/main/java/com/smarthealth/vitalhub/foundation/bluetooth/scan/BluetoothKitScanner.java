package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import android.util.Log;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitReal;
import com.smarthealth.vitalhub.foundation.bluetooth.provider.BluetoothProvider;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback;

public class BluetoothKitScanner {

    public static BluetoothKitScanner getInstance() {
        return BluetoothKitScannerHolder._instance;
    }

    private static class BluetoothKitScannerHolder {
        private static final BluetoothKitScanner _instance = new BluetoothKitScanner();
    }

    private final BluetoothScanRulesConfig scanRulesConfig;
    private BluetoothScanState mScanState=BluetoothScanState.STATE_IDLE;

    private ScannerWrapper mWrapper;

    private BluetoothKitScanner() {
        scanRulesConfig= BluetoothKitReal.getScanRulesConfig();
    }

    enum Type{
        CLASSIC,
        LE,
        ALTERNATE
    }

    /**
     * 扫描低功耗设备
     */
    public void startLeScan(BluetoothScanCallback callback){
        startScan(Type.LE,callback);
    }

    /**
     * 经典蓝牙扫描，可搜索到低功耗设备
     */
    public void startDiscovery(BluetoothScanCallback callback){
        startScan(Type.CLASSIC,callback);
    }

    /**
     * 低功耗扫描、经典扫描交替进行
     */
    public void startAlternateScan(BluetoothScanCallback callback){
        startScan(Type.ALTERNATE,callback);
    }

    private void startScan(Type type,BluetoothScanCallback callback){
        if (callback == null) {
            throw new IllegalArgumentException("BluetoothConnectCallback can not be Null!");
        }

        if(mScanState !=BluetoothScanState.STATE_IDLE) {
            Log.w("BluetoothKitScanner","scan action already exists, complete the previous scan action first");
            return;
        }

        ScannerWrapper wrapper=new ScannerWrapper(this,type,scanRulesConfig,callback);
        final BluetoothProvider bluetoothProvider=BluetoothProvider.getInstance();
        if(!bluetoothProvider.isBluetoothEnable()) {
            wrapper.shutdown();
        }else {
            if(mWrapper==null) {
                mWrapper=wrapper;
                mScanState=BluetoothScanState.STATE_SCANNING;
                mWrapper.execute();
            }
        }
    }

    public void stopScan(){
        if(mWrapper!=null) {
            mWrapper.shutdown();
            mWrapper=null;
        }
        mScanState=BluetoothScanState.STATE_IDLE;
    }

    public BluetoothScanState getScanState(){
        return mScanState;
    }

    void notifyScanComplete(){
        mWrapper=null;
        mScanState=BluetoothScanState.STATE_IDLE;
    }
}
