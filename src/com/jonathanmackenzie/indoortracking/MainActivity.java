package com.jonathanmackenzie.indoortracking;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {
    public final static String EXTRA_MESSAGE = "com.jonathanmackenzie.indoortracking.MESSAGE";
    
    private enum Sex {MALE, FEMALE};
    private Sex currentSex = Sex.MALE;
    private int height = 180;
    private float yaw = 0, x =0, y=0;
    private float[] rotationMatrix = new float[9];
    private float[] magneticFields = new float[3];
    private float[] accelerations  = new float[3];
    private SensorManager mSensorManager;
    private Sensor mCompass;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getActionBar().show();
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
      //  mSensorManager.
        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_actions, menu);
        return true;
    }

    public void listSensors(Context c) {
        
        List<Sensor> devicesSensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
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
        Intent i = new Intent(this,HelpActivity.class);
        startActivity(i);
    }

    private void openSettings() {
        // TODO Auto-generated method stub
        Intent i = new Intent(this,SettingsActivity.class); 
        startActivity(i);
        
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // TODO Auto-generated method stub
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
               //  accelerations = Arrays..values;
            
        }
        else if(event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerations,  magneticFields);
        float[] orientation = new float[3];
        mSensorManager.getOrientation(rotationMatrix, orientation);
        yaw = orientation[0]*57.2957795f; // radians to degrees
        
        float yaw_angle = event.values[0];
        float pitch_angle = event.values[1];
        float roll_angle = event.values[2];
        TextView tvYaw = (TextView)findViewById(R.id.textViewYaw);
        TextView tvRoll = (TextView)findViewById(R.id.textViewRoll);
        TextView tvPitch = (TextView)findViewById(R.id.textViewPitch);
        tvYaw.setText("Yaw: "+yaw_angle);
        tvPitch.setText("Pitch: "+pitch_angle);
        tvRoll.setText("Roll: "+roll_angle);
        }
    }
    @Override
    protected void onResume() {
      super.onResume();
      mSensorManager.registerListener(this, mCompass, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
      super.onPause();
      mSensorManager.unregisterListener(this);
    }
}
