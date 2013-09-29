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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
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
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener,
        OnSharedPreferenceChangeListener {

    private String currentSex = "Male";
    private int height = 180;
    private float yaw = 0;
    private static float x = 0;
    private static float y = 0;
    private float stepDist = 0;
    private SensorManager mSensorManager;
    private Sensor mMagnetometer;
    private Sensor mAccelerometer;

    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;

    private DecimalFormat df = new DecimalFormat("###.##");
    private static List<Point> stepXY = new LinkedList<Point>();
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];
    private SharedPreferences prefs;

    private static LinkedList<Double> lastAccels;
    private double g = 9.81;
    private double stepThreshold = 0.5;
    private MyImageView iv;
    private int steps;
    private long lastStep = System.nanoTime(); // When the last step was taken, steps take 0.5s
    private long stepTimeout = 500000000l;
    private static SoundPool soundPool;
    private static int stepSound;

    public class Point {
        public float x, y;

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
        public String toString() {
            return x+","+y;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        
        
        LinearLayout ll = (LinearLayout) findViewById(R.id.mainView);
        iv = (MyImageView)findViewById(R.id.istMap);// new MyImageView(getApplicationContext());
        
        // addContentView(new MyImageView(this), );
        prefs.registerOnSharedPreferenceChangeListener(this);

        super.onCreate(savedInstanceState);
        updateSettings();
        getActionBar().show();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        updateSettings();
        lastAccels = new LinkedList<Double>();
        resetLocation(null);
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 100);
        stepSound = soundPool.load(this, R.raw.step_sound, 1);
    }

    public static void playStepSound() {
        soundPool.play(stepSound, 1, 1, 1, 0, 1f);
    }
    public List<Point> getSteps() {
        return stepXY;
    }
    public float getX() {
        return x;
    }
    public float getY() {
        return y;
    }
    public void setX(float x) {
        MainActivity.x = x;
    }
    public void setY(float y) {
        MainActivity.y = y;
    }
    public void resetLocation(View v) {
        steps = 0;
        x = 163 * iv.getXScale();
        y = 414 * iv.getYScale();
        stepXY.clear();
        stepXY.add(new Point(x, y));
        iv.invalidate();
        Log.i("MainActivity", "Location reset");
    }

    private void updateSettings() {
        try {
            height = Integer.valueOf(prefs.getString("height_value", ""
                    + height));
            currentSex = prefs.getString("sex_value", "Male");
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
        Class i = Object.class;
        switch (item.getItemId()) {
        case R.id.action_settings:
            i = SettingsActivity.class;
            break;
        case R.id.action_help:
            i = HelpActivity.class;
            break;
        case R.id.action_graph:
            i = GraphActivity.class;
            break;
        default:
            break;
        }
        if (i != Object.class) {
            Log.i("Action bar", "Starting " + i);
            startActivity(new Intent(this, i));
            return true;
        } else
            return super.onOptionsItemSelected(item);

    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public synchronized void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAccelerometer) {
            // accelerations = Arrays..values;
            System.arraycopy(event.values, 0, mLastAccelerometer, 0,
                    event.values.length);
            mLastAccelerometerSet = true;
            // Detect Step
            float genFactor = (currentSex.equals("Female")) ? 0.413f : 0.415f;
            float stepDist = genFactor * height;
            // accelerations
            double xa = event.values[0], ya = event.values[1], za = event.values[2];

            double accelVector = Math.sqrt(xa * xa + ya * ya + za * za) - 9.81; // account
            g = 0.9 * g + 0.1 * accelVector;
            double v = accelVector - g;
            lastAccels.add(v);
            boolean stepTaken = v > stepThreshold;
           
            if (stepTaken && System.nanoTime() >= lastStep + stepTimeout ) {
                lastStep = System.nanoTime();
                Log.i("MainActivity","Step taken");
                playStepSound();
                steps++;
                x += Math.cos(yaw) * stepDist / iv.getDistScale() * iv.getXScale(); // This is in metres
                y -= Math.sin(yaw) * stepDist /  iv.getDistScale() * iv.getYScale();
                stepXY.add(new Point(x, y));
                Log.i("MainActivity", "Steps at :"+stepXY);
              
                iv.invalidate();
                ((TextView)findViewById(R.id.textViewSteps)).setText("Steps: "+steps);
            }
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
            double rad_deg = 180.0 / Math.PI;
            yaw = mOrientation[0]; // * rad_deg; // radians to degrees
            TextView tvYaw = (TextView) findViewById(R.id.textViewYaw);
            tvYaw.setText("Yaw: " + df.format(yaw));
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
        }
    }
}
