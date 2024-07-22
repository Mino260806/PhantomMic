package tn.amin.phantom_mic.hook;

import android.content.Intent;

public interface ActivityResultHook {
    void onResult(int resultCode, Intent resultData);
}
