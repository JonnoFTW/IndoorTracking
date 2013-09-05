package com.jonathanmackenzie.indoortracking;

import java.util.List;
import java.util.Vector;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener,
        OnSharedPreferenceChangeListener {

    static public String currentSex = "Male";
    static public int height = 180;
    private float yaw = 0, pitch = 0, roll = 0, x = 0, y = 0;
    private SensorManager mSensorManager;
    private Sensor mMagnetometer;
    private Sensor mAccelerometer;

    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;

    
    private CircularFifoQueue<Double> accelReadings = new CircularFifoQueue<Double>(32);
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];
    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());


        prefs.registerOnSharedPreferenceChangeListener(this);
        updateSettings();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        updateSettings();
        getActionBar().show();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        findViewById(R.id.mainView).setOnTouchListener(new OnTouchListener() {
            
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                Log.i(v.toString(), "Tapped "+v+", resetting  location");
                return false;
            }
        });
    }

    private void resetLocation() {
        x = 0;
        y = 0;
    }
    private void updateSettings() {
        try {
            height = Integer.valueOf(prefs.getString("height_value", "0"));
            ((TextView) findViewById(R.id.textViewHeight)).setText("Height: "
                    + height);
            currentSex = prefs.getString("sex_value", "Male");
            ((TextView) findViewById(R.id.textViewSex)).setText("Sex: "
                    + currentSex);
            float genFactor = (currentSex.equals("Female")) ? 0.413f : 0.415f;
            float stepDist = genFactor * height;
            ((TextView) findViewById(R.id.textViewStepDist)).setText("Step dist: "+stepDist);
           
        } catch (Exception e) {
            // TODO: handle exception
            Log.e("Settings", e.toString());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_actions, menu);
        return true;
    }

    public void listSensors(Context c) {

        List<Sensor> devicesSensors = mSensorManager
                .getSensorList(Sensor.TYPE_ALL);
        // c.get

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        Class i = Object.class;
        switch (item.getItemId()) {
        case R.id.action_settings:
            i = SettingsActivity.class;
            break;
        case R.id.action_help: 
            i = SettingsActivity.class;
            break;
        case R.id.action_graph:
            i = GraphActivity.class;
            break;
        default:
           break;
        }
        if(i!=Object.class) {
            Log.i("Action bar", "Starting "+i);
            startActivity(new Intent(this,i));
            return true;
        } else
            return  super.onOptionsItemSelected(item);
        
    }


    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub

    }

    public void onSensorChanged(SensorEvent event) {
        synchronized (this) {
            if (event.sensor == mAccelerometer) {
                // accelerations = Arrays..values;
                System.arraycopy(event.values, 0, mLastAccelerometer, 0,
                        event.values.length);
                mLastAccelerometerSet = true;
                // Detect Step
                float genFactor = (currentSex.equals("Female")) ? 0.413f
                        : 0.415f;
                float stepDist = genFactor * height;
                // accelerations
                double xa = event.values[0], ya = event.values[1], za = event.values[2];
                ((TextView) findViewById(R.id.textViewXAccel))
                        .setText("Xaccel: " + event.values[0]);
                ((TextView) findViewById(R.id.textViewYAccel))
                        .setText("Yaccel: " + event.values[1]);
                ((TextView) findViewById(R.id.textViewZAccel))
                        .setText("Zaccel: " + event.values[2]);

                double accelVector = Math.sqrt(xa * xa + ya * ya + za * za) - 9.81; // account for acceleration at sea level
                ((TextView) findViewById(R.id.textViewAccelVector))
                        .setText("accel vector: " + accelVector);
                accelReadings.add(accelVector);
                // If we take a step update the numbers
                float accelThreshold = 5;
                /*
                
                x += Math.cos(yaw) * stepDist;
                y += Math.sin(yaw) * stepDist;
                */
                ((TextView) findViewById(R.id.textViewX)).setText("X: " + x);
                ((TextView) findViewById(R.id.textViewY)).setText("Y: " + y);
            } else if (event.sensor == mMagnetometer) {
                System.arraycopy(event.values, 0, mLastMagnetometer, 0,
                        event.values.length);
                mLastMagnetometerSet = true;
            }
            if (mLastAccelerometerSet && mLastMagnetometerSet) {
                SensorManager.getRotationMatrix(mR, null, mLastAccelerometer,
                        mLastMagnetometer);
                SensorManager.getOrientation(mR, mOrientation);
                float rad_deg = 57.2957795f;
                yaw = mOrientation[0] ;//* rad_deg; // radians to degrees
                pitch = mOrientation[1];// * rad_deg;
                roll = mOrientation[2] ;//* rad_deg;
                TextView tvYaw = (TextView) findViewById(R.id.textViewYaw);
                TextView tvRoll = (TextView) findViewById(R.id.textViewRoll);
                TextView tvPitch = (TextView) findViewById(R.id.textViewPitch);
                tvYaw.setText("Yaw: " + yaw);
                tvPitch.setText("Pitch: " + pitch);
                tvRoll.setText("Roll: " + roll);
            }

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLastAccelerometerSet = false;
        mLastMagnetometerSet = false;
        mSensorManager.registerListener(this, mAccelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mMagnetometer,
                SensorManager.SENSOR_DELAY_NORMAL);

    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        updateSettings();
        try {
            Log.i("Preferences",
                    key + " was changed to "
                            + sharedPreferences.getString(key, null));
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}
