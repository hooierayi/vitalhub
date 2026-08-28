package com.smarthealth.vitalhub.foundation.bluetooth;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothWriteCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.BluetoothConnector;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.BluetoothEventsCenter;
import com.smarthealth.vitalhub.foundation.bluetooth.connect.GattEventsCenter;

import java.util.LinkedList;
import java.util.Queue;

public class SplitWriter {

    public static final int MSG_SPLIT_WRITE_NEXT = 0x33;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private BluetoothEventsCenter mBluetoothEventsCenter;
    private byte[] mData;
    private int mCount;
    private boolean mSendNextWhenLastSuccess;
    private long mIntervalBetweenTwoPackage;
    private BluetoothWriteCallback mCallback;
    private Queue<byte[]> mDataQueue;
    private int mTotalNum;

    public SplitWriter() {
        mHandlerThread = new HandlerThread("splitWriter");
        mHandlerThread.start();

        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == MSG_SPLIT_WRITE_NEXT) {
                    write();
                }
            }
        };
    }

    public void splitWriteGatt(BluetoothConnector bluetoothConnector,
                               String uuid_service,
                               String uuid_write,
                               byte[] data,
                               boolean sendNextWhenLastSuccess,
                               long intervalBetweenTwoPackage,
                               BluetoothWriteCallback callback) {
        GattEventsCenter gattEventsCenter= (GattEventsCenter) bluetoothConnector.newBluetoothEventsCenter();
        mBluetoothEventsCenter=gattEventsCenter.withUUIDString(uuid_service, uuid_write);
        mData = data;
        mSendNextWhenLastSuccess = sendNextWhenLastSuccess;
        mIntervalBetweenTwoPackage = intervalBetweenTwoPackage;
        mCount = BluetoothKitReal.getSpiltWriteSize();
        mCallback = callback;

        splitWrite();
    }

    public void splitWriteSpp(BluetoothConnector bluetoothConnector,
                           byte[] data,
                           boolean sendNextWhenLastSuccess,
                           long intervalBetweenTwoPackage,
                           BluetoothWriteCallback callback) {
        mBluetoothEventsCenter = bluetoothConnector.newBluetoothEventsCenter();
        mData = data;
        mSendNextWhenLastSuccess = sendNextWhenLastSuccess;
        mIntervalBetweenTwoPackage = intervalBetweenTwoPackage;
        mCount = BluetoothKitReal.getSpiltWriteSize();
        mCallback = callback;

        splitWrite();
    }

    private void splitWrite() {
        if (mData == null) {
            throw new IllegalArgumentException("data is Null!");
        }
        if (mCount < 1) {
            throw new IllegalArgumentException("split count should higher than 0!");
        }
        mDataQueue = splitByte(mData, mCount);
        mTotalNum = mDataQueue.size();
        write();
    }

    private void write() {
        if (mDataQueue.peek() == null) {
            release();
            return;
        }

        byte[] data = mDataQueue.poll();
        mBluetoothEventsCenter.write(data,new BluetoothWriteCallback() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                int position = mTotalNum - mDataQueue.size();
                if (mCallback != null) {
                    mCallback.onWriteSuccess(position, mTotalNum, justWrite);
                }
                if (mSendNextWhenLastSuccess) {
                    Message message = mHandler.obtainMessage(MSG_SPLIT_WRITE_NEXT);
                    mHandler.sendMessageDelayed(message, mIntervalBetweenTwoPackage);
                }
            }

            @Override
            public void onWriteFailure(Exception exception) {
                if (mCallback != null) {
                    mCallback.onWriteFailure(new Exception("exception occur while writing: " + exception.getMessage()));
                }
                if (mSendNextWhenLastSuccess) {
                    Message message = mHandler.obtainMessage(MSG_SPLIT_WRITE_NEXT);
                    mHandler.sendMessageDelayed(message, mIntervalBetweenTwoPackage);
                }
            }
        });
        if (!mSendNextWhenLastSuccess) {
            Message message = mHandler.obtainMessage(MSG_SPLIT_WRITE_NEXT);
            mHandler.sendMessageDelayed(message, mIntervalBetweenTwoPackage);
        }
    }

    private void release() {
        mHandlerThread.quit();
        mHandler.removeCallbacksAndMessages(null);
    }

    private static Queue<byte[]> splitByte(byte[] data, int count) {
//        if (count > 20) {
//            BleLog.w("Be careful: split count beyond 20! Ensure MTU higher than 23!");
//        }
        Queue<byte[]> byteQueue = new LinkedList<>();
        int pkgCount;
        if (data.length % count == 0) {
            pkgCount = data.length / count;
        } else {
            pkgCount = data.length / count + 1;
        }

        if (pkgCount > 0) {
            for (int i = 0; i < pkgCount; i++) {
                byte[] dataPkg;
                int j;
                if (pkgCount == 1 || i == pkgCount - 1) {
                    j = data.length % count == 0 ? count : data.length % count;
                    System.arraycopy(data, i * count, dataPkg = new byte[j], 0, j);
                } else {
                    System.arraycopy(data, i * count, dataPkg = new byte[count], 0, count);
                }
                byteQueue.offer(dataPkg);
            }
        }

        return byteQueue;
    }


}
