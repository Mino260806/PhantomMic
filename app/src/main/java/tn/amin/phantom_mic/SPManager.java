package tn.amin.phantom_mic;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.lang.ref.WeakReference;

public class SPManager {
    private final SharedPreferences sp;

    SPManager(Context context) {
        sp = context.getSharedPreferences("phantom_mic", Context.MODE_PRIVATE);
    }

    public Uri getUriPath() {
        String path =  sp.getString("recordings_path", null);
        if (path == null) {
            return null;
        }

        return Uri.parse(path);
    }

    public void setUriPath(Uri uri) {
        sp.edit().putString("recordings_path", uri.toString()).apply();
    }
}
