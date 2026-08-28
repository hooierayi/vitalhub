package com.smarthealth.vitalhub.foundation.bluetooth;

import android.app.Application;
import android.content.Context;

import com.smarthealth.vitalhub.foundation.bluetooth.provider.ContextProvider;

public class BluetoothKitEnv {

    public static Application requireApp() {
        final Context context = requireContext();
        if (context == null) {
            throw new IllegalStateException("BluetoothKit app not set");
        }
        return (Application) context.getApplicationContext();
    }

    private static Context requireContext() {
        return ContextProvider.mContext;
    }
}
