package com.smarthealth.vitalhub.foundation.bluetooth;

import com.smarthealth.vitalhub.foundation.bluetooth.scan.BluetoothScanRulesConfig;

public class BluetoothKitReal {

    private static long connectTimeout = 10_000L;
    private static long delayDisconnectCallbackMills = 0L;
    private static int spiltWriteSize = 20;

    private static BluetoothScanRulesConfig scanRulesConfig = new BluetoothScanRulesConfig();

    public static BluetoothScanRulesConfig getScanRulesConfig() {
        return scanRulesConfig;
    }

    public static long getConnectTimeout() {
        return connectTimeout;
    }

    public static int getSpiltWriteSize() {
        return spiltWriteSize;
    }

    public static long getDisconnectCallbackDelay() {
        return delayDisconnectCallbackMills;
    }

    public static void setScanRulesConfig(BluetoothScanRulesConfig scanRulesConfig) {
        BluetoothKitReal.scanRulesConfig = scanRulesConfig;
    }

    public static void setConnectTimeout(long connectTimeout) {
        BluetoothKitReal.connectTimeout = connectTimeout;
    }

    public static void setSpiltWriteSize(int spiltWriteSize) {
        BluetoothKitReal.spiltWriteSize = spiltWriteSize;
    }

    public static void delayDisconnectCallback(long delayDisconnectCallbackMills) {
        BluetoothKitReal.delayDisconnectCallbackMills = delayDisconnectCallbackMills;
    }

    public static void init(){

    }
}
