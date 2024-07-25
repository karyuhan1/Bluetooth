package com.example.test.helper;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "database.db";
    private static final int DATABASE_VERSION = 1;
    private static String DATABASE_PATH = "";
    private SQLiteDatabase myDatabase;
    private final Context myContext;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.myContext = context;
        DATABASE_PATH = context.getDatabasePath(DATABASE_NAME).getPath();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 테이블 생성 로직을 여기에 추가하지 않습니다.
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 데이터베이스 업그레이드 로직을 여기에 추가할 수 있습니다.
    }

    public void createDatabase() throws IOException {
        boolean dbExist = checkDatabase();

        if (!dbExist) {
            this.getReadableDatabase();
            try {
                copyDatabase();
            } catch (IOException e) {
                throw new Error("Error copying database");
            }
        }
    }

    private boolean checkDatabase() {
        SQLiteDatabase checkDB = null;
        try {
            String myPath = DATABASE_PATH;
            checkDB = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e) {
            // 데이터베이스가 아직 존재하지 않습니다.
        }

        if (checkDB != null) {
            checkDB.close();
        }

        return checkDB != null;
    }

    private void copyDatabase() throws IOException {
        InputStream myInput = myContext.getAssets().open(DATABASE_NAME);
        String outFileName = DATABASE_PATH;
        OutputStream myOutput = new FileOutputStream(outFileName);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = myInput.read(buffer)) > 0) {
            myOutput.write(buffer, 0, length);
        }

        myOutput.flush();
        myOutput.close();
        myInput.close();
    }

    public void openDatabase() throws SQLiteException {
        String myPath = DATABASE_PATH;
        myDatabase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE);
    }

    @Override
    public synchronized void close() {
        if (myDatabase != null) {
            myDatabase.close();
        }
        super.close();
    }

    public void insertSensingData(int deviceIdx, int middleFlexSensor, int middlePressureSensor, int ringFlexSensor,
                                  int ringPressureSensor, int pinkyFlexSensor, int acceleration, int gyroscope,
                                  int magneticField, String timestamp, int userIdx) {
        SQLiteDatabase db = this.getWritableDatabase();
        String insertQuery = "INSERT INTO tb_sensing (device_idx, middle_flex_sensor, middle_pressure_sensor, ring_flex_sensor, " +
                "ring_pressure_sensor, pinky_flex_sensor, acceleration, gyroscope, magnetic_field, timestamp, user_idx) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        db.execSQL(insertQuery, new Object[]{deviceIdx, middleFlexSensor, middlePressureSensor, ringFlexSensor,
                ringPressureSensor, pinkyFlexSensor, acceleration, gyroscope,
                magneticField, timestamp, userIdx});
    }

    public void resetSensingData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM tb_sensing");
        db.execSQL("VACUUM");
    }

    public void insertDeepLearningData(String modelName, String analysisResult, float predictionRate, long createdAt) {
        SQLiteDatabase db = this.getWritableDatabase();
        String insertQuery = "INSERT INTO tb_deep_learning (model_name, analysis_result, prediction_rate, created_at) " +
                "VALUES (?, ?, ?, ?)";
        db.execSQL(insertQuery, new Object[]{modelName, analysisResult, predictionRate, createdAt});
    }
}
