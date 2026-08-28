package com.smarthealth.vitalhub.foundation.bluetooth.scan;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.text.TextUtils;

import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SuppressLint("MissingPermission")
public class BluetoothScanRulesConfig {

    private final long scanTimeout;
    private final ScanFilter scanFilter;
    private final boolean readCache;
    private final UUID[] gattServiceUuids;

    public BluetoothScanRulesConfig() {
        this(new Builder());
    }

    private BluetoothScanRulesConfig(Builder builder){
        this.scanTimeout=builder.scanTimeout;
        this.scanFilter=builder.scanFilter;
        this.readCache=builder.readCache;
        this.gattServiceUuids=builder.gattServiceUuids;
    }

    public long getScanTimeout() {
        return scanTimeout;
    }

    public ScanFilter getScanFilter() {
        return scanFilter;
    }

    public boolean isReadCache() {
        return readCache;
    }

    public UUID[] getGattServiceUuids() {
        return gattServiceUuids;
    }

    public static final class Builder{
        private long scanTimeout;
        private ScanFilter scanFilter;
        private boolean readCache;
        private UUID[] gattServiceUuids;

        public Builder() {
            this.scanTimeout = 10_000L;
            this.scanFilter = bluetoothKitDevice -> {
                final BluetoothDevice bluetoothDevice=bluetoothKitDevice.getBluetoothDevice();
                final String name = bluetoothDevice.getName();
                return !TextUtils.isEmpty(name);
            };
            this.readCache = false;
            this.gattServiceUuids = null;
        }

        public Builder setTimeout(long timeout, TimeUnit timeUnit){
            this.scanTimeout=timeUnit.toMillis(timeout);
            return this;
        }

        public Builder setFilter(ScanFilter filter){
            this.scanFilter = filter;
            return this;
        }

        public Builder setReadCache(boolean read){
            this.readCache=read;
            return this;
        }

        public Builder setFilterGattServiceUuids(UUID[] serviceUuids){
            this.gattServiceUuids=serviceUuids;
            return this;
        }

        public BluetoothScanRulesConfig build(){
            return new BluetoothScanRulesConfig(this);
        }

    }
}
