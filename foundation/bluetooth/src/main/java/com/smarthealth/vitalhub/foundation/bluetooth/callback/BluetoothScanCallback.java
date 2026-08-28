package com.smarthealth.vitalhub.foundation.bluetooth.callback;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;

public abstract class BluetoothScanCallback {

    /**
     * 开始搜索
     */
    public void onScanStart(){}

    /**
     * 搜索结果
     */
    public void onScanResult(BluetoothKitDevice bluetoothKitDevice) {}

    /**
     * 搜索结束
     */
    public void onScanComplete() {}

    /**
     * 搜索取消
     */
    public void onScanCancel(){}
}
