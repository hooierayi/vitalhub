package com.smarthealth.vitalhub.foundation.bluetooth.connect;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKit;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitEnv;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitReal;
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothSppDevice;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothConnectCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.callback.BluetoothSppReadCallback;
import com.smarthealth.vitalhub.foundation.bluetooth.constants.BluetoothConstants;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;

@SuppressLint("MissingPermission")
public class SppConnector extends BluetoothConnector implements Runnable{

    private final MainHandler mainHandler=new MainHandler(Looper.getMainLooper());
    private final ExecutorService mService= Executors.newFixedThreadPool(1);

    private BluetoothSocket mBluetoothSocket;

    private BluetoothSppReadCallback sppReadCallback;
    private final byte[] buffer = new byte[1024];

    public SppConnector(BluetoothKitDevice device) {
        super(device);
    }

    @Override
    public BluetoothEventsCenter newBluetoothEventsCenter() {
        return new SppEventsCenter(this);
    }

    public BluetoothSocket getBluetoothSocket(){
        return mBluetoothSocket;
    }

    public void addSppReadCallback(BluetoothSppReadCallback sppReadCallback) {
        this.sppReadCallback = sppReadCallback;
    }

    public void removeSppReadCallback() {
        this.sppReadCallback = null;
    }

    @Override
    public void run() {
        while (true) {
            try {
                if(mBluetoothSocket==null) {
                    return;
                }
                final InputStream inputStream = mBluetoothSocket.getInputStream();
                int readable = inputStream.read(buffer);
                byte[] payload = Arrays.copyOf(buffer, readable);
                if (readable != -1) {
                    if (sppReadCallback != null) {
                        sppReadCallback.onPayloadChanged(payload);
                    }
                }
            } catch (IOException e) {
                Message message=mainHandler.obtainMessage();
                message.what = BluetoothConstants.MSG_DISCONNECTED;
                SppConnector.SppConnectStateParameter parameter=new SppConnector.SppConnectStateParameter(
                        new Exception("stream intercept," + e.getMessage()));
                parameter.setActive(isActiveDisconnected);
                message.obj=parameter;
                mainHandler.sendMessageDelayed(message,BluetoothKitReal.getDisconnectCallbackDelay());
                break;

            }
        }
    }

    private final class MainHandler extends Handler {

        public MainHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BluetoothConstants.MSG_SOCKET_CONNECT:{
                    Message message = mainHandler.obtainMessage();
                    message.what = BluetoothConstants.MSG_CONNECT_TIMEOUT;
                    final long timeout = BluetoothKitReal.getConnectTimeout();
                    mainHandler.sendMessageDelayed(message, timeout);

                    mService.execute(SppConnector.this::realConnect);
                }
                break;

                case BluetoothConstants.MSG_CONNECT_FAIL: {
                    mainHandler.removeMessages(BluetoothConstants.MSG_CONNECT_TIMEOUT);

                    closeSocket();
                    unregisterPairReceiver();

                    //todo reconnect
                    mConnectionState = ConnectionState.FAILURE;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(SppConnector.this);

                    SppConnectStateParameter parameter = (SppConnectStateParameter) msg.obj;
                    if (mConnectCallback != null) {
                        mConnectCallback.onConnectFailure(device, parameter.exception);
                    }
                }
                break;

                case BluetoothConstants.MSG_DISCONNECTED: {
                    mConnectionState = ConnectionState.DISCONNECTED;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnectedPool(SppConnector.this);

                    closeSocket();
                    removeSppReadCallback();
                    unregisterPairReceiver();
                    shutdownService();
                    mainHandler.removeCallbacksAndMessages(null);
                    mBluetoothSocket=null;

                    SppConnectStateParameter parameter = (SppConnectStateParameter) msg.obj;
                    boolean isActive = parameter.isActive();
                    if (mConnectCallback != null)
                        mConnectCallback.onDisConnected(device, isActive);
                }
                break;

                case BluetoothConstants.MSG_CONNECT_TIMEOUT: {
                    closeSocket();
                    unregisterPairReceiver();
                    shutdownService();

                    mConnectionState = ConnectionState.FAILURE;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(SppConnector.this);

                    if (mConnectCallback != null)
                        mConnectCallback.onConnectFailure(device, new Exception("connect timeout"));
                }
                break;

                case BluetoothConstants.MSG_PAIR: {
                    registerPairReceiver();
                    try {
                        boolean result=createBond(device.getBluetoothDevice());
                        if(!result) {
                            Message message=mainHandler.obtainMessage();
                            message.what=BluetoothConstants.MSG_CONNECT_FAIL;
                            message.obj=new SppConnectStateParameter(new Exception("bond false on immediate error"));
                            mainHandler.sendMessage(message);
                        }
                    } catch (Exception e) {
                        Message message=mainHandler.obtainMessage();
                        message.what=BluetoothConstants.MSG_CONNECT_FAIL;
                        message.obj=new SppConnectStateParameter(new Exception("pairing,"+e.getMessage(),e));
                        mainHandler.sendMessage(message);
                    }
                }
                break;
                case BluetoothConstants.MSG_SOCKET_CONNECT_SUCCESS:{
                    mainHandler.removeMessages(BluetoothConstants.MSG_CONNECT_TIMEOUT);

                    mConnectionState = ConnectionState.CONNECTED;
                    isActiveDisconnected = false;
                    BluetoothKit.getInstance().getMultipleBluetoothController().removeConnector(SppConnector.this);
                    BluetoothKit.getInstance().getMultipleBluetoothController().putConnectedPool(SppConnector.this);

                    if (mConnectCallback != null)
                        mConnectCallback.onConnectSuccess(device);

                    mService.execute(SppConnector.this);
                }
                break;

                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    @Override
    public synchronized void connect(BluetoothConnectCallback bluetoothConnectCallback) {
        super.connect(bluetoothConnectCallback);
        final BluetoothDevice bluetoothDevice=device.getBluetoothDevice();
        if(isUnpaired(bluetoothDevice)) {
            Message message=mainHandler.obtainMessage();
            message.what=BluetoothConstants.MSG_PAIR;
            mainHandler.sendMessage(message);
        }else {
            Message message=mainHandler.obtainMessage();
            message.what=BluetoothConstants.MSG_SOCKET_CONNECT;
            mainHandler.sendMessage(message);
        }

        if (mConnectCallback != null) {
            mConnectCallback.onConnectStart();
        }
    }

    @Override
    public synchronized void disconnect() {
        super.disconnect();
        Log.d("BluetoothKit", "disconnect: ");
        removeSppReadCallback();
        closeSocket();
        unregisterPairReceiver();
    }

    @Override
    public synchronized void destroy() {
        super.destroy();
        closeSocket();
        removeConnectCallback();
        removeSppReadCallback();
        shutdownService();
        mainHandler.removeCallbacksAndMessages(null);
        mBluetoothSocket=null;
    }

    private void closeSocket(){
        if(mBluetoothSocket!=null) {
            try {
                mBluetoothSocket.close();
            } catch (IOException ignored) {
                Log.d("BluetoothKit", "closeSocket: ");
            }
        }
    }

    private void shutdownService(){
        if(mService!=null) {
            mService.shutdown();
        }
    }

    private void realConnect(){
        final BluetoothDevice bluetoothDevice=device.getBluetoothDevice();
        final UUID uuid=((BluetoothSppDevice)device).getSppUuid();
        mBluetoothSocket=createSocket(bluetoothDevice,uuid);
        try {
            mBluetoothSocket.connect();
            if(mBluetoothSocket.isConnected()) {
                Message message=mainHandler.obtainMessage();
                message.what=BluetoothConstants.MSG_SOCKET_CONNECT_SUCCESS;
                mainHandler.sendMessage(message);
            }
        } catch (IOException e) {
            Message message=mainHandler.obtainMessage();
            message.what=BluetoothConstants.MSG_CONNECT_FAIL;
            message.obj=new SppConnectStateParameter(new Exception("real connect,"+e.getMessage(),e));
            mainHandler.sendMessage(message);
        }
    }

    private BluetoothSocket createSocket(BluetoothDevice mBTDevice, UUID uuid) {
        BluetoothSocket socket = null;
        try {
            socket = mBTDevice.createInsecureRfcommSocketToServiceRecord(uuid);
        } catch (IOException e) {
            try {
                Method method = mBTDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                socket = (BluetoothSocket) method.invoke(mBTDevice, Integer.valueOf(1));
                return socket;
            } catch (Exception e1) {
                Message message=mainHandler.obtainMessage();
                message.what=BluetoothConstants.MSG_CONNECT_FAIL;
                message.obj=new SppConnectStateParameter(new Exception("createSocket,"+e1.getMessage(),e1));
                mainHandler.sendMessage(message);
            }
        }
        return socket;
    }

    private boolean isUnpaired(BluetoothDevice bluetoothDevice){
        if(bluetoothDevice!=null) {
            return bluetoothDevice.getBondState()==BluetoothDevice.BOND_NONE;
        }
        return false;
    }

    private PairedReceiver pairedReceiver;

    private void registerPairReceiver(){
        final Context context= BluetoothKitEnv.requireApp();
        unregisterPairReceiver();
        pairedReceiver=new PairedReceiver();
        IntentFilter filters = new IntentFilter();
        filters.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filters.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        context.registerReceiver(pairedReceiver,filters);
    }

    private void unregisterPairReceiver(){
        final Context context=BluetoothKitEnv.requireApp();
        if(pairedReceiver!=null) {
            context.unregisterReceiver(pairedReceiver);
            pairedReceiver=null;
        }
    }

    private boolean createBond(BluetoothDevice btDevice)
            throws Exception {
        Method createBondMethod = btDevice.getClass().getMethod("createBond");
        return (Boolean) createBondMethod.invoke(btDevice);
    }

    private final class PairedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action=intent.getAction();
            final BluetoothDevice bluetoothDevice=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final String address=bluetoothDevice.getAddress();
            final String pairedAddress=device.getBluetoothDevice().getAddress();
            if(address.equals(pairedAddress)) {
                switch (action){
                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:{
                        final int bondState=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR);

                        if(bondState==BluetoothDevice.BOND_BONDED) {
                            Message message=mainHandler.obtainMessage();
                            message.what=BluetoothConstants.MSG_SOCKET_CONNECT;
                            mainHandler.sendMessage(message);

                        }else if(bondState==BluetoothDevice.BOND_NONE) {
                            Message message=mainHandler.obtainMessage();
                            message.what=BluetoothConstants.MSG_CONNECT_FAIL;
                            message.obj=new SppConnectStateParameter(new Exception("pair exception,maybe timeout or cancel pair"));
                            mainHandler.sendMessage(message);

                        }
                        break;
                    }

                    case BluetoothDevice.ACTION_PAIRING_REQUEST:
                        int type = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT,
                                BluetoothDevice.ERROR);
                        break;
                }
            }
        }
    }

    public static final class SppConnectStateParameter {
        private Exception exception;
        private boolean isActive;

        public SppConnectStateParameter(Exception exception) {
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }

        public boolean isActive() {
            return isActive;
        }

        public void setActive(boolean active) {
            isActive = active;
        }
    }
}
