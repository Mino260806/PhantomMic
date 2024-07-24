package tn.amin.phantom_mic;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;

import de.robv.android.xposed.XposedBridge;
import tn.amin.phantom_mic.log.Logger;

public class FileManager {
    final WeakReference<Context> mContext;

    private ParcelFileDescriptor tobeClosed = null;

    public FileManager(Context context) {
        mContext = new WeakReference<>(context);
    }

    public String readLine(Uri parent, String fileName) {
        ParcelFileDescriptor tobeClosedBackup = tobeClosed;
        FileDescriptor fd = open(parent, fileName);
        if (fd == null) {
            Logger.d("fd is null");
            return null;
        }

        try {
            // Create a FileInputStream from the FileDescriptor
            FileInputStream fileInputStream = new FileInputStream(fd);

            // Wrap the FileInputStream with BufferedReader
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));

            // Read lines from the file
            String line = bufferedReader.readLine();

            // Close resources
            bufferedReader.close();
            fileInputStream.close();
            close();
            tobeClosed = tobeClosedBackup;

            return line;
        } catch (IOException e) {
            Logger.d(e.getMessage());
        }

        return null;
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

    public FileDescriptor open(Uri parent, String fileName) {
        FileDescriptor fd = null;
        if ("content".equals(parent.getScheme())) {
            DocumentFile directory = DocumentFile.fromTreeUri(mContext.get(), parent);
            DocumentFile file;
            if (directory != null && (file = directory.findFile(fileName)) != null) {
                fd = openFileDescriptor(file.getUri());
            }
        }
        else {
            fd = openFileDescriptorLegacy(Uri.withAppendedPath(parent, fileName));
        }

        return fd;
    }

    private Uri findAudioFile(Uri uri, String fileName) {
        // Create a DocumentFile from the tree URI
        DocumentFile directory = DocumentFile.fromTreeUri(mContext.get(), uri);

        if (directory != null && directory.isDirectory()) {
            for (DocumentFile file : directory.listFiles()) {
                if (file.isFile() && file.getName() != null && file.getName().startsWith(fileName)
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
        } catch (IOException e) {
            Logger.d(e.getMessage());
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
