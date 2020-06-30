package com.hollyfan.app.camremote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RemoteActivity extends Activity {
    private static final String TAG = "CamRemote ";
    private static final String TAG2 = "RemoteActivity ";

    private View remoteView;


    /*
    private ListView mConversationView;
    private ArrayAdapter<String> mConversationArrayAdapter;
    */

    private FrameLayout preview;
    private Bitmap picture;

    // Used to send signals to Camera
    ByteBuffer messageToCamera = ByteBuffer.allocate(4);


    public static int exif;
    ExifInterface newExif;

    private static Uri lastPicUri = null;

    private boolean galleryOn = false;

    private boolean flashOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.e(TAG, TAG2 +"onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        Log.v(TAG, TAG2 +"setupChatServiceRemote()");

        // Get the camera chat service from the main activity
        //mChatServiceRemote = (BTChatServiceRemote) getIntent().getSerializableExtra("RemoteChatService");

        // Add the handler
        MainActivity.mChatServiceRemote.addHandler(mHandlerRemoteAct);
        Log.v(TAG, TAG2 +"Remote handler added");

        preview = (FrameLayout) findViewById(R.id.remoteFrameLayout);
        remoteView = new myView(this);

        // Set the picture to something to avoid null pointer exception:
        Bitmap.Config conf = Bitmap.Config.ARGB_8888; // see other conf types
        picture = Bitmap.createBitmap(500, 500, conf); // this creates a MUTABLE bitmap

        // Add the view to the remote frame layout
        preview.addView(remoteView);

        // Send the remote phone's screen dimension
        sendScreenDimensions();

        // On Flash
        ToggleButton toggle = (ToggleButton) findViewById(R.id.flash);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled, turn flash on
                    Log.d(TAG, TAG2 +"Tell camera to turn on flash");
                    messageToCamera.putInt(Constants.FLASH_ON);
                    sendMessage(messageToCamera.array());
                    messageToCamera.clear();
                    flashOn = true;
                } else {
                    // The toggle is disabled, turn flash off
                    Log.d(TAG, TAG2 +"Tell camera to turn on flash");
                    messageToCamera.putInt(Constants.FLASH_OFF);
                    sendMessage(messageToCamera.array());
                    messageToCamera.clear();
                    flashOn = false;

                }
            }
        });


        /*
        mConversationView = (ListView) findViewById(R.id.in);
        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView.setAdapter(mConversationArrayAdapter);

        mConversationArrayAdapter.add("test string");
        */
    }

    private void sendScreenDimensions(){

        Display display = getWindowManager().getDefaultDisplay();

        // Send width
        messageToCamera.putInt(display.getWidth());
        sendMessage(messageToCamera.array());
        messageToCamera.clear();


        // Send width
        messageToCamera.putInt(display.getHeight());
        sendMessage(messageToCamera.array());
        messageToCamera.clear();

    }


    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandlerRemoteAct = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case Constants.CONNECTION_LOST:
                    finish();
                    break;

                case Constants.ERROR_RECEIVING_FRAMES:
                    Log.d(TAG, TAG2 +"Tell camera to flush stream and restart preview");
                    messageToCamera.putInt(Constants.ERROR_RECEIVING_FRAMES);
                    RemoteActivity.sendMessage(messageToCamera.array());
                    messageToCamera.clear();
                    break;

                /*
                case Constants.STOP_RECEIVING_PREVIEW:
                    Log.d(TAG, TAG2 +"Tell camera to stop sending preview frames");
                    messageToCamera.putInt(Constants.STOP_RECEIVING_PREVIEW);
                    RemoteActivity.sendMessage(messageToCamera.array());
                    messageToCamera.clear();
                    break;
                    */

                case Constants.MESSAGE_READ:

                    // Dislay to image view
                    byte[] readBuf = (byte[]) msg.obj;

                    /*
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    YuvImage yuv = new YuvImage(readBuf, ImageFormat.NV21, 1440, 1080, null);
                    yuv.compressToJpeg(mArea, 50, out);
                    */
                    picture = BitmapFactory.decodeByteArray(readBuf, 0, readBuf.length);
                    if (picture != null) {
                        remoteView.invalidate();
                        Log.d(TAG, TAG2 +"Bitmap is not null");

                    } else{
                        Log.d(TAG, TAG2 +"Bitmap is null");

                    }


                    /*
                    Log.d(TAG, TAG2 +"Byte buffer size: " + Integer.toString(readBuf.length));


                    ByteBuffer buffer = ByteBuffer.wrap(readBuf);
                    Log.d(TAG, TAG2 +"Byte buffer remaining : " + Integer.toString(buffer.remaining()));

                    picture = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
                    Log.d(TAG, TAG2 +"Bitmap size = " + Integer.toString(picture.getRowBytes() * picture.getHeight()));
                    picture.copyPixelsFromBuffer(buffer);

                    mImageView= (ImageView) findViewById(R.id.remoteImageView);
                    mImageView.setImageBitmap(picture);


                    /*
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add("From Camera:  " + readMessage);
                    */

                    break;

                case Constants.PICTURE_RECEIVED:
                    byte []  picData = (byte[]) msg.obj;
                    Log.d(TAG, TAG2 +"Handler receiving pic with size " + picData.length);


                    File pictureFile = getOutputMediaFile();

                    if (pictureFile == null) {
                        Log.d(TAG, TAG2 +"Error creating media file, check storage permissions: ");
                        return;
                    }

                    try {
                        FileOutputStream fos = new FileOutputStream(pictureFile);
                        fos.write(picData);
                        fos.close();
                        //Log.d(TAG, TAG2 +"Creating pictureFile");

                    } catch (FileNotFoundException e) {
                        Log.e(TAG, TAG2 +"File not found: " + e.getMessage());
                        Logger.e(TAG, TAG2 +"File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.e(TAG, TAG2 +"Error accessing file: " + e.getMessage());
                        Logger.e(TAG, TAG2 +"Error accessing file: " + e.getMessage());
                    }

                    // Set the EXIF tag
                    if (exif > Constants.PICTURE_DATA) {
                        try {
                            newExif = new ExifInterface(pictureFile.getPath());
                            newExif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(exif));
                            newExif.saveAttributes();
                        } catch (IOException e) {
                            Logger.e(TAG, TAG2 +"ExifInterface error: " + e.getMessage());

                        }

                    }
                    scanFile(new String[]{pictureFile.getPath()});

                    restartPreview();
                    break;
                    // Clear the buffers in BTChatServiceRemote so preview can restart??

            }

        }
    };

    private void restartPreview(){
        Log.d(TAG, TAG2 + "Tell camera to restart preview");
        messageToCamera.putInt(Constants.RESTART_PREVIEW);
        RemoteActivity.sendMessage(messageToCamera.array());
        messageToCamera.clear();
    }

    private void stopPreview(){
        Log.d(TAG, TAG2 +"Tell camera to stop preview");
        Log.e(TAG, TAG2 +"Tell camera to stop preview");
        messageToCamera.putInt(Constants.STOP_PREVIEW);
        RemoteActivity.sendMessage(messageToCamera.array());
        messageToCamera.clear();
    }


    private void scanFile (String filenName[]){
        // Tell the media scanner about the new file so that it is
        // immediately available to the user.
        MediaScannerConnection.scanFile(this,
                filenName, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        //Log.i("ExternalStorage", "Scanned " + path + ":");
                        //Log.i("ExternalStorage", "-> uri=" + uri);
                        lastPicUri = uri;
                    }
                });
    }
    /** Create a file Uri for saving an image or video */
    public  static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile());
    }

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CamRemote");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("CamRemote", "failed to create directory");
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


    public void openGallery(View view) {
        if (lastPicUri == null){
            Toast.makeText(getApplicationContext(), "No pictures yet", Toast.LENGTH_LONG).show();
        } else {
            Logger.e(TAG, TAG2 + "Opening gallery");
            Intent i = new Intent();
            i.setAction(Intent.ACTION_VIEW);
            i.setData(lastPicUri);
            galleryOn = true;
            startActivity(i);
        }
    }

    private class myView extends View {

        public myView(Context context) {
            super(context);
            // TODO Auto-generated constructor stub
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // TODO Auto-generated method stub
            canvas.drawBitmap(picture, 0, 0, null);
        }
    }


    public void capture(View view) {
        Log.d(TAG, TAG2 + "Clicked CAPTURE button");
        messageToCamera.putInt(Constants.TAKE_PICTURE_MESSAGE);
        sendMessage(messageToCamera.array());
        messageToCamera.clear();

        /*
        // Clear the buffers in BTChatServiceRemote so preview can restart??

        final MediaPlayer mp = MediaPlayer.create(this, R.raw.chime10);

            for (int n = 0; n < 2; n++) {
                Log.d(TAG, TAG2 +"n is " + n);
                mp.start();
                while (true) {
                    if (!mp.isPlaying()) {
                        break;
                    }
                }

            }
            */

    }

    /*
    public void flush(View view){
        messageToCamera.putInt(Constants.ERROR_RECEIVING_FRAMES);
        RemoteActivity.sendMessage(messageToCamera.array());
        messageToCamera.clear();
        //startPreviewAgain();
        BTChatServiceRemote.readInputStream=true;
    }
    */


    private void startPreviewAgain(){
        Log.d(TAG, TAG2 +"Tell camera to start preview again");
        Logger.e(TAG, TAG2 +"Tell camera to start preview again");
        messageToCamera.putInt(Constants.START_PREVIEW_AGAIN);
        sendMessage(messageToCamera.array());
        messageToCamera.clear();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, TAG2 + "onPause");
        Logger.e(TAG, TAG2 + "onPause");
        if (galleryOn) {
            // stop the camera preview
            stopPreview();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, TAG2 + "onResume");
        Logger.e(TAG, TAG2 +"onResume");
        // restart camera preview
        if (galleryOn) {
            startPreviewAgain();
            galleryOn = false;
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, TAG2 + "onStart");
        Logger.e(TAG, TAG2 + "onStart");

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, TAG2 + "onRestart");
        Logger.e(TAG, TAG2 + "onRestart");

    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, TAG2 + "onStop");
        Logger.e(TAG, TAG2 +"onStop");

    }
    /**
     * For sending message to camera
     *
     * @param message A string of text to send.
     */
    // TODO: Complete the sendMessage method
    public static void sendMessage(byte[] message) {
        //Log.d(TAG, TAG2 +"Invoking sendMessage");
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
        MainActivity.mChatServiceRemote.write(message);

            /* Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        */}

    public void onDestroy() {
        Logger.e(TAG, TAG2 + "onDestroy");
        super.onDestroy();
        //MainActivity.mChatServiceRemote.stop();
        //MainActivity.mChatServiceRemote.connectionLost();
        //finish();
    }



}

