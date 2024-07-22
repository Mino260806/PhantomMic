package tn.amin.phantom_mic.log;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

public class Logger {
    public static void d(String message) {
        XposedBridge.log("(PhantomMic) " + message);
    }


    public static void st() {
        Logger.d(Log.getStackTraceString(new Throwable()));
    }
}
