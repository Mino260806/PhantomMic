package tn.amin.phantom_mic;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import tn.amin.phantom_mic.audio.AudioMaster;
import tn.amin.phantom_mic.hook.ActivityResultWrapper;
import tn.amin.phantom_mic.log.Logger;

public class PhantomManager {
    private static final String DEFAULT_RECORDINGS_PATH = "Recordings";

    private static final String KEY_INTENT_FILE = "tn.amin.phantom_mic.AUDIO_FILE";

    private static final String FILE_CONFIG = "phantom.txt";

    private static final int REQUEST_CODE = 2608;

    private Uri mUriPath;
    private ParcelFileDescriptor tobeClosed = null;

    private final WeakReference<Context> mContext;

    private final AudioMaster mAudioMaster;
    private final SPManager mSPManager;
    private final FileManager mFileManager;
    private boolean mNeedPrepare = true;

    public PhantomManager(Context context, boolean isNativeHook) {
        Logger.d("Init phantom manager");

        mContext = new WeakReference<>(context);

        mAudioMaster = new AudioMaster();
        mSPManager = new SPManager(context);
        mFileManager = new FileManager(context);

        if (isNativeHook) {
            nativeHook();
        }
    }

    public void interceptIntent(Intent intent) {
//        if (intent.getExtras() != null && intent.getExtras().containsKey(KEY_INTENT_FILE)) {
//            mFileName = intent.getExtras().getString(KEY_INTENT_FILE);
//            intent.getExtras().remove(KEY_INTENT_FILE);
//        }
    }

    public void forceUriPath() {
        ensureHasUriPath();
    }

    public void prepare(Activity activity) {
        if (mUriPath != null) {
            return;
        }

        mNeedPrepare = false;
        if (mSPManager.getUriPath() == null) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDefaultUriPath());
            }

            ActivityResultWrapper arWrapper = new ActivityResultWrapper(activity, REQUEST_CODE);
            arWrapper.start(intent, (resultCode, resultData) -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (resultData != null && resultData.getData() != null) {
                        Uri uri = resultData.getData();
                        // Perform operations on the document using its URI.
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        // Check for the freshest data.
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);

                        mSPManager.setUriPath(uri);
                        mUriPath = uri;

                        Logger.d("Saved uri " + mUriPath);
                    }
                }
            });
        }
        else {
            mUriPath = mSPManager.getUriPath();
        }
        Logger.d("PhantomManager.prepare done");
    }

    public Uri getDefaultUriPath() {
        File defaultPath = new File(Environment.getExternalStorageDirectory(), DEFAULT_RECORDINGS_PATH);
        return Uri.fromFile(defaultPath);
    }

    public void updateAudioFormat(int sampleRate, int channelMask, int encoding) {
        mAudioMaster.setFormat(sampleRate, channelMask, encoding);
        Logger.d("Target: " + sampleRate + "Hz, encoding " + encoding + ", channel count " + mAudioMaster.getFormat().getChannelCount());
    }

    public void load() {
        ensureHasUriPath();

        String fileName = mFileManager.readLine(mUriPath, FILE_CONFIG);
        if (fileName == null || fileName.trim().isEmpty()) {
            Logger.d("No audio file specified");
            return;
        }

        FileDescriptor fd = mFileManager.openAudioWithName(mUriPath, fileName.trim());

        if (fd == null) {
            Toast.makeText(mContext.get(), "Could not open file", Toast.LENGTH_SHORT).show();
            return;
        }

        mAudioMaster.load(fd);

        Logger.d("Audio file loaded");
    }

    private void ensureHasUriPath() {
        if (mUriPath == null) {
            mUriPath = Uri.fromFile(new File(mContext.get().getExternalFilesDir(null), DEFAULT_RECORDINGS_PATH));
            Toast.makeText(mContext.get(), "[E] Defaulting to " + mUriPath.getPath(), Toast.LENGTH_SHORT).show();
        }
    }

    public void unload() {
        mAudioMaster.unload();
        mFileManager.close();
        Logger.d("Done unloading data");
    }

    private ContentResolver getContentResolver() {
        return mContext.get().getContentResolver();
    }

    public boolean needPrepare() {
        return mNeedPrepare;
    }

    private native void nativeHook();
}
