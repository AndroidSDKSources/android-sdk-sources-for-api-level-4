/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.gallery;

import com.android.camera.Util;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

// This class handles the mini-thumb file. A mini-thumb file consists
// of blocks, indexed by id. Each block has BYTES_PER_MINTHUMB bytes in the
// following format:
//
// 1 byte status (0 = empty, 1 = mini-thumb available)
// 8 bytes magic (a magic number to match what's in the database)
// 4 bytes data length (LEN)
// LEN bytes jpeg data
// (the remaining bytes are unused)
//
class MiniThumbFile {
    private static final String TAG = "MiniThumbFile";
    private static final int MINI_THUMB_DATA_FILE_VERSION = 3;
    public static final int BYTES_PER_MINTHUMB = 10000;
    private static final int HEADER_SIZE = 1 + 8 + 4;
    private Uri mUri;
    private RandomAccessFile mMiniThumbFile;

    private String randomAccessFilePath(int version) {
        String directoryName =
                Environment.getExternalStorageDirectory().toString()
                + "/DCIM/.thumbnails";
        return directoryName + "/.thumbdata" + version + "-" + mUri.hashCode();
    }

    private void removeOldFile() {
        String oldPath = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION - 1);
        File oldFile = new File(oldPath);
        if (oldFile.exists()) {
            try {
                oldFile.delete();
            } catch (SecurityException ex) {
                // ignore
            }
        }
    }

    private RandomAccessFile miniThumbDataFile() {
        if (mMiniThumbFile == null) {
            removeOldFile();
            String path = randomAccessFilePath(MINI_THUMB_DATA_FILE_VERSION);
            File directory = new File(path).getParentFile();
            if (!directory.isDirectory()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "Unable to create .thumbnails directory "
                            + directory.toString());
                }
            }
            File f = new File(path);
            try {
                mMiniThumbFile = new RandomAccessFile(f, "rw");
            } catch (IOException ex) {
                // Open as read-only so we can at least read the existing
                // thumbnails.
                try {
                    mMiniThumbFile = new RandomAccessFile(f, "r");
                } catch (IOException ex2) {
                    // ignore exception
                }
            }
        }
        return mMiniThumbFile;
    }

    public MiniThumbFile(Uri uri) {
        mUri = uri;
    }

    public void deactivate() {
        if (mMiniThumbFile != null) {
            try {
                mMiniThumbFile.close();
                mMiniThumbFile = null;
            } catch (IOException ex) {
                // ignore exception
            }
        }
    }

    // Get the magic number for the specified id in the mini-thumb file.
    // Returns 0 if the magic is not available.
    public long getMagic(long id) {
        // check the mini thumb file for the right data.  Right is
        // defined as having the right magic number at the offset
        // reserved for this "id".
        RandomAccessFile r = miniThumbDataFile();
        if (r != null) {
            synchronized (r) {
                long pos = id * BYTES_PER_MINTHUMB;
                try {
                    // check that we can read the following 9 bytes
                    // (1 for the "status" and 8 for the long)
                    if (r.length() >= pos + 1 + 8) {
                        r.seek(pos);
                        if (r.readByte() == 1) {
                            long fileMagic = r.readLong();
                            return fileMagic;
                        }
                    }
                } catch (IOException ex) {
                    Log.v(TAG, "Got exception checking file magic: ", ex);
                }
            }
        }
        return 0;
    }

    public void saveMiniThumbToFile(Bitmap bitmap, long id, long magic)
            throws IOException {
        byte[] data = Util.miniThumbData(bitmap);
        saveMiniThumbToFile(data, id, magic);
    }

    public void saveMiniThumbToFile(byte[] data, long id, long magic)
            throws IOException {
        RandomAccessFile r = miniThumbDataFile();
        if (r == null) return;

        long pos = id * BYTES_PER_MINTHUMB;
        synchronized (r) {
            try {
                if (data != null) {
                    if (data.length > BYTES_PER_MINTHUMB - HEADER_SIZE) {
                        // not enough space to store it.
                        return;
                    }
                    r.seek(pos);
                    r.writeByte(0);     // we have no data in this slot

                    // if magic is 0 then leave it alone
                    if (magic == 0) {
                        r.skipBytes(8);
                    } else {
                        r.writeLong(magic);
                    }
                    r.writeInt(data.length);
                    r.write(data);
                    r.seek(pos);
                    r.writeByte(1);  // we have data in this slot
                }
            } catch (IOException ex) {
                Log.e(TAG, "couldn't save mini thumbnail data for "
                        + id + "; ", ex);
                throw ex;
            }
        }
    }

    byte [] getMiniThumbFromFile(long id, byte [] data, long magicCheck) {
        RandomAccessFile r = miniThumbDataFile();
        if (r == null) return null;

        long pos = id * BYTES_PER_MINTHUMB;
        synchronized (r) {
            try {
                r.seek(pos);
                if (r.readByte() == 1) {
                    long magic = r.readLong();
                    if (magic != magicCheck) {
                        return null;
                    }
                    int length = r.readInt();
                    int got = r.read(data, 0, length);
                    if (got != length) return null;
                    return data;
                } else {
                    return null;
                }
            } catch (IOException ex) {
                return null;
            }
        }
    }
}
