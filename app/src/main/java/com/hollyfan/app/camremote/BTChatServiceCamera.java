package com.hollyfan.app.camremote;

/**
 * Created by hollyfan on 11/29/15.
 *
 * Based on BluetoothChatService from Bluetooth Chat Android sample.
 * Modified to fit Bluetooth server behavior.
 *
 * This class is used on the phone selected to be "Camera" and brokers the BT connection
 * with phone selected to be "Remote".
 *
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

public class BTChatServiceCamera {
    // For debugging
    private static final String TAG = "CamRemote";
    private static final String TAG2 = "BTChatServiceCamera: ";

    // Name for SDP record when creating server socket
    private static final String NAME_SECURE = "Bluetooth Camera";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private Handler mCameraActivityHandler;
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;
    public int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTED = 2;  // now connected to a remote phone


    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */

    public BTChatServiceCamera(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    // Allows RemoteActivity to use its own handler
    public void addHandler (Handler newHandler) {
        mCameraActivityHandler = newHandler;
    }


    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */

    private synchronized void setState(int state) {
        Log.d(TAG, TAG2 + "setState() " + mState + " -> " + state);
        mState = state;

        // Update the UI Activity with the state
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }


    /**
     * Start the Bluetooth Chat Server for the phone selected as "Camera."
     * Specifically, start the AcceptThread to begin a session in listening on server mode.
     */
    public synchronized void begin() {
        Log.d(TAG, TAG2 + "begin");

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // When mChatService first starts it will set state from STATE_NONE to STATE_LISTEN (0->1)
        // meaning AcceptThread starting
        setState(STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        // this starts AcceptThread which logs Socket Type: SecureBEGIN -- only offer Secure option
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread(); // new AcceptThread
            mAcceptThread.start();
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *  @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device) {
        Log.d(TAG, TAG2 + "connected");

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, TAG2 + "stop");

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }


    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    public void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        //mCameraActivityHandler.obtainMessage(Constants.CONNECTION_LOST).sendToTarget();


        // Start the service over to restart listening mode
        //BTChatServiceCamera.this.begin();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                            MY_UUID_SECURE);
            } catch (IOException e) {
                Log.e(TAG, TAG2 + "listen() failed", e);
                Logger.e(TAG, TAG2 + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            // AcceptThread starts running after mChatService is started
            Logger.e(TAG, TAG2 + "BEGIN mAcceptThread");

            BluetoothSocket socket = null;

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                    Logger.e(TAG, TAG2 + "Trying to accept");
                } catch (IOException e) {
                    Log.e(TAG, TAG2 + "accept() failed", e);
                    Logger.e(TAG, TAG2 + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BTChatServiceCamera.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, TAG2+ "Could not close unwanted socket", e);
                                    Logger.e(TAG, TAG2+ "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, TAG2 + "END mAcceptThread");

        }

        public void cancel() {
            Log.d(TAG, TAG2 + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, TAG2 + "close() of server failed", e);
                Logger.e(TAG, TAG2 + "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     *
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, TAG2 + "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, TAG2+"temp sockets not created", e);
                Logger.e(TAG, TAG2+"temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, TAG2+"BEGIN mConnectedThread");
            byte[] messageReceived = new byte[4];
            ByteBuffer messageBuffer = ByteBuffer.allocate(4);
            int message;


            // Keep listening to the InputStream while connected (incoming messages from Remote)
            while (true) {
                try {
                    // Read from the InputStream
                    mmInStream.read(messageReceived, 0, 4);
                    messageBuffer.put(messageReceived);
                    message = messageBuffer.getInt(0);
                    Log.i(TAG, TAG2 + "Received message from Remote");

                    switch (message){
                        case Constants.FLASH_OFF:
                            Log.d(TAG, TAG2+"Told by Remote to turn off flash");
                            messageBuffer.clear();
                            mCameraActivityHandler.obtainMessage(Constants.FLASH_OFF).sendToTarget();
                            break;

                        case Constants.FLASH_ON:
                            Log.d(TAG, TAG2 + "Told by Remote to turn on flash");
                            messageBuffer.clear();
                            mCameraActivityHandler.obtainMessage(Constants.FLASH_ON).sendToTarget();
                            break;


                        case Constants.ERROR_RECEIVING_FRAMES:
                            Log.d(TAG, TAG2+"Told by Remote to flush stream due to error");
                            messageBuffer.clear();
                            CameraActivity.sendPreview = false;
                            CameraActivity.mCamera.stopPreview();
                            mmOutStream.flush();
                            //CameraActivity.mCamera.startPreview();
                            break;


                        case Constants.NEW_P_CALLBACK:
                            Log.d(TAG, TAG2+"Need to create a new CameraPreview due to pCallback not working");
                            messageBuffer.clear();
                            mCameraActivityHandler.obtainMessage(Constants.NEW_P_CALLBACK).sendToTarget();
                            break;


                        case Constants.TAKE_PICTURE_MESSAGE:
                            Log.d(TAG, TAG2+"Told by Remote to take pic");
                            messageBuffer.clear();


                            // Delay by two seconds
                            try {
                                Thread.sleep(2000);
                            } catch (Exception e){
                                Logger.e(TAG, TAG2 + "Thread sleep exception", e);

                            }

                            mCameraActivityHandler.obtainMessage(Constants.TAKE_PICTURE_MESSAGE).sendToTarget();
                            break;

                        // FOR AFTER PICTURE IS SAVED
                        case Constants.RESTART_PREVIEW:
                            messageBuffer.clear();
                            CameraActivity.sendPreview = true;
                            CameraPreview.sendPreviewSwitch = true;
                            Log.d(TAG, TAG2 + "Told by Remote to start sending preview frames again");
                            break;

                        // FOR RESUMING FROM PAUSE
                        case Constants.START_PREVIEW_AGAIN:
                            messageBuffer.clear();
                            CameraActivity.sendPreview = true;
                            CameraPreview.sendPreviewSwitch = true;
                            Log.d(TAG, TAG2 + "Told by Remote to start preview again after resuming");
                            mCameraActivityHandler.obtainMessage(Constants.START_PREVIEW_AGAIN).sendToTarget();
                            break;


                        case Constants.STOP_PREVIEW:
                            messageBuffer.clear();
                            CameraActivity.sendPreview = false;
                            /*
                            if (CameraActivity.mCamera != null) {
                                CameraActivity.mCamera.stopPreview();
                                Log.d(TAG, TAG2 + "Told by Remote to stop preview");
                            }
                            */
                            break;


                        default:
                            messageBuffer.clear();
                            Log.i(TAG, TAG2 + "Received dimensions from Remote");
                            if (CameraActivity.remoteWidth == 0) {
                                CameraActivity. remoteWidth = message;
                            } else {
                                CameraActivity. remoteHeight = message;
                            }
                            Log.d(TAG, TAG2 + "Remote dimensions are: " + CameraActivity.remoteWidth + "x" + CameraActivity.remoteHeight);
                            break;
                    }


                    /* won't need to do this if Camera
                    // Send the obtained bytes to the UI Activity

                    */

                } catch (IOException e) {
                    Log.e(TAG, TAG2+"disconnected", e);
                    //Logger.e(TAG, TAG2+"disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    //BTChatServiceCamera.this.begin();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                //Log.d(TAG, TAG2+"Starting to write");
                // This is the part that send the byte[] to the client.
                // Write the frame to output stream
                mmOutStream.write(buffer);

                /*
                // Share the sent message back to the UI Activity
                // TODO: Get rid of this part. Not necessary for camera to see the messages it's sending.
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
                */
            } catch (IOException e) {
                Log.e(TAG, TAG2+"Exception during write", e);
                Logger.e(TAG, TAG2+"Exception during write", e);

            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, TAG2+"close() of connect socket failed", e);
                Logger.e(TAG, TAG2+"close() of connect socket failed", e);
            }
        }
    }
}
