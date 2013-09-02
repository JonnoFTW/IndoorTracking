package com.jonathanmackenzie.indoortracking;

import java.util.List;
import java.util.Vector;

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
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener,
		OnSharedPreferenceChangeListener {
	public final static String EXTRA_MESSAGE = "com.jonathanmackenzie.indoortracking.MESSAGE";

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

	private float[] mR = new float[9];
	private float[] mOrientation = new float[3];
	public SharedPreferences prefs;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		updateSettings();
		getActionBar().show();
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mMagnetometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

	}

	private void updateSettings() {
		try {
			height = prefs.getInt("height_value", height);
			currentSex = prefs.getString("sex_value", "Male");
			((TextView) findViewById(R.id.textViewSex)).setText("Sex: "
					+ currentSex);
			((TextView) findViewById(R.id.textViewHeight)).setText("Height: "
					+ height);
			((TextView) findViewById(R.id.textViewX)).setText("X: " + x);
			((TextView) findViewById(R.id.textViewY)).setText("Y: " + y);
		} catch (Exception e) {
			// TODO: handle exception
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
		switch (item.getItemId()) {
		case R.id.action_settings:
			openSettings();
			return true;
		case R.id.action_help:
			openHelp();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void openHelp() {
		// TODO Auto-generated method stub
		Intent i = new Intent(this, HelpActivity.class);
		startActivity(i);
	}

	private void openSettings() {
		// TODO Auto-generated method stub
		Intent i = new Intent(this, SettingsActivity.class);
		startActivity(i);

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
				float genFactor = (currentSex.equals("Female")) ? 0.413f : 0.415f;
				float stepDist = genFactor * height;
				Vector<Float> stepVector = new Vector<Float>();
				x += yaw;
				y += yaw;
				((TextView) findViewById(R.id.textViewXAccel))
						.setText("Xaccel: " + event.values[0]);
				((TextView) findViewById(R.id.textViewYAccel))
						.setText("Yaccel: " + event.values[1]);
				((TextView) findViewById(R.id.textViewZAccel))
						.setText("Zaccel: " + event.values[2]);
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
				yaw = mOrientation[0] * rad_deg; // radians to degrees
				pitch = mOrientation[1] * rad_deg;
				roll = mOrientation[2] * rad_deg;
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
