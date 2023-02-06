package com.nousdtech.audiodemo;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import in.basulabs.audiofocuscontroller.AudioFocusController;

import android.Manifest;
import android.app.PendingIntent;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "Lawrence";
    private EditText mLogEt;
    private AudioFocusController audioFocusController;
    protected String[] mPermissions = new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MOUNT_FORMAT_FILESYSTEMS,
            "android.permission.USB_PERMISSION",
            "android.hardware.usb.accessory",
            "android.hardware.usb.host",
            "android.permission.WRITE_MEDIA_STORAGE"
    };
    private final static String ACTION_USB_PERMISSION = "com.nousdtech.audiodemo.usb";

    private UsbManager usbManager = null;
    private UsbMassStorageDevice usbMassStorageDevice = null;
    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION == intent.getAction()) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    mLogEt.append("USB Device Name " + device.getDeviceName() + "\n");
                } else {
                    mLogEt.append("Permission denied for " + device.getDeviceName() + "\n");
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(usbReceiver);
    }

    @Override
    protected void onDestroy() {
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(MainActivity.this);
        if (devices != null && devices.length == 0) {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0,
                    new Intent(ACTION_USB_PERMISSION), 0);
            for (UsbMassStorageDevice device: devices) {
                if (usbManager.hasPermission(device.getUsbDevice())) {
                    device.close();
                }
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        mLogEt = findViewById(R.id.log_editText);
        List<String> permissions = new ArrayList<>();
        for (String permission : mPermissions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(permission);
                }
            }
        }

        if (!permissions.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(permissions.toArray(new String[permissions.size()]),
                        0);
            }
        }

        audioFocusController = new AudioFocusController.Builder(this) // Context must be passed
                .setAudioFocusChangeListener(new AudioFocusController.OnAudioFocusChangeListener() {
                    @Override
                    public void decreaseVolume() {
                        Log.e(TAG, "decreaseVolume");
                    }

                    @Override
                    public void increaseVolume() {
                        Log.e(TAG, "increaseVolume");
                    }

                    @Override
                    public void pause() {
                        Log.e(TAG, "pause");
                    }

                    @Override
                    public void resume() {
                        Log.e(TAG, "resume");
                    }
                }) // Pass the listener instance created above
                .setAcceptsDelayedFocus(true) // Indicate whether you will accept delayed focus
                .setPauseWhenAudioIsNoisy(false) // Indicate whether you want to be paused when audio becomes noisy
                .setPauseWhenDucked(false) // Indicate whether you want to be paused instead of ducking
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // Set the content type
                .setDurationHint(AudioManager.AUDIOFOCUS_GAIN) // Set the duration hint
                .setUsage(AudioAttributes.USAGE_MEDIA) // Set the usage
                .setStream(AudioManager.STREAM_MUSIC) // Set the stream
                .build();
        findViewById(R.id.play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioFocusController.requestFocus();
                int fileCount = 0;
                String playPath = "";
                ContentResolver musicResolver = getContentResolver();
                Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                Cursor musicCursor = musicResolver.query(musicUri, null, null, null,
                        MediaStore.MediaColumns.DATE_ADDED + " DESC");
                if (musicCursor != null && musicCursor.moveToFirst()) {
                    //get columns
                    int titleColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE);
                    int idColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID);
                    int artistColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST);
                    int albumColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                    int pathColumn = musicCursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA);
                    int durationColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                    do {
                        String thisTitle = musicCursor.getString(titleColumn);
                        String path = musicCursor.getString(pathColumn);
                        if ("new_order-blue_monday".equals(thisTitle)) {
                            playPath = path;
                        }
                        Log.d(TAG, "thisTitle = " + thisTitle);
                        Log.d(TAG, "path = " + path);
                        mLogEt.append(thisTitle + "\n");
                        mLogEt.append( path + "\n");
                        fileCount++;
                    } while (musicCursor.moveToNext());
                }
                if (fileCount == 0) {
                    mLogEt.append("no any music file\n");
                }
                if (!TextUtils.isEmpty(playPath)) {
                    playMusic(playPath, false);
                }
            }
        });
        findViewById(R.id.stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMusic();
            }
        });
        findViewById(R.id.clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLogEt.setText("");
            }
        });
        findViewById(R.id.play_asset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioFocusController.requestFocus();
                playAssetMusic("submit_success.mp3", false);
            }
        });
        findViewById(R.id.get_usb_permission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(MainActivity.this);
                    if (devices == null || devices.length == 0) {
                        mLogEt.append("No devices" + "\n");
                    } else {
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0,
                                new Intent(ACTION_USB_PERMISSION), 0);
                        for (UsbMassStorageDevice device: devices) {
                            if (!usbManager.hasPermission(device.getUsbDevice())) {
                                usbManager.requestPermission(device.getUsbDevice(),pendingIntent);
                            } else {
                                device.init();
                                usbMassStorageDevice = device;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    mLogEt.append(e.toString()+ "\n");
                }
            }
        });
        findViewById(R.id.play_usb_manager).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioFocusController.requestFocus();
                try {
                    // Only uses the first partition on the device
                    FileSystem currentFs = usbMassStorageDevice.getPartitions().get(0).getFileSystem();
                    Log.d(TAG, "Capacity: " + currentFs.getCapacity());
                    mLogEt.append("Capacity: " + currentFs.getCapacity() + "\n");
                    Log.d(TAG, "Occupied Space: " + currentFs.getOccupiedSpace());
                    mLogEt.append("Occupied Space: " + currentFs.getOccupiedSpace() + "\n");
                    Log.d(TAG, "Free Space: " + currentFs.getFreeSpace());
                    mLogEt.append("Free Space: " + currentFs.getFreeSpace() + "\n");
                    Log.d(TAG, "Chunk size: " + currentFs.getChunkSize());
                    mLogEt.append("Chunk size: " + currentFs.getChunkSize() + "\n");

                    UsbFile root = currentFs.getRootDirectory();

                    UsbFile[] files = root.listFiles();
                    for (UsbFile file: files) {
                        Log.d(TAG, file.getName());
                        if (!file.isDirectory()) {
                            mLogEt.append("Name: " + file.getName() + ", file size: " + file.getLength() + "\n");
                            mLogEt.append("getAbsolutePath: " + file.getAbsolutePath() + "\n");
                            playUsbManager(file, false);
                            break;
                        }
                    }
                } catch (Exception e) {
                    mLogEt.append(e.toString() + "\n");
                }
            }
        });
        findViewById(R.id.play_usb_manager_sp).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioFocusController.requestFocus();
                try {
                    // Only uses the first partition on the device
                    FileSystem currentFs = usbMassStorageDevice.getPartitions().get(0).getFileSystem();
                    UsbFile root = currentFs.getRootDirectory();
                    UsbFile[] files = root.listFiles();
                    for (UsbFile file: files) {
                        Log.d(TAG, file.getName());
                        if (!file.isDirectory()) {
                            mLogEt.append("Name: " + file.getName() + ", file size: " + file.getLength() + "\n");
                            mLogEt.append("getAbsolutePath: " + file.getAbsolutePath() + "\n");
                            playUsbManagerStorageProvider(file, false);
                            break;
                        }
                    }
                } catch (Exception e) {
                    mLogEt.append(e.toString() + "\n");
                }
            }
        });
    }
    private static MediaPlayer m = null;
    public void playMusic(String fileName, boolean isLoop) {
        mLogEt.append("playMusic " + fileName + "\n");
        try {
            Log.d(TAG, "playMusic, " + fileName);
            File originFile = new File(fileName);

            mLogEt.append("canRead = " + originFile.canRead() + "\n");
            File file = new File(getFilesDir().getAbsolutePath() + "/1.mp3");
            copy(new File(fileName), file);
            mLogEt.append("file to string = " + file.toString() + "\n");
            m = new MediaPlayer();
            m.setDataSource(file.getAbsolutePath());
            m.prepare();
            m.setVolume(1f, 1f);
            if (isLoop) m.setLooping(false);
            m.start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            mLogEt.append(e.toString() + "\n");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static void copy(File origin, File dest) throws IOException {
        Files.copy(origin.toPath(), dest.toPath());
    }

    public void playAssetMusic(String fileName, boolean isLoop) {
        mLogEt.append("playAssetMusic " + fileName + "\n");
        try {
            Log.d(TAG, "playMusic, " + fileName);
            m = new MediaPlayer();
            AssetFileDescriptor descriptor = getAssets().openFd(fileName);
            m.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            m.prepare();
            m.setVolume(1f, 1f);
            if (isLoop) m.setLooping(false);
            m.start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            mLogEt.append(e.toString() + "\n");
        }
    }

    public void playUsbManager(UsbFile usbFile, boolean isLoop) {
        mLogEt.append("playUsbManager " + usbFile.getName() + "\n");
        new Thread(new Runnable() {
            @Override
            public void run() {
                UsbFileInputStream inputStream = null;
                FileOutputStream out = null;
                BufferedOutputStream bis = null;
                try {
                    Log.d(TAG, "playUsbManager, " +  usbFile.getName());
                    File temp = File.createTempFile("musicTemp", "temp");
                    inputStream = new UsbFileInputStream(usbFile);
                    out = new FileOutputStream(temp);
                    bis = new BufferedOutputStream(out);

                    byte buf[] = new byte[128];
                    do {
                        int numRead = inputStream.read(buf);
                        if (numRead < 0) break;
                        bis.write(buf, 0, numRead);
                    } while (true);

                    m = new MediaPlayer();
                    m.setDataSource(temp.getAbsolutePath());
                    m.prepare();
                    m.setVolume(1f, 1f);
                    if (isLoop) m.setLooping(false);
                    m.start();
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                    e.printStackTrace();
                } finally {
                    try {
                        if (bis != null) bis.close();
                    } catch (Exception e) {
                        //do nothing
                    }
                    try {
                        if (out != null) out.close();
                    } catch (Exception e) {
                        //do nothing
                    }
                    try {
                        if (inputStream != null) inputStream.close();
                    } catch (Exception e) {
                        //do nothing
                    }
                }
            }
        }).start();
    }

    public void playUsbManagerStorageProvider(UsbFile usbFile, boolean isLoop) {
        mLogEt.append("playUsbManagerStorageProvider " + usbFile.getName() + "\n");
        try {
            m = new MediaPlayer();
            byte[] bytes = IOUtils.toByteArray(new UsbFileInputStream(usbFile));
            m.setDataSource(new MyAudioSource(bytes));
            m.prepare();
            m.setVolume(1f, 1f);
            if (isLoop) m.setLooping(false);
            m.start();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    private void stopMusic() {
        mLogEt.append("stopMusic\n");
        if (m == null) return;
        try {
            m.stop();
            m.release();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }
}