package com.hollyfan.app.camremote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.OrientationEventListener;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


public class CameraActivity extends Activity {
    private static final String TAG = "CamRemote";
    private static final String TAG2 = "CameraActivity: ";

    public static Camera mCamera;
    private CameraPreview mPreview;

    // For testing purposes
    private int testN = 0;
    private String testMsg;

    public static boolean safeToTakePicture = true;
    private byte[] sendBuffer = new byte[1024];
    private int picSize;


    // Fields for orientation
    private static int backCameraId;
    private MyOrientationEventListener mOrientationListener;
    private static int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private static Camera.Parameters mParameters;

    // Remote screen dimensions
    public static int remoteWidth, remoteHeight;

    private static ByteBuffer header = ByteBuffer.allocate(4);


    private static ExifInterface oldExif;
    private static int exifOrientation;

    public static boolean sendPreview;

    private FrameLayout preview;

    private boolean flashOn = false;

    private FocusThread mFocusThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        Log.d(TAG, TAG2 + "onCreate");
        Logger.e(TAG, TAG2 + "onCreate");


        // Add the handler
        MainActivity.mChatServiceCamera.addHandler(mHandlerCameraAct);
        //Log.i(TAG, TAG2 + "Camera handler added");
        sendPreview = true;

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, TAG2 + "onStart");
        Logger.e(TAG, TAG2 + "onStart");

        // Set up the camera
        // Create an instance of Camera
        mCamera = MainActivity.mCamera;

        // Get camera parameters
        mParameters = mCamera.getParameters();

        // Get the camera chat service from the main activity
        //mChatServiceCamera = (BTChatServiceCamera) getIntent().getSerializableExtra("CameraChatService");

        // Create orientation listenter. This should be done first because it
        // takes some time to get first orientation.
        getCameraId();
        mOrientationListener = new MyOrientationEventListener(this);
        mOrientationListener.enable();

        // Set up preview
        // Set the preview call back
        setupPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, TAG2 + "onResume");
        Logger.e(TAG, TAG2 + "onResume");


    }

    private void setupPreview(){
        // Create our Preview view and set it as the content of our activity.
        if (remoteHeight==0 || remoteWidth==0){
            Log.d(TAG, TAG2 + "Remote height or width not set");
            while (true){
                Log.d(TAG, TAG2 + "Waiting for remote dimensions to be set");
                if (remoteHeight!=0 && remoteWidth!=0) {
                    Logger.e(TAG, TAG2 + "Optimizing dimensions in while loop");
                    optimizePreviewSize(remoteWidth, remoteHeight);
                    break;
                }
            }
        } else {
            optimizePreviewSize(remoteWidth, remoteHeight);
        }

        mPreview = new CameraPreview(this, mCamera);
        preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);
    }

    // --- Gets camera ID for back facing camera
    private static void getCameraId () {
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                backCameraId = i;
                //Log.i(TAG, TAG2 + "Back camera ID found, is " + Integer.toString(backCameraId));
                break;
            }
        }
    }

    private void optimizePreviewSize(int w, int h) {

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Camera.Size> sizes = mParameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = getOptimalPreviewSize(this,
                sizes, (double) w / h);
        Log.d(TAG, TAG2 + "Optimal size is "+optimalSize.width+"x"+optimalSize.height);

        /*
        Camera.Size original = mParameters.getPreviewSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPreviewSize(optimalSize.width, optimalSize.height);
        }
        mCamera.setParameters(mParameters);
        */

        // set the params:
        mParameters.setPreviewSize(optimalSize.width, optimalSize.height);
        mCamera.setParameters(mParameters);

    }

    public static Camera.Size getOptimalPreviewSize(Activity currentActivity,
                                             List<Camera.Size> sizes, double targetRatio) {
        // Use a very small tolerance because we want an exact match.
        final double ASPECT_TOLERANCE = 0.001;
        if (sizes == null) return null;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        // Because of bugs of overlay and layout, we sometimes will try to
        // layout the viewfinder in the portrait orientation and thus get the
        // wrong size of mSurfaceView. When we change the preview size, the
        // new overlay will be created before the old one closed, which causes
        // an exception. For now, just get the screen size

        int targetHeight = remoteHeight;
        //Log.i(TAG, TAG2 + "targetHeight is " + targetHeight);


        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            //Log.i(TAG, TAG2 + "Preview size Width: "+size.width + ", " + "Height: " + size.height);

            double ratio = (double) size.width / size.height;
            //Log.i(TAG, TAG2 + "Preview size ratio is " + ratio);
            // Compare ratio of the preview size to the picture size width/height
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue; // not within tolerance, start the next iteration
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                // Set the minDiff as the difference between size height and screen height
                // To see if there are better sizes
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        // Cannot find the one match the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            //Log.w(TAG, TAG2 + "No preview size match the aspect ratio");
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }

        return optimalSize;
    }

    public static int roundOrientation(int orientation) {
        return ((orientation + 45) / 90 * 90) % 360;
    }
    private class MyOrientationEventListener
            extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }
        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = roundOrientation(orientation);
            //Log.d(TAG, TAG2 + "Orientation changed, mOrientation is " + mOrientation);

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, TAG2 + "onPause");
        Logger.e(TAG, TAG2 + "onPause");

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, TAG2 + "onStop");
        Logger.e(TAG, TAG2 + "onStop");

        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            preview.removeView(mPreview);
            //mPreview.getHolder().removeCallback(mPreview);
            mCamera.release();
            mCamera = null;
            Log.d(TAG, TAG2 + "Camera released");
        }
    }


    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, TAG2 + "onRestart");
        Logger.e(TAG, TAG2 + "onRestart");

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, TAG2 + "onDestroy");
        Logger.e(TAG, TAG2 + "onDestroy");
        // If activity is destroyed, stop the bluetooth connection
        //MainActivity.mChatServiceCamera.stop();
        //MainActivity.mChatServiceCamera.connectionLost();
        //finish();
    }

    public void capture(){

        // See android.hardware.Camera.Parameters.setRotation for documentation.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            mCamera.getCameraInfo(backCameraId, info);
            rotation = (info.orientation + mOrientation) % 360;
            //Log.i(TAG, TAG2 + "capture, mOrientation is " + mOrientation);
            //Log.i(TAG, TAG2 + "capture, rotation is " + rotation);
        }
        mParameters.setRotation(rotation);
        mCamera.setParameters(mParameters);


        Log.i(TAG, TAG2 + "Calling takePicture");
        mCamera.takePicture(mShutter, null, mPicture);
        //mCamera.takePicture(null, null, mPicture);

    }

    /*
    public void capture2(){
        Log.d(TAG, TAG2 + "Entering capture2");
        // Setting the autofocus
        mCamera.autoFocus(mFocus);

    }
    */

    private Camera.AutoFocusCallback mFocus = new Camera.AutoFocusCallback(){

        public void onAutoFocus (boolean success, Camera camera){
            Log.d(TAG, TAG2 + "Autofocus success is " + success);
            Logger.e(TAG, TAG2 + "Autofocus success is " + success);

            /*

            if (success){
                Logger.e(TAG, "Autofocus successful");
                //capture();
            } else {
                Logger.e(TAG, "Autofocus unsuccessful");
                //capture();

            }
            */
        }

    };


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

    private  Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            //sendPreview = false;
            mCamera.startPreview();
            Log.d(TAG, TAG2 + "Original pic size : " + data.length);


            // Restart pre-review -- this is AFTER picture is taken
            // First save the picture
            File pictureFile = getOutputMediaFile();

            if (pictureFile == null) {
                Log.d(TAG, TAG2 + "Error creating media file, check storage permissions: ");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.e(TAG, TAG2 + "File not found: " + e.getMessage());
                Logger.e(TAG, TAG2 + "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, TAG2 + "Error accessing file: " + e.getMessage());
                Logger.e(TAG, TAG2 + "Error accessing file: " + e.getMessage());
            }
            try {
                oldExif = new ExifInterface(pictureFile.getPath());
            } catch (IOException e){
                Logger.e(TAG, TAG2 + "ExifInterface exception: " + e.getMessage());


            }
            exifOrientation = Integer.parseInt(oldExif.getAttribute(ExifInterface.TAG_ORIENTATION));
            //Log.i(TAG, TAG2 + "The exifOrientation is " + exifOrientation);

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 10, out);
            Log.d(TAG, TAG2 + "Compressed pic size: " + out.size());


            // Then send it to the Remote
            // Send the header -- data type
            header.putInt(Constants.PICTURE_DATA + exifOrientation);
            Log.d(TAG, TAG2 + "Sending picture data flag of: " + exifOrientation);
            sendMessage(header.array());
            header.clear();

            // then send size of the picture
            picSize = out.size();
            header.putInt(picSize);
            sendMessage(header.array());
            header.clear();

            /*
            // Send the pic -- no buffering
            sendMessage(out.toByteArray());
            */

            // Break the preview frame data into chunks of 1024 and send to BTChatServiceCamera to send to client:
            // Create input stream with frame data
            InputStream is = new ByteArrayInputStream(out.toByteArray());

            // Send buffer with size 1024
            int counter = 0;
            int readLength = 0;
            int totalReadLength = 0;
            int bytesLeft = picSize;

            //Log.d(TAG, TAG2 + "BytesLeft is " + Integer.toString(bytesLeft));
            while (bytesLeft > 0 && bytesLeft >= 1024){
                //Log.d(TAG, TAG2 + "Iteration " + Integer.toString(counter));

                try {
                    readLength = is.read(sendBuffer);
                    //Log.d(TAG, TAG2 + "The readLength is " + Integer.toString(readLength));
                    totalReadLength = totalReadLength + readLength;
                    //Log.d(TAG, TAG2 + "The totalreadLength is " + Integer.toString(totalReadLength));
                    CameraActivity.sendMessage(sendBuffer);
                } catch (IOException e) {
                    Logger.e(TAG, TAG2 + "input stream error: " + e.getMessage());

                }
                counter++;
                bytesLeft = bytesLeft - readLength;
                //Log.d(TAG, TAG2 + "BytesLeft is " + Integer.toString(bytesLeft));

            }
            // Send the last set of bytes
            //Log.d(TAG, TAG2 + "Exited while loop");
            byte[] lastSendBuffer = new byte[bytesLeft];
            try {
                //Log.d(TAG, TAG2 + "BytesLeft is " + Integer.toString(bytesLeft));
                readLength = is.read(lastSendBuffer);
                //Log.d(TAG, TAG2 + "The readLength is " + Integer.toString(readLength));
                totalReadLength = totalReadLength + readLength;
                //Log.d(TAG, TAG2 + "The totalreadLength is " + Integer.toString(totalReadLength));
                CameraActivity.sendMessage(lastSendBuffer);
            } catch (IOException e){
                Logger.e(TAG, TAG2 + "input stream error: " + e.getMessage());
            }
            // Sending the delimiter after a frame is done sending
            //Log.d(TAG, TAG2 + testMsg + "All pic bytes sent");

            // Change flag
            safeToTakePicture = true;

            scanFile (new String[] {pictureFile.getPath()});


        }
    };

    private static Camera.ShutterCallback mShutter = new Camera.ShutterCallback() {

        @Override
        public void onShutter() {

            Log.d(TAG, TAG2 + "Shuttercallback sound");
            Logger.e(TAG, TAG2 + "Shuttercallback sound");

        }
    };


    /** Create a file Uri for saving an image or video */
    private  static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile());
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CamRemote");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, TAG2 + "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_"+ timeStamp + ".jpg");

        return mediaFile;
    }

    private void scanFile (String filenName[]){
        // Tell the media scanner about the new file so that it is
        // immediately available to the user.
        MediaScannerConnection.scanFile(this,
                filenName, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    }
                });
    }

    private void newPreview(){
        mPreview = new CameraPreview(this, mCamera);
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandlerCameraAct = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                /*

                case Constants.MESSAGE_READ:

                    if (remoteWidth == 0) {
                        remoteWidth = (int) msg.obj;
                    } else {
                        remoteHeight = (int) msg.obj;
                    }
                    Log.d(TAG, TAG2 + "Remote dimensions are: "+remoteWidth+"x"+remoteHeight);
                    break;

                */

                case Constants.FLASH_OFF:
                    mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    mCamera.setParameters(mParameters);
                    flashOn = false;
                    break;

                case Constants.FLASH_ON:
                    mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    mCamera.setParameters(mParameters);
                    flashOn = true;
                    break;

                case Constants.CONNECTION_LOST:
                    finish();
                    break;

                case Constants.TAKE_PICTURE_MESSAGE:
                    if (safeToTakePicture) {
                    sendPreview = false;
                    mFocusThread = new FocusThread();
                    mFocusThread.start();
                    for (int n = 0; n < 2; n++) {
                        mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        mCamera.setParameters(mParameters);
                        if (flashOn){
                            mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                            mCamera.setParameters(mParameters);
                            mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                            mCamera.setParameters(mParameters);

                        } else {
                            mParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                            mCamera.setParameters(mParameters);
                        }

                        // wait 1 second
                        try {
                            Thread.sleep(1200);

                        } catch (Exception e) {
                            Logger.e(TAG, TAG2 + "Thread sleep exception", e);
                        }
                        Log.d(TAG, "Exit flash for loop");
                    }


                    /*else {
                        // Flash is on, use chime instead
                        try {
                            Thread.sleep(2000);

                        } catch (Exception e) {
                            Logger.e(TAG, TAG2 + "Thread sleep exception", e);
                        }
                    }
                    */
                    // After countdown take picture
                    //Log.i(TAG, TAG2 + "Handler received take pic message");

                        Log.d(TAG, "Calling capture");
                        capture();
                        safeToTakePicture = false;
                    } else {
                        // what happens when user clicks "Capture"
                        // when the preview if frozen?
                    }
                    break;


            }
        }
    };


    private class FocusThread extends Thread {
        public void run(){
            Log.d(TAG, TAG2 + "Starting focus thread");
            mCamera.autoFocus(mFocus);
        }

    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    // TODO: Complete the sendMessage method
    public static void sendMessage(byte[] message) {
        //Log.d(TAG, TAG2 + "Invoking sendMessage");
        // Check that we're actually connected before trying anything
        // TODO: Code for scenario when Bluetooth connection fails.
        /*
        if (MainActivity.mChatServiceCamera.getState() != BTChatServiceCamera.STATE_CONNECTED) {
            Toast.makeText(getApplicationContext(), "you're not connected", Toast.LENGTH_LONG).show();
            return;
        }
        */
        // Check that there's actually something to send
        //if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            //byte[] send = message.getBytes();
            MainActivity.mChatServiceCamera.write(message);

            /* Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        */}


}


