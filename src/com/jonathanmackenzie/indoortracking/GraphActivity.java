/*
 * Copyright 2012 AndroidPlot.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.jonathanmackenzie.indoortracking;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

// Monitor the phone's orientation sensor and plot the resulting azimuth pitch and roll values.
// See: http://developer.android.com/reference/android/hardware/SensorEvent.html
public class GraphActivity extends Activity implements SensorEventListener {
    private static final int HISTORY_SIZE = 100; // number of points to plot in
                                                 // history
    private SensorManager sensorMgr = null;
    private Sensor orSensor = null;

    private XYPlot aprHistoryPlot = null;

    private SimpleXYSeries vectorAccelHistorySeries = null;
    private SimpleXYSeries medianAccelHistorySeries = null;
    private SimpleXYSeries meanAccelHistorySeries = null;
    private LinkedList<Double> lastAccels, medianAccels, meanAccels;
    private int stepsTaken = 0,meanSteps = 0;
    private double g = 9.81;
    private static int WINDOW_SIZE = 3;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // setup the APR Levels plot:

        lastAccels = new LinkedList<Double>();
        medianAccels = new LinkedList<Double>();
        meanAccels = new LinkedList<Double>();

        // setup the APR History plot:
        aprHistoryPlot = (XYPlot) findViewById(R.id.accelHistoryPlot);

        vectorAccelHistorySeries = new SimpleXYSeries("Vector");
        vectorAccelHistorySeries.useImplicitXVals();

        medianAccelHistorySeries = new SimpleXYSeries("Median Filtered");
        medianAccelHistorySeries.useImplicitXVals();
        
        meanAccelHistorySeries = new SimpleXYSeries("Mean Filtered");
        meanAccelHistorySeries.useImplicitXVals();

        aprHistoryPlot.setRangeBoundaries(-10, 10, BoundaryMode.FIXED);
        aprHistoryPlot.setDomainStepValue(2);

        aprHistoryPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);

        aprHistoryPlot.addSeries(vectorAccelHistorySeries,
                new LineAndPointFormatter(Color.MAGENTA, Color.BLACK, null,
                        null));
        aprHistoryPlot
                .addSeries(medianAccelHistorySeries, new LineAndPointFormatter(
                        Color.WHITE, Color.BLACK, null, null));
        
        aprHistoryPlot.addSeries(meanAccelHistorySeries, new LineAndPointFormatter(Color.BLUE,Color.BLACK,null,null));
        
        aprHistoryPlot.setDomainStepValue(2);
        aprHistoryPlot.setTicksPerRangeLabel(1);
        aprHistoryPlot.setDomainLabel("Sample Index");
        aprHistoryPlot.getDomainLabelWidget().pack();
        aprHistoryPlot.setRangeLabel("Accel (m/s/s)");
        aprHistoryPlot.getRangeLabelWidget().pack();
        
   //     aprHistoryPlot.centerOnRangeOrigin(0);

        // register for orientation sensor events:
        sensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        orSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // if we can't access the orientation sensor then exit:
        if (orSensor == null) {
            System.out.println("Failed to attach to orSensor.");
            cleanup();
        }

        sensorMgr.registerListener(this, orSensor,
                SensorManager.SENSOR_DELAY_UI);

        Button bp = (Button) findViewById(R.id.buttonPlusWindow);
        Button bm = (Button) findViewById(R.id.buttonMinusWindow);
        
        bm.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                WINDOW_SIZE--;
                updateWindowSize();
            }
        });
        bp.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                WINDOW_SIZE++;
                updateWindowSize();
            }
        });
        updateWindowSize();

    }

    private void updateWindowSize() {
        TextView tv =(TextView) findViewById(R.id.windowSizeInput);
        if(WINDOW_SIZE < 1)
            WINDOW_SIZE = 1;
        tv.setText("Window size: "+WINDOW_SIZE);
    }
    private void cleanup() {
        // aunregister with the orientation sensor before exiting:
        sensorMgr.unregisterListener(this);
        finish();
    }

    // Called whenever a new orSensor reading is taken.
    public synchronized void onSensorChanged(SensorEvent sensorEvent) {
        // get rid the oldest sample in history:
        if (vectorAccelHistorySeries.size() > HISTORY_SIZE) {
            vectorAccelHistorySeries.removeFirst();
            medianAccelHistorySeries.removeFirst();
            meanAccelHistorySeries.removeFirst();
        }

        // add the latest history sample:
        double vectorAccel = Math.sqrt(sensorEvent.values[0]
                * sensorEvent.values[0] + sensorEvent.values[1]
                * sensorEvent.values[1] + sensorEvent.values[2]
                * sensorEvent.values[2]);

        // Median holds the median of the last 3 vectorAccels
        // Apply a low pass filter to remove gravity
        g = 0.9 * g + 0.1 * vectorAccel;
        double v = vectorAccel - g;
        vectorAccelHistorySeries.addLast(null, v);
        lastAccels.addLast(v);
        while (lastAccels.size() > WINDOW_SIZE) {
            lastAccels.removeFirst();
        }
        // Use an extra list to get the median value because we need to sort the
        // data
        LinkedList<Double> medianList = new LinkedList<Double>(lastAccels);
        Collections.sort(medianList);

        double sum= 0;
        for (Double d : medianList) {
            sum += d;
        }
        double mean = sum /medianList.size();
        meanAccels.addLast(mean);
        while (meanAccels.size() > WINDOW_SIZE) {
            meanAccels.removeFirst();
        }
       
        // Get the median value
        double median = medianList.get(medianList.size()/2);
        meanAccelHistorySeries.addLast(null, mean);
        medianAccels.addLast(median);
        while (medianAccels.size() > WINDOW_SIZE) {
            medianAccels.removeFirst();
        }
        medianAccelHistorySeries.addLast(null, median);
        DecimalFormat df = new DecimalFormat("##.##");
       
        ((TextView) findViewById(R.id.medianVector)).setText(" Median: "
                +  df.format(median));
        if (medianAccels.getLast() - medianAccels.getFirst() <= 0.5
                && medianAccels.get(medianAccels.size() / 2) > 1.8) {
            ((TextView) findViewById(R.id.stepsTaken)).setText(" Median Steps: "
                    + (stepsTaken++));

        }
        
        ((TextView) findViewById(R.id.meanVector)).setText(" Mean: "
                +  df.format(mean));
        if (meanAccels.getLast() - meanAccels.getFirst() <= 0.5
                && meanAccels.get(meanAccels.size() / 2) > 1.3) {
            ((TextView) findViewById(R.id.stepsTakenMean)).setText(" Mean Steps: "
                    + (meanSteps++));

        }
        // redraw the Plots:
        aprHistoryPlot.redraw();
    }

    public void onAccuracyChanged(Sensor sensor, int i) {
        // Not interested in this event
    }
}