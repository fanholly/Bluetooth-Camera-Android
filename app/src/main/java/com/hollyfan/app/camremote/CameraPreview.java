package com.hollyfan.app.camremote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class CameraPreview  extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CamRemote";
    private static final String TAG2 = "CameraPreview: ";


    private SurfaceHolder mHolder;
    private Camera mCamera;
    private int cameraWidth, cameraHeight;

    // For testing purposes
    private int testN = 0;
    private String testMsg;
    private Camera.Parameters mParams;
    private Rect mArea;
    private byte[] send;
    public static Bitmap bm;
    private Context mContext;
    ByteBuffer header1 = ByteBuffer.allocate(4);
    byte[] frameFlag = new byte[4];
    ByteBuffer header2 = ByteBuffer.allocate(4);


    private byte[] sendBuffer = new byte[1024];
    private int frameSize;
    public static boolean sendPreviewSwitch = true;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        Log.d(TAG, TAG2 + "Initializing CameraPeview");
        if (mCamera == null) {
            mCamera = CameraActivity.mCamera;
        }

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mParams = mCamera.getParameters();
        cameraWidth = mParams.getPreviewSize().width;
        cameraHeight = mParams.getPreviewSize().height;
        mArea = new Rect(0, 0, cameraWidth, cameraHeight);

        mContext = context;

        // set header 1
        header1.putInt(Constants.PREVIEW_DATA);
        frameFlag = header1.array();


        //Log.i(TAG, TAG2 + "Camera width is " + Integer.toString(cameraWidth));
        //Log.i(TAG, TAG2 + "Camera height is " + Integer.toString(cameraHeight));

}

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            if (CameraActivity.mCamera != null) {
                mCamera.setPreviewDisplay(holder);
                mCamera.setPreviewCallback(pCallback);
                //mCamera.setOneShotPreviewCallback(pCallback);
                mCamera.startPreview();
            }
        } catch (IOException e) {
           Log.e(TAG, TAG2 + "Error setting camera preview: " + e.getMessage());
            Logger.e(TAG, TAG2 + "Error setting camera preview: " + e.getMessage());

        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, TAG2 + "Surface destroyed");

        //mCamera.release();              // release the camera immediately on pause event
        //Log.d(TAG, TAG2 + "Camera released");
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            Logger.e(TAG, TAG2 + "tried to stop a non-existent preview: " + e.getMessage());

            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(pCallback);
            //mCamera.setOneShotPreviewCallback(pCallback);
            mCamera.startPreview();


        } catch (Exception e){
            Log.e(TAG, TAG2 + "Error starting camera preview: " + e.getMessage());
            Logger.e(TAG, TAG2 + "Error starting camera preview: " + e.getMessage());

        }
    }

    Camera.PreviewCallback pCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (sendPreviewSwitch) {
                if (CameraActivity.sendPreview) {

                    testN++;
                    testMsg = "Frame" + Integer.toString(testN);

            /*
            try{
                CameraActivity.compress(data);
            } catch (IOException e){

            }

            /*

            // Test with startTestFrame
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            YuvImage yuv = new YuvImage(data, ImageFormat.NV21, cameraWidth, cameraHeight, null);
            yuv.compressToJpeg(mArea, 50, out);
            bm = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
            startTestFrame();
            */


                    //Testing performance of various compression methods

                    // Raw frame data without compression
                    Log.d(TAG, TAG2 + "Generating " + testMsg); // + ". Its byte array length is " + Integer.toString(data.length));

                    // Yuv compressed to Jpeg
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    YuvImage yuv = new YuvImage(data, ImageFormat.NV21, cameraWidth, cameraHeight, null);
                    yuv.compressToJpeg(mArea, 15, out);
                    //Log.d(TAG, TAG2 + "Length of buffer from yuv compressed to Jpeg " + Integer.toString(out.size()));

                    // Send the header -- data type
                    CameraActivity.sendMessage(frameFlag);

                    // Then send header -- size of frame
                    frameSize = out.size();
                    header2.putInt(frameSize);
                    CameraActivity.sendMessage(header2.array());
                    Log.d(TAG, TAG2 + "Sending frame size of " + Integer.toString(header2.getInt(0)));
                    header2.clear();

            /*
            // Send the frame -- without buffering
            Log.d(TAG, TAG2 + "Sending frame size of " + Integer.toString(out.size()));
            CameraActivity.sendMessage(out.toByteArray());
            */

                    // Break the preview frame data into chunks of 1024 and send to BTChatServiceCamera to send to client:
                    // Create input stream with frame data
                    InputStream is = new ByteArrayInputStream(out.toByteArray());

                    // Send buffer with size 1024
                    int counter = 0;
                    int readLength = 0;
                    int totalReadLength = 0;
                    int bytesLeft = frameSize;

                    //Log.d(TAG, TAG2 + "BytesLeft is " + Integer.toString(bytesLeft));
                    while (bytesLeft > 0 && bytesLeft >= 1024) {
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
                        // Log.d(TAG, TAG2 + "BytesLeft is " + Integer.toString(bytesLeft));
                        readLength = is.read(lastSendBuffer);
                        //Log.d(TAG, TAG2 + "The readLength is " + Integer.toString(readLength));
                        totalReadLength = totalReadLength + readLength;
                        //Log.d(TAG, TAG2 + "The totalreadLength is " + Integer.toString(totalReadLength));
                        CameraActivity.sendMessage(lastSendBuffer);
                    } catch (IOException e) {
                        Logger.e(TAG, TAG2 + "input stream error: " + e.getMessage());

                    }
                    sendPreviewSwitch = false;

                    // Sending the delimiter after a frame is done sending
                    //Log.d(TAG, TAG2 + testMsg + " iteration complete");

                } else {
                    Log.d(TAG, TAG2 + "Not sending preview frame");
                }
            } else {
                sendPreviewSwitch = true;
                Log.d(TAG, TAG2 + "Not sending frame due to switch");
            }

        }

        /*
        private void startTestFrame(){
            Log.d(TAG, TAG2 + "Starting test frame activity");
            Intent testIntent = new Intent(mContext, TestFrame.class);
            testIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(testIntent);
        }
        */

    };
}