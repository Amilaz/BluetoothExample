package com.amilaz.bluetoothpaymentexample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT_FOR_RECEIVE = 1;
    private static final int REQUEST_ENABLE_BT_FOR_PAY = 2;

    // Normal View
    private Button mBtnReceive;
    private Button mBtnPay;
    private Button mBtnCancel;
    private Button mBtnYes;
    private EditText mEditMoney;
    private EditText mEditPin;
    private TextView mTvPaymentInfo;
    private TextView mTvEmptyView;
    private RelativeLayout mLoadingView;
    private RelativeLayout mConfirmView;

    // Bluetooth scan
    private BluetoothAdapter mBtAdapter = null;
    private BluetoothChatService mChatService = null;

    private String mDeviceName = null;
    private String mDeviceAddress = null;
    private boolean mFoundDevice = false;
    private double mMoneyInput;
    private boolean mSendAction = false;

    private long time;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initInstance();
        initBluetoothAdapter();
        setReceiveListener();
        setPayListener();
        setCancelListener();
        setYesListener();
    }

    private void initInstance() {
        mBtnReceive = (Button) findViewById(R.id.btn_receive);
        mBtnPay = (Button) findViewById(R.id.btn_pay);
        mBtnCancel = (Button) findViewById(R.id.btn_cancel);
        mBtnYes = (Button) findViewById(R.id.btn_yes);
        mEditMoney = (EditText) findViewById(R.id.ed_money);
        mEditPin = (EditText) findViewById(R.id.ed_pin);
        mTvPaymentInfo = (TextView) findViewById(R.id.tv_payment_info);
        mTvEmptyView = (TextView) findViewById(R.id.tv_empty);
        mLoadingView = (RelativeLayout) findViewById(R.id.loading_view);
        mConfirmView = (RelativeLayout) findViewById(R.id.confirm_view);
    }

    private void initBluetoothAdapter() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    }


    private void setReceiveListener() {
        mBtnReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    mMoneyInput = Double.valueOf(mEditMoney.getText().toString());
                } catch (NumberFormatException er) {
                    mMoneyInput = 0;
                }
                Log.d(TAG, Double.toString(mMoneyInput));
                if(mMoneyInput > 0){
                    mFoundDevice = false;
                    mSendAction = true;
                    initBluetoothAdapter();
                    if (!mBtAdapter.isEnabled()) {
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableIntent, REQUEST_ENABLE_BT_FOR_RECEIVE);
                    } else {
                        receiveListener();
                    }
                } else {
                    showInvalidInputDialog("Input Error", "Please enter money.");
                    MainActivity.this.unregisterReceiver(mReceiver);
                }
            }
        });
    }

    private void receiveListener() {
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
            mLoadingView.setVisibility(View.GONE);
            mTvEmptyView.setVisibility(View.VISIBLE);
            mTvEmptyView.setText("Bluetooth scan was cancel.");
        } else {
            mTvEmptyView.setVisibility(View.GONE);
            mLoadingView.setVisibility(View.VISIBLE);
            time = System.currentTimeMillis();
            mBtAdapter.startDiscovery();
        }
    }

    private void showInvalidInputDialog(String title, String message){
        AlertDialog popup = new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .setCancelable(true)
                .create();
        popup.show();
    }

    private void setPayListener() {
        mBtnPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mBtAdapter.isEnabled()) {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT_FOR_PAY);
                } else {
                    mSendAction = false;
                    mTvEmptyView.setVisibility(View.GONE);
                    mLoadingView.setVisibility(View.VISIBLE);
                    ensureDiscoverable();
                    connectedDevice();
                }
            }
        });
    }

    private void ensureDiscoverable() {
        if (mBtAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
            startActivity(discoverableIntent);
        }
    }

    private void setCancelListener() {
        mBtnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
                    Toast.makeText(MainActivity.this, "Not connected please try again", Toast.LENGTH_SHORT).show();
                    //return;
                } else {
                    mConfirmView.setVisibility(View.GONE);
                    mTvEmptyView.setVisibility(View.VISIBLE);
                    mTvEmptyView.setText("Payment was canceled");
                    sendMessage("ActionSendBack false");
                    closeBluetooth();
                }

            }
        });
    }

    private void setYesListener() {
        mBtnYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Check that we're actually connected before trying anything
                if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
                    Toast.makeText(MainActivity.this, "Not connected please try again", Toast.LENGTH_SHORT).show();
                    //return;
                } else {
                    if(mEditPin.getText().toString().equals("111111")){
                        mConfirmView.setVisibility(View.GONE);
                        mTvEmptyView.setVisibility(View.VISIBLE);
                        mTvEmptyView.setText("Payment successful");
                        sendMessage("ActionSendBack true");
                        closeBluetooth();
                    } else {
                        showInvalidInputDialog("Invalid Pin", "Pin is invalid please re-enter your pin");
                    }
                }
            }
        });
    }

    private void connectDevice(String deviceAddress) {
        mBtAdapter = null;
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        // Get the BluetoothDevice object
        mChatService = new BluetoothChatService(this , mHandler);
        BluetoothDevice device = mBtAdapter.getRemoteDevice(deviceAddress);
        mChatService.start();
        // Attempt to connect to the device
        mChatService.connect(device, false);
    }


    private void connectedDevice() {
        // Get the BluetoothDevice object
        mChatService = new BluetoothChatService(this , mHandler);
        // Attempt to connect to the device
        mChatService.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT_FOR_RECEIVE:
                if (resultCode == Activity.RESULT_OK) {
                    receiveListener();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "You must enable bluetooth to do payment",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_ENABLE_BT_FOR_PAY:
                if (resultCode == Activity.RESULT_OK) {
                    ensureDiscoverable();
                    connectedDevice();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "You must enable bluetooth to do payment",
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);
                long timeTemp = System.currentTimeMillis() - time;
                time = System.currentTimeMillis();
                Log.d("Range RSSI", rssi + "dBm with time " + timeTemp);
                if(rssi > -40){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mBtAdapter.cancelDiscovery();
                    mDeviceName = device.getName();
                    mFoundDevice = true;
                    connectDevice(device.getAddress());
                    Log.d(TAG, "Near with " + device.getName());
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if(!mFoundDevice){
                    mBtAdapter.startDiscovery();
                    Log.d(TAG, "Continue scan");
                } else {
                    Log.d(TAG, "Scan done");
                }
            }
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = MainActivity.this;
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            if(mDeviceName != null){
                                Log.d(TAG, "Connected to " + mDeviceName);
                                if(mSendAction){
                                    MainActivity.this.sendMessage("ActionSend " + Double.toString(mMoneyInput));
                                }
                                Toast.makeText(activity, "Connected to " + mDeviceName,
                                        Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            if(mDeviceName != null){
                                Log.d(TAG, "Connecting to " + mDeviceName);
                                Toast.makeText(activity, "Connecting to " + mDeviceName,
                                        Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                            //mFoundDevice = false;
                            //scanBluetooth();
                            Log.d(TAG, "Listen");
                            Toast.makeText(activity, "Listen",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothChatService.STATE_NONE:
                            Log.d(TAG, "Not connected");
                            Toast.makeText(activity, "Not connected",
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    Log.d(TAG, writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    receiveMessage(readMessage);
                    Log.d(TAG, readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                        /*if (mChatService != null) {
                            // Only if the state is STATE_NONE, do we know that we haven't started already
                                // Start the Bluetooth chat services
                            if(msg.getData().getString(Constants.TOAST).equals("Device connection was lost")){
                                mChatService.stop();
                            }
                        }*/
                    }
                    break;
            }
        }
    };

    private void sendMessage(String message) {
        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);
            Log.d(TAG + "Send",send.toString());
            Toast.makeText(MainActivity.this, send.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void receiveMessage(String message){
        Log.d(TAG + "Receive", message);
        String[] temp = message.split(" ");
        if(temp[0].equals("ActionSend")){
            mLoadingView.setVisibility(View.GONE);
            mConfirmView.setVisibility(View.VISIBLE);
            mTvPaymentInfo.setText("You want to pay " + temp[1] + "bath");
        } else if (temp[0].equals("ActionSendBack")) {
            mLoadingView.setVisibility(View.GONE);
            mTvEmptyView.setVisibility(View.VISIBLE);
            if(temp[1].equals("true")){
                mTvEmptyView.setText("Payment successful");
            } else {
                mTvEmptyView.setText("Payment was canceled");
            }
            closeBluetooth();
        }

    }

    private void closeBluetooth(){
        if (mChatService != null) {
            mChatService.stop();
        }
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
            mBtAdapter.disable();
            mBtAdapter = null;
        }
        this.unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeBluetooth();
    }

}
