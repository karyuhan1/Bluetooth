package com.example.test;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RunningActivity extends AppCompatActivity {
    // DB에 저장된 센서 데이터를 토대로 학습을 실행하는 액티비티
    // 여기부터는 아두이노랑 연동해야하는데 아직 아두이노가 미완성이라 대략적으로 만들었습니다.
    private DatabaseHelper dbHelper;
    private Interpreter tflite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running);

        dbHelper = new DatabaseHelper(this);

        dbHelper.openDatabase();
        trainAndSaveModel();
    }

    private void trainAndSaveModel() {
        // 학습 데이터 가져오기
        List<float[]> dataList = getSensingData();

        if (dataList.isEmpty()) {
            Toast.makeText(this, "학습할 데이터가 없습니다.", Toast.LENGTH_SHORT).show();
            NavigationService.navigateToWorkActivity(this);
            return;
        }

        // 데이터를 float 배열로 변환
        float[][] dataArray = dataList.toArray(new float[dataList.size()][]);

        // 모델 정의 및 학습
        try {
            tflite = new Interpreter(loadModelFile());

            // 데이터 전처리 및 모델 학습
            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(dataArray.length * dataArray[0].length * 4).order(ByteOrder.nativeOrder());
            for (float[] data : dataArray) {
                for (float value : data) {
                    inputBuffer.putFloat(value);
                }
            }

            // 모델 학습
            float[][] output = new float[dataArray.length][2];
            tflite.run(inputBuffer, output);

            // 학습 결과 평가
            float predictionRate = evaluateModel(output);

            // 학습 결과 저장
            saveModel("Sensor Pair Model", "Analysis Result", predictionRate);
        } catch (Exception e) {
            Log.e(TAG, "Error during model training", e); // 예외 로깅
        }
    }

    private List<float[]> getSensingData() {
        List<float[]> dataList = new ArrayList<>();
        Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT middleFlexSensor, middlePressureSensor, ringFlexSensor, ringPressureSensor, pinkyFlexSensor, acceleration, gyroscope, magneticField FROM tb_sensing", null);
        while (cursor.moveToNext()) {
            float[] data = new float[8];
            data[0] = cursor.getInt(0);
            data[1] = cursor.getInt(1);
            data[2] = cursor.getInt(2);
            data[3] = cursor.getInt(3);
            data[4] = cursor.getInt(4);
            data[5] = cursor.getInt(5);
            data[6] = cursor.getInt(6);
            data[7] = cursor.getInt(7);
            dataList.add(data);
        }
        cursor.close();
        return dataList;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        File modelFile = new File(getFilesDir(), "sensor_pair_model.tflite");
        FileInputStream inputStream = new FileInputStream(modelFile);
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = 0;
        long declaredLength = fileChannel.size();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void saveModel(String modelName, String analysisResult, float predictionRate) {
        long currentTime = new Date().getTime();
        dbHelper.insertDeepLearningData(modelName, analysisResult, predictionRate, currentTime);

        // 학습이 끝나면 센싱 테이블 초기화
        dbHelper.resetSensingData();

        // 학습이 끝나면 ActualActivity로 이동
        Intent intent = new Intent(RunningActivity.this, ActualActivity.class);
        intent.putExtra("device_address", getIntent().getStringExtra("device_address"));
        startActivity(intent);
        finish();
    }

    private float evaluateModel(float[][] output) {
        // 모델의 예측 결과를 평가하는 로직을 구현합니다.
        // 여기서는 단순히 예측 정확도의 평균을 계산하는 예제를 제공합니다.
        float totalAccuracy = 0;
        for (float[] prediction : output) {
            totalAccuracy += (prediction[0] + prediction[1]) / 2;
        }
        return totalAccuracy / output.length;
    }
}
