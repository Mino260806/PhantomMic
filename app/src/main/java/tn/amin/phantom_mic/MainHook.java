package tn.amin.phantom_mic;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.PersistableBundle;

import java.nio.ByteBuffer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import tn.amin.phantom_mic.log.Logger;

public class MainHook implements IXposedHookLoadPackage {
    private PhantomManager phantomManager = null;

    private boolean needHook = true;

    private String packageName;

    private int accBytes = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (needHook) {
            needHook = false;

            packageName = lpparam.packageName;
            System.loadLibrary("xposedlab");

            Logger.d("Beginning hook");
            doHook(lpparam);
            Logger.d("Successful hook");
        }
    }

    private void doHook(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookConstructor("android.media.AudioRecord", lpparam.classLoader,
                int.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                accBytes = 0;

                int audioSource = (int) param.args[0];
                int sampleRate = (int) param.args[1];
                int channelConfig = (int) param.args[2];
                int encoding = (int) param.args[3];
                int bufferSize = (int) param.args[4];
                phantomManager.updateAudioFormat(sampleRate, channelConfig, encoding);
                phantomManager.load();
            }
        });

        XposedBridge.hookAllMethods(AudioRecord.class, "read", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] instanceof byte[]) {
                    if (param.args.length == 4) {
                        byte[] audioData = (byte[]) param.args[0];
                        int offsetInBytes = (int) param.args[1];
                        int sizeInBytes = (int) param.args[2];
                        int readMode = (int) param.args[3];

                        int bytesRead = (int) XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        param.setResult(bytesRead);

                        Logger.d("AudioRecord.read byte[] (" + bytesRead + ")");
                        accBytes += bytesRead;
                        if (phantomManager.onDataRead(audioData, 0, bytesRead)) {
                            Logger.d("Overwritten data at offset " + offsetInBytes + " with size " + bytesRead);
                        }
                    }
                }

                if (param.args[0] instanceof short[]) {
                    if (param.args.length == 4) {
                        short[] audioData = (short[]) param.args[0];
                        int offsetInShorts = (int) param.args[1];
                        int sizeInShorts = (int) param.args[2];
                        int readMode = (int) param.args[3];

                        int bytesRead = (int) XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        param.setResult(bytesRead);

                        Logger.d("AudioRecord.read short[] (" + bytesRead + ")");
                        accBytes += bytesRead;
                        if (phantomManager.onDataRead(audioData, 0, bytesRead)) {
                            Logger.d("Overwritten data at offset " + offsetInShorts + " with size " + bytesRead);
                        }
                    }
                }

                else if (param.args[0] instanceof float[]) {
                    if (param.args.length == 4) {
                        float[] audioData = (float[]) param.args[0];
                        int offsetInFloats = (int) param.args[1];
                        int sizeInFloats = (int) param.args[2];
                        int readMode = (int) param.args[3];

                        int bytesRead = (int) XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);

                        param.setResult(bytesRead);

                        Logger.d("AudioRecord.read float[] (" + bytesRead + ")");
                        accBytes += bytesRead;
                        if (phantomManager.onDataRead(audioData, 0, bytesRead)) {
                            Logger.d("Overwritten data at offset " + offsetInFloats + " with size " + bytesRead);
                        }
                    }
                }

                else if (param.args[0] instanceof ByteBuffer) {
                    if (param.args.length == 3) {
                        AudioRecord audioRecord = (AudioRecord) param.thisObject;
                        ByteBuffer audioData = (ByteBuffer) param.args[0];
                        int sizeInBytes = (int) param.args[1];
                        int bytesRead = (int) XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);

                        Logger.d("[" + accBytes + "] AudioRecord.read ByteBuffer (" + bytesRead + ")");
                        accBytes += bytesRead;
                        param.setResult(bytesRead);
                        if (phantomManager.onDataRead(audioData.array(), 0, bytesRead)) {
                            Logger.d("Overwritten data at offset " + 0 + " with size " + bytesRead);
                        }
                    }
                }

                else {
                    Logger.d("Called unsupported read (" + param.args[0].getClass().getSimpleName() + ")");
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.media.MediaRecorder", lpparam.classLoader, "start" , new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Logger.d("MediaRecorder start");
            }
        });

        XposedHelpers.findAndHookMethod("android.media.AudioRecord", lpparam.classLoader, "startRecording", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Logger.d("AudioRecord start");
            }
        });

        XposedHelpers.findAndHookMethod("android.media.AudioRecord", lpparam.classLoader, "stop", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Logger.d("AudioRecord stop");
                phantomManager.unload();
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "performCreate", Bundle.class, PersistableBundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                phantomManager.interceptIntent(activity.getIntent());
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                onActivityObtained(activity);
            }
        });

        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Application application = (Application) param.args[0];
                if (phantomManager == null) {
                    initPhantomManager(application.getApplicationContext());
                }
            }
        });
    }

    private void onActivityObtained(Activity activity) {
        if (phantomManager == null) {
            initPhantomManager(activity.getApplicationContext());
        }

        if (phantomManager.needPrepare()) {
            phantomManager.prepare(activity);

//            phantomManager.updateAudioFormat(48000, AudioFormat.CHANNEL_IN_MONO, 2);
//            phantomManager.load(null);
        }
    }

    private void initPhantomManager(Context context) {
        phantomManager = new PhantomManager(context, isNativeHook());
    }

    public boolean isNativeHook() {
        return packageName.equals("com.whatsapp");
    }
}
