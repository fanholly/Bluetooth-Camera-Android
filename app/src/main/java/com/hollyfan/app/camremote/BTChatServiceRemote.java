package com.hollyfan.app.camremote;

/**
 * Created by hollyfan on 11/29/15.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Based on BluetoothChatService from Bluetooth Chat Android sample.
 * Modified to fit bluetooth client behavior.
 *
 */

public class BTChatServiceRemote {
    // Debugging
    private static final String TAG = "CamRemote";
    private static final String TAG2 = "BTChatServiceRemote: ";
    

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private Handler mRemoteActivityHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public static boolean readInputStream = true;


    // for the run thread
    private int testN = 0;
    private String testMsg;
    byte[] buffer = new byte[1024];
    int bytesRead;
    int receivedBytes = 0;
    int counter = 0;
    int frameSize;
    byte[] flagHeader = new byte[4];
    ByteBuffer flagHeaderBuffer = ByteBuffer.allocate(4);
    byte[] frameHeader = new byte[4];
    ByteBuffer frameHeaderBuffer = ByteBuffer.allocate(4);
    byte[] picHeader = new byte[4];
    ByteBuffer picHeaderBuffer = ByteBuffer.allocate(4);

    int lastBytes;
    int dataType;

    int frameHeaderRead;
    int flagHeaderRead;
    int flagHeaderReadCount;
    int frameHeaderReadCount;


    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BTChatServiceRemote(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    // Allows RemoteActivity to use its own handler
    public void addHandler (Handler newHandler) {
        mRemoteActivityHandler = newHandler;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d(TAG, TAG2 + "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, TAG2 + "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, TAG2 + "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
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
        Log.d(TAG, TAG2 + "stopping");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
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
        //Log.i(TAG, TAG2 + "Sending message to Camera");

    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device. Please try again.");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    public void connectionLost() {
        // Send a failure message back to the Activity
        // Instead of starting the service over to restart listening mode, tell client to connect again.
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost. Please try to connect again");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        //mRemoteActivityHandler.obtainMessage(Constants.CONNECTION_LOST).sendToTarget();

    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                    tmp = device.createRfcommSocketToServiceRecord(
                            MY_UUID_SECURE);
            } catch (IOException e) {
                Log.e(TAG, TAG2 + "create() failed", e);
                Logger.e(TAG, TAG2 + "create() failed", e);

            }
            mmSocket = tmp;
        }

        public void run() {
            Logger.e(TAG, TAG2 + "BEGIN mConnectThread");
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
                Logger.e(TAG, TAG2 + "Trying to connect");
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                    Log.e(TAG, TAG2 + "closing mmSocket");
                    Logger.e(TAG, TAG2 + "closing mmSocket");
                } catch (IOException e2) {
                    Log.e(TAG, TAG2 + "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                    Logger.e(TAG, TAG2 + "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                Log.e(TAG, TAG2 + "connection failed: " + e.getMessage());
                Logger.e(TAG, TAG2 + "connection failed: " + e.getMessage());

                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BTChatServiceRemote.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, TAG2 + "close() of connect " + mSocketType + " socket failed", e);
                Logger.e(TAG, TAG2 + "close() of connect " + mSocketType + " socket failed", e);

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
                Log.e(TAG, TAG2 + "temp sockets not created", e);
                Logger.e(TAG, TAG2 + "temp sockets not created", e);

            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.i(TAG, TAG2 + "BEGIN mConnectedThread");
            // TODO: Figure out byte array allocation for images.

            // Keep listening to the InputStream while connected
            while (true) {
                if (readInputStream) {

                    /*
                    try {
                        Log.d(TAG, TAG2 + "available in input stream" + mmInStream.available());
                    } catch (IOException e){

                    }
                    */
                    try {
                        //Log.d(TAG, TAG2 + "Start to read the bluetooth input stream");
                        // Head the packet header
                        // Bluetooth blocks at mmInstream.read()
                        flagHeaderRead = mmInStream.read(flagHeader, 0, 4);
                        if (flagHeaderRead <4 ){
                            Log.d(TAG, TAG2 + "Flag header bytes read: " + flagHeaderRead);
                            Logger.e(TAG, TAG2 + "Flag header bytes < 4: " + flagHeaderRead);
                            flagHeaderReadCount = flagHeaderRead;
                            flagHeaderBuffer.put(flagHeader, 0, flagHeaderRead);
                            while(flagHeaderReadCount < 4){
                                Log.d(TAG, TAG2 + "Entering recursive loop to recover bytes");
                                Logger.e(TAG, TAG2 + "Entering recursive loop to recover bytes");
                                flagHeaderRead = mmInStream.read(flagHeader, 0, 4-flagHeaderReadCount);
                                Log.d(TAG, TAG2 + "Flag header bytes read: " + flagHeaderRead);
                                Logger.e(TAG, TAG2 + "Flag header bytes read: " + flagHeaderRead);
                                flagHeaderBuffer.put(flagHeader, 0, flagHeaderRead);
                                flagHeaderReadCount = flagHeaderReadCount + flagHeaderRead;
                            }
                            dataType = flagHeaderBuffer.getInt(0);
                            Log.d(TAG, TAG2 + "Receiving (recovered) type of " + dataType);
                            Logger.e(TAG, TAG2 + "Receiving (recovered) type of " + dataType);
                            flagHeaderBuffer.clear();
                        } else {
                            flagHeaderBuffer.put(flagHeader);
                            dataType = flagHeaderBuffer.getInt(0);
                            Log.d(TAG, TAG2 + "Receiving type of " + dataType);
                            flagHeaderBuffer.clear();
                        }


                        if (dataType == Constants.PREVIEW_DATA) {
                            //Log to check if frame was received
                            testN++;
                            testMsg = "Frame" + Integer.toString(testN);
                            Log.d(TAG, TAG2 + testMsg + " received");
                            // then read the header
                            frameHeaderRead = mmInStream.read(frameHeader, 0, 4);
                            if (frameHeaderRead < 4){
                                Log.d (TAG, TAG2 + "Frame header read is " + Integer.toString(frameHeaderRead));
                                Logger.e (TAG, TAG2 + "Frame header read < 4: " + Integer.toString(frameHeaderRead));
                                frameHeaderReadCount = frameHeaderRead;
                                frameHeaderBuffer.put(frameHeader, 0, frameHeaderRead);
                                while (frameHeaderReadCount < 4){
                                    Log.d(TAG, TAG2 + "Entering recursive loop to recover bytes");
                                    Logger.e(TAG, TAG2 + "Entering recursive loop to recover bytes");
                                    frameHeaderRead = mmInStream.read(frameHeader, 0, 4-frameHeaderReadCount);
                                    Log.d (TAG, TAG2 + "Frame header read is " + frameHeaderRead);
                                    Logger.e(TAG, TAG2 + "Frame header read is " + frameHeaderRead);
                                    frameHeaderBuffer.put(frameHeader, 0, frameHeaderRead);
                                    frameHeaderReadCount = frameHeaderReadCount + frameHeaderRead;
                                }
                                frameSize = frameHeaderBuffer.getInt(0);
                                Log.d(TAG, TAG2 + "Receiving (recovered) frame size of " + frameSize);
                                Logger.e(TAG, TAG2 + "Receiving (recovered) frame size of " + frameSize);
                                frameHeaderBuffer.clear();
                            } else {
                                frameHeaderBuffer.put(frameHeader);
                                frameSize = frameHeaderBuffer.getInt(0);
                                frameHeaderBuffer.clear();
                                Log.d(TAG, TAG2 + "The frame size is " + frameSize);
                            }

                            // Read from the InputStream
                            // Reading the byte[] of frame from camera
                    /*
                    Equivalent to read(buffer, 0, buffer.length).
                    Reads up to byteCount bytes from this stream and stores them in the byte array buffer starting at byteOffset.
                    Returns the number of bytes actually read or -1 if the end of the stream has been reached.
                    */
                            ByteArrayOutputStream out = new ByteArrayOutputStream();

                            // Start by reading the first block of frame data from input stream

                            while (true) {
                                lastBytes = frameSize - receivedBytes;
                                //Log.d(TAG, TAG2 + "Iteration " + Integer.toString(counter));
                                if (lastBytes < buffer.length) {
                                    while (out.size() < frameSize) {
                                        //Log.d(TAG, TAG2 + "lastBytes are " + Integer.toString(lastBytes));
                                        lastBytes = frameSize - receivedBytes;
                                        //Log.d(TAG, TAG2 + "lastBytes are " + Integer.toString(lastBytes));
                                        bytesRead = mmInStream.read(buffer, 0, lastBytes);
                                        //Log.d(TAG, TAG2 + "Bytes read froyyyyym mmInstream " + Integer.toString(bytesRead));
                                        receivedBytes = receivedBytes + bytesRead;
                                        out.write(buffer, 0, bytesRead);
                                        //Log.d(TAG, TAG2 + "Size of out buffer is " + Integer.toString(out.size()));
                                    }
                                    break;
                                } else {
                                    bytesRead = mmInStream.read(buffer);
                                    //Log.d(TAG, TAG2 + "Bytes read from mmInstream " + Integer.toString(bytesRead));
                                    out.write(buffer, 0, bytesRead);
                                    receivedBytes = receivedBytes + bytesRead;
                                    //Log.d(TAG, TAG2 + "Size of out: " + Integer.toString(out.size()));
                                    //Log.d(TAG, TAG2 + "receivedBytes: " + Integer.toString(receivedBytes));

                                    counter++;
                                }
                            }


                            //Log.d(TAG, TAG2 + "End of iterations");
                            //header.clear();
                            receivedBytes = 0;

                            //Log.d(TAG, TAG2 + "Sending to handler");
                            // Send the obtained bytes to the UI Activity (RemoteActvity)
                            mRemoteActivityHandler.obtainMessage(Constants.MESSAGE_READ, -1, -1, out.toByteArray()).sendToTarget();

                            // Log to check if message received
                            //Log.d(TAG, TAG2 + "Receiving frame data from camera");


                        } else if (dataType >= Constants.PICTURE_DATA && dataType < 9) {
                            //mRemoteActivityHandler.obtainMessage(Constants.STOP_RECEIVING_PREVIEW).sendToTarget();

                            //header.clear();

                            // receive the photo and save it
                            //Log.d(TAG, TAG2 + "Start to read the bluetooth input stream");
                            // Head the packet header
                            mmInStream.read(picHeader, 0, 4);
                            picHeaderBuffer.put(picHeader);
                            frameSize = picHeaderBuffer.getInt(0);
                            picHeaderBuffer.clear();
                            Log.d(TAG, TAG2 + "The picture size is " + Integer.toString(frameSize));

                            // Read from the InputStream
                            // Reading the byte[] of frame from camera
                    /*
                    Equivalent to read(buffer, 0, buffer.length).
                    Reads up to byteCount bytes from this stream and stores them in the byte array buffer starting at byteOffset.
                    Returns the number of bytes actually read or -1 if the end of the stream has been reached.
                    */
                            ByteArrayOutputStream out = new ByteArrayOutputStream();

                            // Start by reading the first block of frame data from input stream

                            while (true) {
                                lastBytes = frameSize - receivedBytes;
                                //Log.d(TAG, TAG2 + "Receiving picture data from camera");
                                if (lastBytes < buffer.length) {
                                    while (out.size() < frameSize) {
                                        //Log.d(TAG, TAG2 + "lastBytes are " + Integer.toString(lastBytes));
                                        lastBytes = frameSize - receivedBytes;
                                        //Log.d(TAG, TAG2 + "lastBytes are " + Integer.toString(lastBytes));
                                        bytesRead = mmInStream.read(buffer, 0, lastBytes);
                                        //Log.d(TAG, TAG2 + "Bytes read from mmInstream " + Integer.toString(bytesRead));
                                        receivedBytes = receivedBytes + bytesRead;
                                        out.write(buffer, 0, bytesRead);
                                        //Log.d(TAG, TAG2 + "Size of out buffer is " + Integer.toString(out.size()));
                                    }
                                    break;
                                } else {
                                    bytesRead = mmInStream.read(buffer);
                                    //Log.d(TAG, TAG2 + "Bytes read from mmInstream " + Integer.toString(bytesRead));
                                    out.write(buffer, 0, bytesRead);
                                    receivedBytes = receivedBytes + bytesRead;
                                    //Log.d(TAG, TAG2 + "Size of out: " + Integer.toString(out.size()));
                                    //Log.d(TAG, TAG2 + "receivedBytes: " + Integer.toString(receivedBytes));
                                }
                            }


                            //Log.d(TAG, TAG2 + "End of iteration");
                            //header.clear();
                            receivedBytes = 0;


                            // Setting the exif for the JPEG
                            RemoteActivity.exif = dataType - Constants.PICTURE_DATA;
                            //Log.i(TAG, TAG2 + "EXIF is set: " + RemoteActivity.exif);
                            //Log.i(TAG, TAG2 + "Size of out: " + Integer.toString(out.size()));
                            //Log.i(TAG, TAG2 + "Sending picture data to handler");
                            // Send the obtained bytes to the UI Activity (RemoteActvity)
                            mRemoteActivityHandler.obtainMessage(Constants.PICTURE_RECEIVED, -1, -1, out.toByteArray()).sendToTarget();

                            // Log to check if message received
                            //Log.d(TAG, TAG2 + "Receiving messages from camera")


                        } else {
                            // data type is neither preview data or picture data
                            // something wrong with the sequence
                            Logger.e(TAG, TAG2 + "Received erroneous flag");
                            stop();
                            connectionLost();

                            /*
                            Log.d(TAG, TAG2 + "Data type is erroneous");
                            readInputStream = false;
                            mRemoteActivityHandler.obtainMessage(Constants.ERROR_RECEIVING_FRAMES).sendToTarget();

                            try {
                                Log.d(TAG, TAG2 + "available in input stream" + mmInStream.available());
                            } catch (IOException e){

                            }
                            */

                        }
                    } catch (IOException e) {
                        Log.e(TAG, TAG2 + "disconnected", e);
                        //Logger.e(TAG, TAG2 + "disconnected", e);
                        connectionLost();
                        break;
                    }
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
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, TAG2 + "Exception during write", e);
                Logger.e(TAG, TAG2 + "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, TAG2 + "close() of connect socket failed", e);
                Logger.e(TAG, TAG2 + "close() of connect socket failed", e);

            }
        }
    }
}




