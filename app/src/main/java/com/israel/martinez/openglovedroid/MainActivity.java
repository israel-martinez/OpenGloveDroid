package com.israel.martinez.openglovedroid;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.israel.martinez.openglovedroid.OpenGloveJavaAPI.MessageGenerator;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_ENABLE_BT = 1;
    private final int MESSAGE_READ = 200;

    private final int DEACTIVATE_MOTOR = 0;
    private final int ACTIVATE_MOTOR = 1;
    private static final int ADD_FLEXOR = 10;
    private static final int REMOVE_FLEXOR =  11;
    private static final int CALIBRATE_FLEXORS = 12;
    private static final int SET_THRESHOLD = 13;
    private static final int RESET_FLEXORS = 14;

    private static final int UPDATE_FLEXOR_VALUE = 100;


    private final String MY_UUID = "1e966f42-52a8-45db-9735-5db0e21b881d";
    private final String NAME = "isrAndroidBluetooth";
    private String mInitializeMotor = "1,1,11s";
    private String mActivateMotor = "2,1,-1s";
    private String mPinMode = "6,1,11,1s";

    // Vibe board: +11 y -12
    ArrayList<Integer> mPins  = new ArrayList<>(
            Arrays.asList(11, 12));
    ArrayList<String> mValuesON  = new ArrayList<>(
            Arrays.asList("HIGH", "LOW"));
    ArrayList<String> mValuesOFF  = new ArrayList<>(
            Arrays.asList("LOW", "LOW"));

    // Flexor pins: 17 and  + and -
    ArrayList<Integer> mFlexorPins = new ArrayList<>(
            Arrays.asList(17));
    ArrayList<Integer> mFlexorMapping = new ArrayList<>(
            Arrays.asList(8));
    ArrayList<String> mFlexorPinsMode = new ArrayList<>(
            Arrays.asList("OUTPUT"));

    private BluetoothAdapter mBluetoothAdapter;
    private ArrayList<String> mDeviceList = new ArrayList<>();
    private ArrayList<String> mDeviceNamesList = new ArrayList<>();
    private ArrayAdapter<String> mArrayAdapter;

    private ListView mListViewDevices;
    private EditText mEditTextDeviceName;
    private TextView mTextViewFlexor;
    private ProgressBar mProgressBar;
    private int mMinProgress = 0;
    private int mMaxProgress = 270;

    private BluetoothDevice mBluetoothDevice;

    private Handler mUIHandler;
    private Handler mHandlerConnectedThread;

    private MessageGenerator messageGenerator = new MessageGenerator();

    private int mState = DEACTIVATE_MOTOR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEditTextDeviceName = findViewById(R.id.edit_text_device_name);
        mTextViewFlexor = findViewById(R.id.text_view_flexor);
        mProgressBar = findViewById(R.id.progress_bar_flexor);
        mProgressBar.setMax(mMaxProgress);

        mListViewDevices = findViewById(R.id.list_view_devices);
        mListViewDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mEditTextDeviceName.setText(mDeviceNamesList.get(position));
            }
        });

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplication(), "This device no support Bluetooth", Toast.LENGTH_SHORT).show();
        }
        else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }

            if (mBluetoothAdapter.isEnabled()) {
                //searchAndDisplayBluetoothDevices();
            }
        }

        /* Handler for send data from threads to UI */
        mUIHandler = new Handler(Looper.getMainLooper()) {
            /*
             * handleMessage() defines the operations to perform when
             * the Handler receives a new Message to process.
             */
            @Override
            public void handleMessage(Message inputMessage) {
                // Gets the image task from the incoming Message object.
                //PhotoTask photoTask = (PhotoTask) inputMessage.obj;
                //TODO get data from threads
                Log.e("UI_THREAD", "Receiving message on UI thread");
                switch (inputMessage.what) {
                    case UPDATE_FLEXOR_VALUE: {
                        mProgressBar.setProgress((int) inputMessage.obj);
                        String flexorValue = "Flexor value: " + Integer.toString((int) inputMessage.obj);
                        mTextViewFlexor.setText(flexorValue);
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
        };
    }

    public void connectDevice(View view) {
        mDeviceList.clear();
        mDeviceNamesList.clear();
        searchAndDisplayBluetoothDevices();
    }

    public void sendMessage(View view){
        if(mState == DEACTIVATE_MOTOR) {
            mState = ACTIVATE_MOTOR;
            ((Button) view).setText(R.string.button_motor_ON);
        } else {
            mState = DEACTIVATE_MOTOR;
            ((Button) view).setText(R.string.button_motor_OFF);
        }
        Message completeMessage =
                mHandlerConnectedThread.obtainMessage(mState);//in this case only need send the state ON/OFF
        completeMessage.sendToTarget();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void searchAndDisplayBluetoothDevices(){
        Toast.makeText(getApplication(), "This device Support Bluetooth, searching Bluetooth devices", Toast.LENGTH_SHORT).show();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mDeviceList.add(device.getName() + "\n" + device.getAddress());
                mDeviceNamesList.add(device.getName());

                if (device.getName().matches(mEditTextDeviceName.getText().toString())) {
                    mBluetoothDevice = device;
                    if(BluetoothDevice.DEVICE_TYPE_LE == device.getType()){
                        Toast.makeText(this, device.getName() + " Is a Bluetooth LE device " , Toast.LENGTH_SHORT).show();
                    }
                    Toast.makeText(this, "Trying connecting to " + device.getName(), Toast.LENGTH_SHORT).show();
                    //bluetoothAdapter.cancelDiscovery();
                    ConnectThread connectThread = new ConnectThread(mBluetoothDevice);
                    connectThread.start();
                }

            }
            mArrayAdapter = new ArrayAdapter<>(this,R.layout.simple_item, R.id.text_view_item, mDeviceList);

            mListViewDevices.setAdapter(mArrayAdapter);
        }
    }

    /*
        The next class is for server side bluetooth connections
    */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, UUID.fromString(MY_UUID));
            } catch (IOException e) { }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // If a connection was accepted
                if (socket != null) {
                    // Do work to manage the connection (in a separate thread)
                    //TODO  manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) { }
        }
    }

    /*
        The next class is for client side bluetooth connections
    */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                //tmp = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID));
                /*
                    Issue solved https://stackoverflow.com/questions/18657427/ioexception-read-failed-socket-might-closed-bluetooth-on-android-4-3/18786701
                    and change target api 15 to 19 (kitkat)
                */
                tmp = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);



                Log.e("CREATED", "BluetoothSocket!!! SUCCESS");

            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            mmSocket = tmp;

            if(mmSocket == null) Log.e("DONT CREATED", "BluetoothSocket!!! FAILED");
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception

                mmSocket.connect();
                Log.e("CONNECTED", "Connect device to BluetoothSocket!!! SUCCESS");
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.e("ConnectException", connectException.getMessage());
                try {
                    mmSocket.close();
                    Log.e("DONT_CONNECTED", "Connect device to BluetoothSocket!!! FAILED");
                } catch (IOException closeException) {
                    Log.e("CloseException", closeException.getMessage());
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            // TODO manageConnectedSocket(mmSocket);
            ConnectedThread connectedThread = new ConnectedThread(mmSocket);
            connectedThread.start();
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    /*
        The next class is for manage the current connected thread
    */

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private BufferedReader mmBufferedReader;
        private Handler mHandler;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mmBufferedReader = new BufferedReader(new InputStreamReader(mmInStream));

            /* Handler for receiving the actions from UI interactions */
            mHandler = new Handler(Looper.getMainLooper()) {
                /*
                 * handleMessage() defines the operations to perform when
                 * the Handler receives a new Message to process.
                 */
                @Override
                public void handleMessage(Message inputMessage) {
                    // Gets the image task from the incoming Message object.
                    //PhotoTask photoTask = (PhotoTask) inputMessage.obj;
                    //TODO get data from threads
                    Log.e("CONNECTED_THREAD", "Receiving message from UI thread");

                    switch (inputMessage.what) {
                        case DEACTIVATE_MOTOR: {
                            String message = messageGenerator.activateMotor(mPins, mValuesOFF);
                            try {
                                mmOutStream.write(message.getBytes());
                            } catch (IOException e) { }
                            break;
                        }
                        case ACTIVATE_MOTOR: {
                            String message = messageGenerator.activateMotor(mPins, mValuesON);
                            try {
                                mmOutStream.write(message.getBytes());
                            } catch (IOException e) { }
                            break;
                        }
                        case ADD_FLEXOR: {
                            String message = messageGenerator.addFlexor(mFlexorPins.get(0), mFlexorMapping.get(0));
                            try {
                                mmOutStream.write(message.getBytes());
                            } catch (IOException e) { }
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                }
            };
            mHandlerConnectedThread = mHandler;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            Log.e("CONNECTED_THREAD", "Start management thread connected");

            try {
                Log.e("INITIALIZING_MOTOR", "Trying initializing motors");

                String message = messageGenerator.initializeMotor(mPins);
                this.write(message.getBytes());

                //message = messageGenerator.pinMode(mFlexorPins, mFlexorPinsMode);
                //this.write(message.getBytes());

                message = messageGenerator.setThreshold(0);
                this.write(message.getBytes());

            } catch (Error e) {
                Log.e("FAIL_INITIALIZE_MOTOR", "Fail to initialize the motor");
            }

            // Keep listening to the InputStream until an exception occurs
            String line;
            Message message;
            while (true) {
                try {
                    // Read from the InputStream
                    //TODO capture message from the flexor
                    line = analogRead(17);
                    if(line != null) Log.e("BUFFER: ", line);
                    //TODO send message data to UI
                    message = mUIHandler.obtainMessage(UPDATE_FLEXOR_VALUE, Integer.parseInt(line));
                    message.sendToTarget();


                } catch (Exception e) {
                    Log.e("Exception","Error in try read/write loop");
                    e.printStackTrace();
                    break;
                }
            }
        }

        public String analogRead(int pin){
            String message = messageGenerator.analogRead(pin);
            this.write(message.getBytes());

            try {
                return mmBufferedReader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

}
