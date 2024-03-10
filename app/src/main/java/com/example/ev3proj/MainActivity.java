package com.example.ev3proj;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    // BT Variables
    private final String CV_ROBOTNAME = "POS";
    private BluetoothAdapter cv_btInterface = null;
    private Set<BluetoothDevice> cv_pairedDevices = null;
    private BluetoothDevice cv_btDevice = null;
    private BluetoothSocket cv_btSocket = null;

    // Data stream to/from NXT bluetooth
    private InputStream cv_is = null;
    private OutputStream cv_os = null;

    TextView statusText;
    boolean isConnected = false;
    boolean spinTurn = false;
    long lastDisconnect = 0;
    ImageButton rightButton;
    ImageButton leftButton;
    ImageButton forwardButton;
    ImageButton backwardButton;
    Button bluetooth_button;
    ImageButton clockwiseButton;
    ImageButton counterClockwiseButton;

    Switch spinSwitch;

    public void connectStatusUpdater() {
        forwardButton.setEnabled(isConnected);
        backwardButton.setEnabled(isConnected);
        leftButton.setEnabled(isConnected);
        rightButton.setEnabled(isConnected);
        clockwiseButton.setEnabled(isConnected);
        counterClockwiseButton.setEnabled(isConnected);
        if (isConnected) {
            bluetooth_button.setText("Disconnect");
            forwardButton.setAlpha(1f);
            backwardButton.setAlpha(1f);
            leftButton.setAlpha(1f);
            rightButton.setAlpha(1f);
            clockwiseButton.setAlpha(1f);
            counterClockwiseButton.setAlpha(1f);
        } else {
            bluetooth_button.setText("Connect");
            forwardButton.setAlpha(0.5f);
            backwardButton.setAlpha(0.5f);
            leftButton.setAlpha(0.5f);
            rightButton.setAlpha(0.5f);
            clockwiseButton.setAlpha(0.5f);
            counterClockwiseButton.setAlpha(0.5f);
        }
    }

    public void setSeekBarListener(SeekBar seekBar, TextView textView, String labelPrefix) {
        SeekBar.OnSeekBarChangeListener temp = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textView.setText(labelPrefix + seekBar.getProgress());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        seekBar.setOnSeekBarChangeListener(temp);
    }

    public void setButtonListenerMoveMotor(ImageButton button, int motorSel, SeekBar seekBar, int powerMultiple) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private Handler mHandler;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mHandler != null) return true;
                        mHandler = new Handler();
                        mHandler.post(mAction);
                        button.setScaleX((float) 0.8);
                        button.setScaleY((float) 0.8);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (mHandler == null) return true;
                        mHandler.removeCallbacks(mAction);

                        if(motorSel == 0x01) {
                            newMotor(0, 0x01);
                        } else {
                            newMotor(0, 0x06);
                        }

                        mHandler = null;
                        button.setScaleX((float) 1);
                        button.setScaleY((float) 1);
                        break;
                }
                return false;
            }

            private Runnable mAction = new Runnable() {
                @Override
                public void run() {
                    if(spinSwitch.isChecked()) {
                        switch(motorSel){
                            case 0x02:
                                newMotor(seekBar.getProgress(), 0x02);
                                newMotor(-1 * seekBar.getProgress(), 0x04);
                            break;
                            case 0x04:
                                newMotor(seekBar.getProgress(), 0x04);
                                newMotor(-1 * seekBar.getProgress(), 0x02);
                            break;
                            default:
                                newMotor(powerMultiple * seekBar.getProgress(), motorSel);
                            break;
                        }
                    } else {
                        newMotor(powerMultiple * seekBar.getProgress(), motorSel);
                    }

                    // Post the action again after a delay
                    mHandler.postDelayed(this, 100); // Adjust the delay as needed
                }
            };
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetooth_button = (Button) findViewById(R.id.bluetooth_button);
        SeekBar powerSlider = (SeekBar) findViewById(R.id.powerSlider);
        SeekBar rotationSlider = (SeekBar) findViewById(R.id.rortationSlider);
        statusText = (TextView) findViewById(R.id.textView);
        TextView powerLabel = (TextView) findViewById(R.id.powerLabel);
        TextView rotationLabel = (TextView) findViewById(R.id.rotationLabel);
        spinSwitch = (Switch) findViewById(R.id.SpinSwitch);

        rightButton = (ImageButton) findViewById(R.id.rightButton);
        leftButton = (ImageButton) findViewById(R.id.leftButton);
        forwardButton = (ImageButton) findViewById(R.id.forwardButton);
        backwardButton = (ImageButton) findViewById(R.id.backwardButton);
        clockwiseButton = (ImageButton) findViewById(R.id.clockwise);
        counterClockwiseButton = (ImageButton) findViewById(R.id.counterclockwise);

        connectStatusUpdater();

        bluetooth_button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (isConnected) {
                    cpf_disconnFromEV3(cv_btDevice);
                    isConnected = false;
                } else {
                    cpf_requestBTPermissions();
                    cv_btDevice = cpf_locateInPairedBTList(CV_ROBOTNAME);
                    isConnected = cpf_connectToEV3(cv_btDevice);
                }
                connectStatusUpdater();
            }
        });

        setSeekBarListener(powerSlider, powerLabel, "Power: ");
        setSeekBarListener(rotationSlider, rotationLabel, "Power: ");

        setButtonListenerMoveMotor(forwardButton, 0x06, powerSlider, 1);
        setButtonListenerMoveMotor(backwardButton, 0x06, powerSlider, -1);
        setButtonListenerMoveMotor(leftButton, 0x04, powerSlider, 1);
        setButtonListenerMoveMotor(rightButton, 0x02, powerSlider, 1);
        setButtonListenerMoveMotor(clockwiseButton, 0x01, rotationSlider, 1);
        setButtonListenerMoveMotor(counterClockwiseButton, 0x01, rotationSlider, -1);
    }

    private void cpf_requestBTPermissions() {
        // We can give any value but unique for each permission.
        final int BLUETOOTH_SCAN_CODE = 100;
        final int BLUETOOTH_CONNECT_CODE = 101;

        // Android version < 12, "android.permission.BLUETOOTH" just fine
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            statusText.setText("1");
//            Toast.makeText(MainActivity.this,
//                    "BLUETOOTH granted for earlier Android", Toast.LENGTH_SHORT).show();
            return;
        }

        // Android 12+ has to go through the process
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED) {
            statusText.setText("2");
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.BLUETOOTH_SCAN},
                    BLUETOOTH_SCAN_CODE);
        } else {
            statusText.setText("3");
//            Toast.makeText(MainActivity.this,
//                    "BLUETOOTH_SCAN already granted", Toast.LENGTH_SHORT).show();
        }

        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            statusText.setText("Try again");
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_CONNECT_CODE);
        } else {
            statusText.setText("5");
//            Toast.makeText(MainActivity.this,
//                    "BLUETOOTH_CONNECT already granted", Toast.LENGTH_SHORT).show();
        }
    }
    private BluetoothDevice cpf_locateInPairedBTList(String name) {
        BluetoothDevice lv_bd = null;
        try {
            cv_btInterface = BluetoothAdapter.getDefaultAdapter();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return null;
            }
            cv_pairedDevices = cv_btInterface.getBondedDevices();
            Iterator<BluetoothDevice> lv_it = cv_pairedDevices.iterator();
            while (lv_it.hasNext()) {
                lv_bd = lv_it.next();
                if (lv_bd.getName().equalsIgnoreCase(name)) {
                    statusText.setText(name + " is in paired list");
                    return lv_bd;
                }
            }
            statusText.setText(name + " is NOT in paired list");
        } catch (Exception e) {
            statusText.setText("Failed in findRobot() " + e.getMessage());
        }
        return null;
    }
    private boolean cpf_connectToEV3(BluetoothDevice bd) {
        if (System.currentTimeMillis() - lastDisconnect < 5000) { // 1 seconds
            Toast.makeText(MainActivity.this,"Please wait a bit before reconnecting", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return false;
            }
            cv_btSocket = bd.createRfcommSocketToServiceRecord
                    (UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            cv_btSocket.connect();

            //// HERE
            cv_is = cv_btSocket.getInputStream();
            cv_os = cv_btSocket.getOutputStream();
            statusText.setText("Connected to " + bd.getName() + " at " + bd.getAddress());
            return true;
        } catch (Exception e) {
            statusText.setText("Error interacting with remote device [" +
                    e.getMessage() + "]");
            return false;
        }
    }
    private void cpf_disconnFromEV3(BluetoothDevice bd) {
        lastDisconnect = System.currentTimeMillis();
        try {
            cv_btSocket.close();
            cv_is.close();
            cv_os.close();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            statusText.setText(bd.getName() + " is disconnect ");
        } catch (Exception e) {
            statusText.setText("Error in disconnect -> " + e.getMessage());
        }
    }

    // Communication Developer Kit Page 27
    // 4.2.2 Start motor B & C forward at power 50 for 3 rotation and braking at destination
    private void cpf_EV3MoveMotor(int inSpeed) {
        if (inSpeed > 100 || inSpeed < -100) {
            statusText.setText("Error in MoveForward(speed argument can only take -100 to 100 int range: " + inSpeed + " )");
            return;
        }
        try {
            byte[] buffer = new byte[20];       // 0x12 command length

            buffer[0] = (byte) (buffer.length - 2); // length of message: length of buffer minus the first two
            buffer[1] = 0;                          // length of message

            // message start
            // !!!! REMEMBER ABOUT BYTE SWAPPING
            buffer[2] = 34;
            buffer[3] = 12;

            buffer[4] = (byte) 0x80; // response: 00 -> reply, 80 -> no reply

            buffer[5] = 0; // parameters: none
            buffer[6] = 0; // parameters: none

            buffer[7] = (byte) 0xae; // opcode [ see 'LEGO_MINDSTORMS_EV3_Firmware_Developer_Kit-1.pdf' ]
            buffer[8] = 0;           // function for opcode, might not be a thing

            buffer[9] = (byte) 0x06; // MOTOR SELECTION [ NOS in docs, see ev3 in Documents folder ]

            buffer[10] = (byte) 0x81;
            buffer[11] = (byte) inSpeed; // speed

            buffer[12] = 0;

            buffer[13] = (byte) 0x82;
            buffer[14] = (byte) 0x84;
            buffer[15] = (byte) 0x01; // times turned

            buffer[16] = (byte) 0x82;
            buffer[17] = (byte) 0xB4;
            buffer[18] = (byte) 0x00;

            buffer[19] = 1;

            cv_os.write(buffer);
            cv_os.flush();
        }
        catch (Exception e) {
            statusText.setText("Error in MoveForward(" + e.getMessage() + ")");
        }
    }

    private void newMotor(int inSpeed, int motorsel) {
        if (inSpeed > 100 || inSpeed < -100) {
            statusText.setText("Error in MoveForward(speed argument can only take -100 to 100 int range: " + inSpeed + " )");
            return;
        }
        try {
            byte[] buffer = new byte[20];       // 0x12 command length

            buffer[0] = (byte) (buffer.length - 2); // length of message: length of buffer minus the first two
            buffer[1] = 0;                          // length of message

            // message start
            // !!!! REMEMBER ABOUT BYTE SWAPPING
            buffer[2] = 34;
            buffer[3] = 12;

            buffer[4] = (byte) 0x80; // response: 00 -> reply, 80 -> no reply

            buffer[5] = 0; // parameters: none
            buffer[6] = 0; // parameters: none

            buffer[7] = (byte) 0xae; // opcode [ see 'LEGO_MINDSTORMS_EV3_Firmware_Developer_Kit-1.pdf' ]
            buffer[8] = 0;           // function for opcode, might not be a thing

            buffer[9] = (byte) motorsel; // MOTOR SELECTION [ NOS in docs, see ev3 in Documents folder ]

            buffer[10] = (byte) 0x81;
            buffer[11] = (byte) inSpeed; // speed

            buffer[12] = 0;

            buffer[13] = (byte) 0x82;
            buffer[14] = (byte) 0x84;
            buffer[15] = (byte) 0x01; // times turned

            buffer[16] = (byte) 0x82;
            buffer[17] = (byte) 0xB4;
            buffer[18] = (byte) 0x00;

            buffer[19] = 1;

            cv_os.write(buffer);
            cv_os.flush();
        }
        catch (Exception e) {
            statusText.setText("Error in MoveForward(" + e.getMessage() + ")");
        }
    }
}