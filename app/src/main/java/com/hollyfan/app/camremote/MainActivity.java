package com.hollyfan.app.camremote;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static Camera mCamera;

    private static final String TAG = "Photog6";
    private static final String TAG2 = "MainActivity: ";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_DISCOVERABLE =3;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Member object for the chat services
     */
    public static BTChatServiceCamera mChatServiceCamera = null;
    public static BTChatServiceRemote mChatServiceRemote = null;

    private MainPreview mMainPreview;
    private FrameLayout previewFL;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            this.finish();

        }

        // Orient preview to portrait mode, passing back-facing camera
        setCameraDisplayOrientation(this, 0, mCamera);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            //TODO: Set up preview?
            setupPreview();
            useAsCamera();
        }

    }

    private void setupPreview(){
        mCamera = getCameraInstance();
        mMainPreview = new MainPreview(this);
        previewFL = (FrameLayout) findViewById(R.id.camera_preview);
        previewFL.addView(mMainPreview);

    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            Log.e(TAG, TAG2 + "Camera is not available (in use or does not exist)");
            Logger.e(TAG, TAG2 + "Camera is not available (in use or does not exist)");

        }
        return c; // returns null if camera is unavailable
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy stopping BT chat services");

        mCamera.release();
        if (mChatServiceCamera!= null) {
            mChatServiceCamera.stop();
            mChatServiceCamera.connectionLost();
        }
        if (mChatServiceRemote != null) {
            mChatServiceRemote.stop();
            mChatServiceRemote.connectionLost();
        }

    }

    /**
     * Set up the UI and background operations for chat.
     * WILL BE SETUPCAMERA
     */
    private void setupChatServiceCamera() {
        Log.d(TAG, "setupChatServiceCamera()");

        // Initialize the BTChatService for Camera to perform bluetooth connections as server
        mChatServiceCamera = new BTChatServiceCamera(this, mHandlerCamera);

        // Start the service for camera
        mChatServiceCamera.begin();
    }

    /**
     * Set up the UI and background operations for chat.
     * WILL BE SETUPCAMERA
     */
    private void setupChatServiceRemote() {
        Log.d(TAG, "setupChatServiceRemote()");

        // Initialize the BTChatService for Camera to perform bluetooth connections as server
        // Don't need to start anything if just client

        mChatServiceRemote = new BTChatServiceRemote(this, mHandlerRemote);

    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        // TODO: Think through scenario where ensure discoverable is already on.
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            // If user selects yes they also permit to turn on bluetooth if it's not turned on
            startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
        }
    }


    private void useAsCamera() {
        Logger.e(TAG, TAG2 + "Use as Camera");

        if (mChatServiceRemote != null) {
            mChatServiceRemote.stop();
        }

            // User selects Camera mode
            // First check if mChatServiceCamera already exists, if not create one
            if (mChatServiceCamera == null) {
                setupChatServiceCamera();
            }

            // if there is chat service camera make sure it's listening
            if (mChatServiceCamera.mState != BTChatServiceCamera.STATE_LISTEN ){
                mChatServiceCamera.begin();
            }

            // If camera is selected, turn on discoverability
            // Per Android BT documentation, "Tip: Enabling discoverability will automatically enable Bluetooth."
            // No need to check if BT is enabled as with Client (Remote)
            // ensureDiscoverable(); ---  not needed for paired devices??

            // Then goes to onActivityResult

    }


    public void useAsRemote (View view){
        Logger.e(TAG, TAG2 + "Use as Remote");

        if (mChatServiceCamera!= null) {
            mChatServiceCamera.stop();
        }

        //check button worked
        Toast.makeText(getApplicationContext(), "this is a remote", Toast.LENGTH_SHORT).show();

        // First check if mChatServiceRemote exists

        if (mChatServiceRemote == null) {
            // setupChat()initializes the BluetoothChatService to mChatService
            setupChatServiceRemote();

            // And also set up devices-List of devices: start device list activity
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
        } else {
            // not the first time user is clicking Remote, don't need to set up chat
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);

            }

    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandlerCamera = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                // The following was originally used to display status bar

                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BTChatServiceCamera.STATE_CONNECTED:
                            // Start the camera activity after it's connected
                            // Pass it the mChatServiceCamera
                            //cameraIntent.putExtra("CameraChatService", mChatServiceCamera);
                            startCamera();
                            break;
                        /*
                        case BTChatServiceCamera.STATE_LISTEN:
                            // Once state is listen start the spinner
                            spinner.setVisibility(View.VISIBLE);

                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                        */
                    }
                    break;

                /* Don't need to display to the camera what is "written" (images)
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;


                // TODO: Code behavior to read Remote's instructions
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    // mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                */

                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                        Toast.makeText(getApplicationContext(), "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;

                case Constants.MESSAGE_TOAST:
                        Toast.makeText(getApplicationContext(), msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();

                    break;
            }
        }
    };


    private void startRemote () {
        //remoteIntent.putExtra("RemoteChatService", mChatServiceRemote);
        Intent remoteIntent = new Intent(this, RemoteActivity.class);
        startActivity(remoteIntent);
    }

    private void startCamera () {
        //remoteIntent.putExtra("RemoteChatService", mChatServiceRemote);
        Intent cameraIntent = new Intent(this, CameraActivity.class);
        startActivity(cameraIntent);
    }
    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandlerRemote = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BTChatServiceRemote.STATE_CONNECTED:
                            startRemote();

                            break;

                        /*
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                            */
                    }
                    break;



                /* DISPLAY WHAT IS 'WRITTEN'? (INSTRUCTIONS TO CAMERA)
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;


                // TODO: Use either broadcast manager or fragment to send message to Remote activity/fragment.
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    // mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                */

                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;

                case Constants.MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(Constants.TOAST),
                            Toast.LENGTH_SHORT).show();

                    break;
            }
        }
    };

    // onActivityResult and connectDevice, the last two methods, are only used by Remote
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                }
                break;

            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    //TODO: when user accepts bluetooth
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "BT not enabled. Leaving app.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }

                /*
                // TODO:  Deal with discoverability later

            case REQUEST_DISCOVERABLE:
                if (resultCode == 300){
                    // If user accepts discoverability then set up the chat service for camera
                    // This also automatically turns on bluetooth if it's not already on
                    setupChatServiceCamera();

                } else if (resultCode == Activity.RESULT_CANCELED){
                    Toast.makeText(this, "Please turn on discoverabilty so the remote can connect to camera",
                            Toast.LENGTH_SHORT).show();
                }
                */
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     */
    private void connectDevice(Intent data) {
        Logger.e(TAG, TAG2 + "Connecting a device");
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatServiceRemote.connect(device);
    }
}
