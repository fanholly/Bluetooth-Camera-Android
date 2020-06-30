package com.hollyfan.app.camremote;

/**
 * Created by hollyfan on 11/30/15.
 */
public class Constants {

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Camera sends remote
    public static final int PREVIEW_DATA= 0;
    public static final int PICTURE_DATA= 1;

    // EXIF constants
    public static final int ORIENTATION_FLIP_HORIZONTAL = 2;
    public static final int ORIENTATION_FLIP_VERTICAL =4;
    public static final int ORIENTATION_NORMAL = 1;
    public static final int ORIENTATION_ROTATE_180 = 3;
    public static final int ORIENTATION_ROTATE_270 = 8;
    public static final int ORIENTATION_ROTATE_90 = 6;
    public static final int ORIENTATION_TRANSPOSE = 5;
    public static final int ORIENTATION_TRANSVERSE = 7;


    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Messages from Remote to Camera
    public static final int TAKE_PICTURE_MESSAGE = 0;
    public static final int STOP_PREVIEW = 8;
    public static final int RESTART_PREVIEW=9;
    public static final int STOP_RECEIVING_PREVIEW = 3;
    public static final int ERROR_RECEIVING_FRAMES = 4;
    public static final int PICTURE_RECEIVED= 6;
    public static final int FLASH_ON = 5;
    public static final int FLASH_OFF = 7;
    public static final int START_PREVIEW_AGAIN = 10;
    public static final int NEW_P_CALLBACK = 11;
    public static final int CONNECTION_LOST = 12;

}
