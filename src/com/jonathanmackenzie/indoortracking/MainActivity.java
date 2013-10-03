package com.jonathanmackenzie.indoortracking;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener,
        OnSharedPreferenceChangeListener {

    private int window_size = 3;
    private String currentSex = "Male";
    private int height = 180;
    private float yaw = 0;
    private static float x = 0;
    private static float y = 0;
    private float stepDist = 0;
    private SensorManager mSensorManager;
    private Sensor mMagnetometer;
    private Sensor mAccelerometer;
    private Sensor mLinearAccelerometer;

    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;

    private DecimalFormat df = new DecimalFormat("###.##");
    private static List<Point> stepXY = new LinkedList<Point>();
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];
    private SharedPreferences prefs;

    private static LinkedList<Double> lastAccels, medianAccels;
    private double g = 0;
    private double stepThreshold = 0.5d;
    private MyImageView iv;
    private int steps;
    private long lastStep = System.nanoTime(); // When the last step was taken,
                                               // steps take 0.5s
    private long stepTimeout = 400000000l;
    private static SoundPool soundPool;
    private static int stepSound, stepSound2, resetSound;
    private static boolean step1 = false;

    public class Point {
        public float x, y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public String toString() {
            return x + "," + y;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        iv = (MyImageView) findViewById(R.id.istMap);// new
                                                     // MyImageView(getApplicationContext());
        iv.addOnLayoutChangeListener(new OnLayoutChangeListener() {

            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight,
                    int oldBottom) {
                resetLocation(null);
                iv.removeOnLayoutChangeListener(this);
            }
        });
        prefs.registerOnSharedPreferenceChangeListener(this);

        super.onCreate(savedInstanceState);
        getActionBar().show();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mLinearAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mMagnetometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        updateSettings();
        lastAccels = new LinkedList<Double>();
        medianAccels = new LinkedList<Double>();
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 100);
        stepSound = soundPool.load(this, R.raw.step_sound, 1);
        stepSound2 = soundPool.load(this, R.raw.step_sound2, 1);
        resetSound = soundPool.load(this, R.raw.reset_sound, 1);

    }

    public static void playStepSound() {
        if (step1) {
            soundPool.play(stepSound, 1, 1, 1, 0, 1f);
            step1 = false;
        } else {
            soundPool.play(stepSound2, 1, 1, 1, 0, 1f);
            step1 = true;
        }
    }

    public List<Point> getSteps() {
        return stepXY;
    }

    public float getOrientation() {
        return yaw;
    }

    public void resetLocation(View v) {
        steps = 0;
        x = 163 * iv.getXScale();
        y = 414 * iv.getYScale();
        stepXY.clear();
        stepXY.add(new Point(x, y));
        iv.invalidate();
        Log.i("MainActivity", "Location reset");
        ((TextView) findViewById(R.id.textViewSteps))
                .setText("Steps: " + steps);
        soundPool.play(resetSound, 1, 1, 1, 0, 1);
    }

    private void updateSettings() {
        try {
            height = Integer.valueOf(prefs.getString("height_value", ""
                    + height));
            currentSex   = prefs.getString("sex_value", "Male");
            window_size  = Integer.valueOf(prefs.getString("windowsize_value", ""+window_size));
            stepThreshold= Double.parseDouble(prefs.getString("threshold_value",""+ stepThreshold));
            stepTimeout  = (long) (Double.parseDouble(prefs.getString("timeout_value",""+ stepTimeout)) * 100000000l);
        } catch (Exception e) {
            Log.e("Settings", e.toString());
        } finally {

            stepDist = getSexFactor() * height;
        }
    }

    private float getSexFactor() {
        return (currentSex.equals("Female")) ? 0.413f : 0.415f;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        Intent in = null;
        switch (item.getItemId()) {
        case R.id.action_settings:
            in = new Intent(this, SettingsActivity.class);
            break;
        case R.id.action_help:
            in = new Intent(this, HelpActivity.class);
            break;
        case R.id.action_graph:
            in = new Intent(this, GraphActivity.class);
            break;
        default:
            break;
        }
        if (in != null) {
            startActivity(in);
            return true;
        } else
            return super.onOptionsItemSelected(item);

    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public synchronized void onSensorChanged(SensorEvent event) {
        if (event.sensor == mLinearAccelerometer) {
            // accelerations
            double xa = event.values[0],
                   ya = event.values[1], 
                   za = event.values[2];
            ((TextView)findViewById(R.id.textViewLinear)).setText(String.format("%.2f %.2f %.2f", xa,ya,za));
            double accelVector = Math.sqrt(xa * xa + ya * ya + za * za);
           
            g = 0.9 * g + 0.1 * accelVector;
            double v = accelVector - g;
            lastAccels.addLast(v);
            while (lastAccels.size() > window_size) {
                lastAccels.removeFirst();
            }
            // Get the median value
            LinkedList<Double> medianList = new LinkedList<Double>(lastAccels);
            Collections.sort(medianList);
            double median = medianList.get(medianList.size() / 2);
            ((TextView)findViewById(R.id.textViewMedianAccel)).setText("Median Accel: "+df.format(median));
            ((TextView)findViewById(R.id.textViewV)).setText("V: "+df.format(v));
            medianAccels.addLast(median);
            while (medianAccels.size() > window_size) {
                medianAccels.removeFirst();
            }

            // Using get() on a linked list should be fine since there's
            // a small windows size (3 or 4)
            if (/*
                 * medianAccels.getLast() - medianAccels.getFirst() <= 0.5 &&
                 * medianAccels.get(medianAccels.size()/2)
                 */v > stepThreshold
                    && System.nanoTime() >= lastStep + stepTimeout) {
                lastStep = System.nanoTime();
                Log.i("MainActivity", "Step taken");
                playStepSound();
                steps++;
                x += Math.cos(yaw) * stepDist / iv.getHorizontalDistScale()
                        * iv.getXScale();
                y -= Math.sin(yaw) * stepDist / iv.getVerticalDistScale()
                        * iv.getYScale();
                stepXY.add(new Point(x, y));
                // Log.i("MainActivity", "Steps at :"+stepXY);

                iv.invalidate();
                ((TextView) findViewById(R.id.textViewSteps)).setText("Steps: "
                        + steps);
            }
        }
        else if (event.sensor == mAccelerometer) {
            // accelerations = Arrays..values;
            System.arraycopy(event.values, 0, mLastAccelerometer, 0,
                    event.values.length);
            mLastAccelerometerSet = true;

        } else if (event.sensor == mMagnetometer) {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0,
                    event.values.length);
            mLastMagnetometerSet = true;
        }
        if (mLastAccelerometerSet && mLastMagnetometerSet) {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer,
                    mLastMagnetometer);
            SensorManager.getOrientation(mR, mOrientation);
            yaw = mOrientation[0]; // * rad_deg; // radians to degrees
            TextView tvYaw = (TextView) findViewById(R.id.textViewYaw);
            tvYaw.setText("Yaw: " + df.format(yaw) + ", "
                    + df.format(Math.toDegrees(yaw)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mLastAccelerometerSet = false;
        mLastMagnetometerSet = false;
        mSensorManager.registerListener(this, mAccelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mLinearAccelerometer,
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
        }
    }
}
