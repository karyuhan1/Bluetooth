package com.example.test;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test.helper.DatabaseHelper;
import com.example.test.helper.NavigationService;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.UUID;

public class ActualActivity extends AppCompatActivity {
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    private BluetoothGatt bluetoothGatt;
    private Interpreter tflite;
    private TextView logTextView;
    private String deviceMac;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actual);

        logTextView = findViewById(R.id.log_text_view);
        Button retrainButton = findViewById(R.id.retrain_button);

        dbHelper = new DatabaseHelper(this);
        try {
            dbHelper.createDatabase();
            dbHelper.openDatabase();
        } catch (IOException e) {
            throw new RuntimeException("Error creating database", e);
        }

        // Get the Bluetooth device address from the intent
        deviceMac = getIntent().getStringExtra("device_address");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceMac);

        // Load the trained model from the database
        loadModelFromDatabase();

        // Connect to the Bluetooth device
        connectToDevice(device);

        // Set up retrain button
        retrainButton.setOnClickListener(v -> {
            Intent intent = new Intent(ActualActivity.this, WorkActivity.class);
            intent.putExtra("device_address", deviceMac);
            startActivity(intent);
        });
    }

    private void loadModelFromDatabase() {
        Cursor cursor = dbHelper.getReadableDatabase().rawQuery(
                "SELECT model_name FROM tb_deep_learning WHERE device_mac = ? ORDER BY model_idx DESC LIMIT 1",
                new String[]{deviceMac}
        );
        if (cursor.moveToFirst()) {
            String modelName = cursor.getString(0);
            loadModelFile(modelName);
        }
        cursor.close();
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                    @Override
                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            runOnUiThread(() -> {
                                Toast.makeText(ActualActivity.this, "Connected to " + device.getName(), Toast.LENGTH_SHORT).show();
                                logTextView.append("Connected to " + device.getName() + "\n");
                            });
                            gatt.discoverServices();
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            runOnUiThread(() -> {
                                Toast.makeText(ActualActivity.this, "Disconnected from " + device.getName(), Toast.LENGTH_SHORT).show();
                                logTextView.append("Disconnected from " + device.getName() + "\n");
                            });
                            gatt.close();
                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            BluetoothGattService service = gatt.getService(SERVICE_UUID);
                            if (service != null) {
                                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                                if (characteristic != null) {
                                    readCharacteristic(gatt, characteristic);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            byte[] data = characteristic.getValue();
                            processSensorData(data);
                        }
                    }
                });
            } else {
                throw new SecurityException("Bluetooth connect permission not granted");
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this);
        }
    }

    private void readCharacteristic(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            if (gatt != null && characteristic != null) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.readCharacteristic(characteristic);
                } else {
                    throw new SecurityException("Bluetooth connect permission not granted");
                }
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this);
        }
    }

    private void processSensorData(byte[] data) {
        // Assume the data is in the same format as the training data
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder());
        for (byte b : data) {
            inputBuffer.putFloat(b);
        }

        float[][] output = new float[1][2];
        tflite.run(inputBuffer, output);

        // Use the model's output to control the Arduino sensors
        controlArduinoSensors(output);
    }

    private void controlArduinoSensors(float[][] output) {
        // Implement the logic to control Arduino sensors based on the model's output
        String command = "SENSOR_CONTROL:" + output[0][0] + "," + output[0][1];
        writeCharacteristic(command);

        // Log the command sent to Arduino
        runOnUiThread(() -> logTextView.append("Command sent: " + command + "\n"));
    }

    private void writeCharacteristic(String value) {
        try {
            if (bluetoothGatt != null) {
                BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        characteristic.setValue(value);
                        bluetoothGatt.writeCharacteristic(characteristic);
                    }
                }
            }
        } catch (SecurityException e) {
            NavigationService.navigateToMainActivity(this);
        }
    }

    private void loadModelFile(String modelName) {
        try {
            File modelFile = new File(getFilesDir(), modelName + ".tflite");
            FileInputStream inputStream = new FileInputStream(modelFile);
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = 0;
            long declaredLength = fileChannel.size();
            MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
            tflite = new Interpreter(mappedByteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
                bluetoothGatt = null;
            } catch (SecurityException e) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }
    }
}
