package tn.amin.phantom_mic;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;

import tn.amin.phantom_mic.log.Logger;

public class FileManager {
    final WeakReference<Context> mContext;

    private ParcelFileDescriptor tobeClosed = null;

    public FileManager(Context context) {
        mContext = new WeakReference<>(context);
    }

    public FileDescriptor openAudioWithName(Uri parent, String fileName) {
        FileDescriptor fd;
        if ("content".equals(parent.getScheme())) {
            Uri file = findAudioFile(parent, fileName);
            if (file == null) {
                return null;
            }
            fd = openFileDescriptor(file);
        }
        else {
            Uri file = findAudioFileLegacy(parent, fileName);
            if (file == null) {
                return null;
            }
            fd = openFileDescriptorLegacy(file);
        }

        return fd;
    }

    public FileDescriptor open(Uri uri) {
        FileDescriptor fd;
        if ("content".equals(uri.getScheme())) {
            fd = openFileDescriptor(uri);
        }
        else {
            fd = openFileDescriptorLegacy(uri);
        }

        return fd;
    }

    private Uri findAudioFile(Uri uri, String fileName) {
        // Create a DocumentFile from the tree URI
        DocumentFile directory = DocumentFile.fromTreeUri(mContext.get(), uri);

        if (directory != null && directory.isDirectory()) {
            for (DocumentFile file : directory.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().startsWith(fileName + ".")
                        && file.getType() != null && file.getType().startsWith("audio/")) {
                    Logger.d("Loading file " + file.getName());

                    return file.getUri();
                }
            }
        } else {
            Logger.d("Directory not found or is not a directory.");
        }

        return null;
    }

    private Uri findAudioFileLegacy(Uri uri, String fileName) {
        String path = uri.getPath();

        if (path != null) {
            File directory = new File(path);
            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            String nameWithoutExtension = file.getName().replaceFirst("[.][^.]+$", "");
                            if (nameWithoutExtension.equals(fileName)) {
                                return Uri.fromFile(file);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private FileDescriptor openFileDescriptor(Uri uri) {
        ParcelFileDescriptor fd = null;
        try {
            fd = getContentResolver().openFileDescriptor(uri, "r");
            tobeClosed = fd;
            if (fd != null) {
                return fd.getFileDescriptor();
            }
        } catch (FileNotFoundException ignored) {
        }
        return null;
    }

    private FileDescriptor openFileDescriptorLegacy(Uri uri) {
        try {
            FileInputStream fin = new FileInputStream(uri.getPath());
            return fin.getFD();
        } catch (IOException ignored) {
        }
        return null;
    }

    private ContentResolver getContentResolver() {
        return mContext.get().getContentResolver();
    }

    public void close() {
        if (tobeClosed != null) {
            try {
                tobeClosed.close();
                tobeClosed = null;
            } catch (IOException ignored) {
            }
        }
    }
}
