package com.jonathanmackenzie.indoortracking;

import java.text.DecimalFormat;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnTouchListener;
import android.widget.TextView;

/**
 * Main activity of the application, tracks steps 
 * and displays how many are taken. Holds the imageview that shows the map
 * @author Jonathan
 *
 */
public class MainActivity extends Activity implements SensorEventListener,
        OnSharedPreferenceChangeListener {

    private int window_size = 3;
    private String currentSex = "Male";
    private int height = 190;
    private float yaw = 0;
    private static float x = 0;
    private static float y = 0;
    private float stepDist = 0;
    private SensorManager mSensorManager;
    private Sensor mMagnetometer;
    private Sensor mAccelerometer;
    private Sensor mOrientationSensor;

    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;

    private DecimalFormat df = new DecimalFormat("###.##");
    private static List<Point> stepXY = new LinkedList<Point>();
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];
    private SharedPreferences prefs;

    private static LinkedList<Double> lastAccels, meanAccels;
    private double g = 10;
    private double stepThreshold = 0.9d;
    private MyImageView iv;
    private int steps;
    private long lastStep = System.nanoTime(); // When the last step was taken,
                                               // steps take 0.5s
    private long stepTimeout = (long) (0.5 * 1000000000l);
    protected float lastTouchY;
    protected float lastTouchX;
    private static SoundPool soundPool;
    private static int stepSound, stepSound2, resetSound;
    private static boolean step1 = false;

    /**
     * A class to hold point data
     * @author Jonathan
     *
     */
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

        iv = (MyImageView) findViewById(R.id.istMap);
        // Resets the location when the application starts.
        // It will only activate when the app launches
        iv.addOnLayoutChangeListener(new OnLayoutChangeListener() {

            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight,
                    int oldBottom) {
                resetLocation(163 * iv.getXScale(),414 * iv.getYScale());
                iv.removeOnLayoutChangeListener(this);
            }
        });
        // Long touches should set the location to where the touch was
        // click should reset to the door of 307
        iv.setOnTouchListener(new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (event.getEventTime() - event.getDownTime() > 450) {
                        resetLocation(event.getX(),event.getY());
                    } else {
                        resetLocation(163 * iv.getXScale(),414* iv.getYScale());
                    }
                }
                return true;
            }
        });
        prefs.registerOnSharedPreferenceChangeListener(this);

        super.onCreate(savedInstanceState);
        getActionBar().show();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        // This is more accurate it seems, it also doesn't change
        // when we are walking
        mOrientationSensor = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ORIENTATION);
        updateSettings();
        lastAccels = new LinkedList<Double>();
        meanAccels = new LinkedList<Double>();
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 100);
        stepSound = soundPool.load(this, R.raw.step_sound, 1);
        stepSound2 = soundPool.load(this, R.raw.step_sound2, 1);
        resetSound = soundPool.load(this, R.raw.reset_sound, 1);

    }
    /**
     * Plays alternating sounds on step
     */
    public static void playStepSound() {
        if (step1) {
            soundPool.play(stepSound, 1, 1, 1, 0, 1f);
            step1 = false;
        } else {
            soundPool.play(stepSound2, 1, 1, 1, 0, 1f);
            step1 = true;
        }
    }
    /**
     * Returns the list of steps taken
     * @return
     */
    public List<Point> getSteps() {
        return stepXY;
    }
    /**
     * Gives the current orientation
     * @return the orientation in radians
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Resets the steps and sets 
     * @param v
     */
    private void resetLocation(float x, float y) {
        this.x = x;
        this.y = y;
        steps = 0;
        stepXY.clear();
        stepXY.add(new Point(x, y));
        iv.invalidate();
        Log.i("MainActivity", "Location reset");
        ((TextView) findViewById(R.id.textViewSteps))
                .setText("Steps: " + steps);
        soundPool.play(resetSound, 1, 1, 1, 0, 1);
    }

    /**
     * Pull the settings from the preferences and use them
     */
    private void updateSettings() {
        try {
            height = Integer.valueOf(prefs.getString("height_value", ""
                    + height));
            currentSex = prefs.getString("sex_value", "Male");
            window_size = Integer.valueOf(prefs.getString("windowsize_value",
                    "" + window_size));
            stepThreshold = Double.parseDouble(prefs.getString(
                    "threshold_value", "" + stepThreshold));
            stepTimeout = (long) (Double.parseDouble(prefs.getString(
                    "timeout_value", "" + stepTimeout)) * 1000000000l);
        } catch (Exception e) {
            Log.e("Settings", e.toString());
        } finally {

            stepDist = getSexFactor() * height;
            Log.i("MainActivity", "Step dist:" + stepDist);
        }
    }
    /**
     * The factor given for sex to step distance. 0.413 for females, 0.415 for men
     * @return the multiplier for males and females
     */
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
    /**
     * When the sensor changes, update the orientation
     */
    public synchronized void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAccelerometer) {
            // accelerations = Arrays..values;
            System.arraycopy(event.values, 0, mLastAccelerometer, 0,
                    event.values.length);
            mLastAccelerometerSet = true;

            // accelerations
            double xa = event.values[0], ya = event.values[1], za = event.values[2];
            double accelVector = Math.sqrt(xa * xa + ya * ya + za * za);
            // Apply a low pass filter
            // to account for gravity, g is initially 9.81 
            // because of gravity due to earth
            g = 0.9 * g + 0.1 * accelVector;
            double v = accelVector - g;
            lastAccels.addLast(v);
            while (lastAccels.size() > window_size) {
                lastAccels.removeFirst();
            }

            double sum = 0;
            for (Double d : lastAccels) {
                sum += d;
            }
            // Credit to Bradley Donnoly for suggesting I use a mean filter
            // to smooth the curve. This is fairly accurate and prevents counting of 
            // steps that might be due to shaking etc.
            double mean = sum / lastAccels.size();
            meanAccels.addLast(mean);
            while (meanAccels.size() > window_size) {
                meanAccels.removeFirst();
            }
            // Prevent multiple steps from being counted on a single peak
            // of the curve
            boolean timedOut = System.nanoTime() >= lastStep + stepTimeout;
            if (meanAccels.getLast() > stepThreshold && timedOut) {
                lastStep = System.nanoTime();
                Log.i("MainActivity", "Step taken");
                playStepSound();
                steps++;
                x += Math.cos(yaw) * stepDist * 5 / iv.getHorizontalDistScale();
                y += Math.sin(yaw) * stepDist * 5 / iv.getVerticalDistScale();
                stepXY.add(new Point(x, y));

                iv.invalidate();
                ((TextView) findViewById(R.id.textViewSteps)).setText("Steps: "
                        + steps);
            }

        } else if (event.sensor == mMagnetometer) {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0,
                    event.values.length);
            mLastMagnetometerSet = true;
        } else if (event.sensor == mOrientationSensor) {
          /*  ((TextView) findViewById(R.id.textViewOrientation))
                    .setText("Orientation: "
                            + df.format(Math.toRadians(event.values[0])) + ", "
                            + df.format(event.values[0]));*/
            
                yaw = (float) Math.toRadians(event.values[0] - 90);
            iv.invalidate();
        }
        if (mLastAccelerometerSet && mLastMagnetometerSet) {
            // Both accelerometer and magnetometer are set so we can 
            // use the derived value for orientation
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer,
                    mLastMagnetometer);
            SensorManager.getOrientation(mR, mOrientation);
           iv.invalidate();
             //   yaw = (float) (mOrientation[0] - Math.toRadians(90)); 
          //  TextView tvYaw = (TextView) findViewById(R.id.textViewYaw);
          /*  tvYaw.setText("Yaw: "
                    + df.format(mOrientation[0] - Math.toRadians(90))
                            + ", "
                            + df.format(Math.toDegrees((mOrientation[0] - Math
                                    .toRadians(90)))));*/
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
        mSensorManager.registerListener(this, mOrientationSensor,
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
