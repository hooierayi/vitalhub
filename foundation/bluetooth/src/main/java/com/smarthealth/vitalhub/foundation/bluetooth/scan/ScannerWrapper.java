package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.provider.BluetoothProvider;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothScanCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;

public class ScannerWrapper extends BluetoothScanCallback {

    private static final int MSG_SCAN_SCHEDULE = 0x1;
    private static final int MSG_SCAN_RESULT = 0x2;

    private final BluetoothScanRulesConfig mScanRulesConfig;
    private BluetoothScanCallback mCallback;

    private final List<Scanner> mScanners;
    private Scanner mCurrentScanner;

    private final HandlerThread mHandlerThread;
    private final ScanHandler mHandler;

    private final BluetoothKitScanner mBluetoothKitScanner;

    public ScannerWrapper(
            BluetoothKitScanner bluetoothKitScanner,
            BluetoothKitScanner.Type type,
            BluetoothScanRulesConfig scanRulesConfig,
            BluetoothScanCallback callback
    ) {
        this.mBluetoothKitScanner = bluetoothKitScanner;
        this.mScanRulesConfig = scanRulesConfig;
        this.mCallback = callback;
        this.mScanners = new ArrayList<>(2);
        switch (type) {
            case LE:
                mScanners.add(new Scanner(mScanRulesConfig, BluetoothKitScanner.Type.LE));
                break;
            case CLASSIC:
                mScanners.add(new Scanner(mScanRulesConfig, BluetoothKitScanner.Type.CLASSIC));
                break;
            case ALTERNATE:
                mScanners.add(new Scanner(mScanRulesConfig, BluetoothKitScanner.Type.CLASSIC));
                mScanners.add(new Scanner(mScanRulesConfig, BluetoothKitScanner.Type.LE));
                break;
        }
        mHandlerThread = new HandlerThread("BluetoothKitScanner");
        mHandlerThread.start();
        mHandler = new ScanHandler(mHandlerThread.getLooper());
    }

    private final class ScanHandler extends Handler {

        public ScanHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_SCAN_SCHEDULE:
                    scheduleNext();
                    break;
                case MSG_SCAN_RESULT: {
                    BluetoothKitDevice bluetoothKitDevice = (BluetoothKitDevice) msg.obj;
                    if (mCallback != null) {
                        mCallback.onScanResult(bluetoothKitDevice);
                    }
                }
                break;
            }
            super.handleMessage(msg);
        }
    }

    public void execute() {
        if (mCallback != null) {
            mCallback.onScanStart();
        }

        final boolean readCache = mScanRulesConfig.isReadCache();
        if (readCache) {
            notifyBondedDevices();
        }

        mHandler.sendEmptyMessage(MSG_SCAN_SCHEDULE);
    }

    public void shutdown() {
        if (mCurrentScanner != null) {
            mCurrentScanner.stopScan();
        }
        if (mCallback != null) {
            mCallback.onScanCancel();
        }

        release();
    }

    private void scheduleNext() {
        if (mScanners.size() > 0) {
            mCurrentScanner = mScanners.remove(0);
            mCurrentScanner.startScan(this);
        } else {
            mBluetoothKitScanner.notifyScanComplete();

            if (mCallback != null) {
                mCallback.onScanComplete();
            }
            release();
        }
    }

    private void release() {
        mScanners.clear();
        mCallback = null;
        mCurrentScanner = null;

        mHandlerThread.quit();
        mHandler.removeCallbacksAndMessages(null);
    }

    private void notifyBondedDevices() {
        final BluetoothProvider bluetoothProvider = BluetoothProvider.getInstance();
        Set<BluetoothDevice> bluetoothDevices = bluetoothProvider.getBondedDevices();
        for (BluetoothDevice bluetoothDevice : bluetoothDevices) {
            final BluetoothKitDevice bluetoothKitDevice = bluetoothProvider.convertBluetoothKitDevice(bluetoothDevice);
            notifyFoundedDevice(bluetoothKitDevice);
        }
    }

    private void notifyFoundedDevice(BluetoothKitDevice bluetoothKitDevice) {
        mHandler.obtainMessage(MSG_SCAN_RESULT, bluetoothKitDevice).sendToTarget();
    }

    @Override
    public void onScanResult(BluetoothKitDevice bluetoothKitDevice) {
        final ScanFilter filter = mScanRulesConfig.getScanFilter();
        if (filter != null && filter.accept(bluetoothKitDevice)) {
            notifyFoundedDevice(bluetoothKitDevice);
        }
    }

    @Override
    public void onScanComplete() {
        mHandler.sendEmptyMessage(MSG_SCAN_SCHEDULE);
    }
}
