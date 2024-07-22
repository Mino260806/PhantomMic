package tn.amin.phantom_mic.hook;

import android.app.Activity;
import android.content.Intent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class ActivityResultWrapper {
    private final Activity mActivity;
    private final int mRequestCode;

    public ActivityResultWrapper(Activity activity, int requestCode) {
        this.mActivity = activity;
        this.mRequestCode = requestCode;
    }

    public void start(Intent intent, ActivityResultHook hook) {
        XposedHelpers.findAndHookMethod(Activity.class, "onActivityResult",
                int.class, int.class, Intent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        int requestCode = (int) param.args[0];
                        int resultCode = (int) param.args[1];
                        Intent resultData = (Intent) param.args[2];

                        if (requestCode == mRequestCode) {
                            hook.onResult(resultCode, resultData);
                            param.setResult(null);
                        }
                    }
                });

        mActivity.startActivityForResult(intent, mRequestCode);
    }
}
