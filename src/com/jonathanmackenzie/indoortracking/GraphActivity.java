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
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.PointLabeler;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

/**
 * Adapted from the example at androidplot.com
 * http://androidplot.com/docs/dynamically-plotting-sensor-data/
 * @author Jonathan
 *
 */
public class GraphActivity extends Activity implements SensorEventListener {
    private static final int HISTORY_SIZE = 100; // number of points to plot in
                                                 // history
    private SensorManager sensorMgr = null;
    private Sensor orSensor = null;

    private XYPlot aprHistoryPlot = null;
    private DecimalFormat df = new DecimalFormat("##.##");
    private SimpleXYSeries vectorAccelHistorySeries = null;
    private SimpleXYSeries medianAccelHistorySeries = null;
    private SimpleXYSeries meanAccelHistorySeries = null;
    private SimpleXYSeries stepHistorySeries = null;
    private LinkedList<Double> lastAccels, medianAccels, meanAccels;
    private int stepsTaken = 0, meanSteps = 0;
    private double g = 9.81;
    private double stepThreshold = 0.9;
    private static int WINDOW_SIZE = 3;
    private boolean paused = false;
    private boolean meanLabels = true;
    private long lastStep = System.nanoTime(); // When the last step was taken,
                                               // steps take 0.5s
    private long stepTimeout = 500000000l;

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

        stepHistorySeries = new SimpleXYSeries("Steps");
        stepHistorySeries.useImplicitXVals();

        LineAndPointFormatter lpf = new LineAndPointFormatter(null,
                Color.YELLOW, Color.YELLOW,
                new PointLabelFormatter(Color.WHITE));
        // Setting this was not described in the documentation....
        lpf.setPointLabeler(new PointLabeler() {

            public String getLabel(XYSeries s, int idx) {
                return df.format(s.getY(idx));
            }
        });
        lpf.getPointLabelFormatter().getTextPaint().setTextSize(25);
        aprHistoryPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);

        aprHistoryPlot.addSeries(vectorAccelHistorySeries,
                new LineAndPointFormatter(Color.MAGENTA, Color.BLACK, null,
                        null));
        aprHistoryPlot
                .addSeries(medianAccelHistorySeries, new LineAndPointFormatter(
                        Color.WHITE, Color.BLACK, null, null));

        aprHistoryPlot.addSeries(meanAccelHistorySeries,
                new LineAndPointFormatter(Color.BLUE, Color.BLACK, null, null));
        aprHistoryPlot.addSeries(stepHistorySeries, lpf);
        aprHistoryPlot.setRangeBoundaries(-5, 5, BoundaryMode.FIXED);
        aprHistoryPlot.setRangeStepValue(10);
        aprHistoryPlot.setDomainStepValue(10);
        aprHistoryPlot.setTicksPerRangeLabel(1);
        aprHistoryPlot.setTicksPerDomainLabel(5);
        aprHistoryPlot.setDomainLabel("Sample Index");
        aprHistoryPlot.getDomainLabelWidget().pack();
        aprHistoryPlot.setRangeLabel("Accel (m/s/s)");
        aprHistoryPlot.getRangeLabelWidget().pack();

        // aprHistoryPlot.centerOnRangeOrigin(0);

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

        Button bpt = (Button) findViewById(R.id.buttonPlusThreshold);
        Button bmt = (Button) findViewById(R.id.buttonMinusThreshold);

        bmt.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                stepThreshold -= 0.1;
                updateStepThreshold();
            }
        });
        bpt.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                stepThreshold += 0.1;
                updateStepThreshold();
            }
        });
        updateWindowSize();
        updateStepThreshold();
        // Buttons toggle the settings
        ((ToggleButton) findViewById(R.id.buttonPause))
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        paused = isChecked;
                    }
                });
        ((ToggleButton) findViewById(R.id.toggleButtonMeanMedian))
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        meanLabels = isChecked;
                    }
                });

        ((Button) findViewById(R.id.buttonClear))
                .setOnClickListener(new View.OnClickListener() {

                    public void onClick(View arg0) {
                        for (SimpleXYSeries i : new SimpleXYSeries[] {
                                stepHistorySeries, vectorAccelHistorySeries,
                                medianAccelHistorySeries,
                                meanAccelHistorySeries }) {
                            while (i.size() > 0) {
                                i.removeFirst();
                            }

                        }
                        stepsTaken = 0;
                        meanSteps = 0;
                        aprHistoryPlot.redraw();
                        ((TextView) findViewById(R.id.stepsTakenMean))
                                .setText(" Mean Steps:\n " + (meanSteps));
                        ((TextView) findViewById(R.id.stepsTakenMedian))
                                .setText(" Median Steps:\n " + (stepsTaken));
                    }
                });
    }

    private void updateWindowSize() {
        TextView tv = (TextView) findViewById(R.id.windowSizeInput);
        if (WINDOW_SIZE < 1)
            WINDOW_SIZE = 1;
        tv.setText("Window size: " + WINDOW_SIZE);
    }

    private void updateStepThreshold() {
        TextView tv = (TextView) findViewById(R.id.stepThreshold);
        if (stepThreshold < 0.1)
            stepThreshold = 0.1;
        tv.setText("Step threshold: " + df.format(stepThreshold));
    }

    private void cleanup() {
        // unregister with the orientation sensor before exiting:
        sensorMgr.unregisterListener(this);
        finish();
    }

    // Called whenever a new orSensor reading is taken.
    public synchronized void onSensorChanged(SensorEvent sensorEvent) {
        if (paused)
            return;
        // get rid the oldest sample in history:
        if (vectorAccelHistorySeries.size() > HISTORY_SIZE) {
            vectorAccelHistorySeries.removeFirst();
            medianAccelHistorySeries.removeFirst();
            meanAccelHistorySeries.removeFirst();
            stepHistorySeries.removeFirst();
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

        double sum = 0;
        for (Double d : medianList) {
            sum += d;
        }
        double mean = sum / medianList.size();
        meanAccels.addLast(mean);
        while (meanAccels.size() > WINDOW_SIZE) {
            meanAccels.removeFirst();
        }

        // Get the median value
        double median = medianList.get(medianList.size() / 2);
        meanAccelHistorySeries.addLast(null, mean);
        medianAccels.addLast(median);
        while (medianAccels.size() > WINDOW_SIZE) {
            medianAccels.removeFirst();
        }
        medianAccelHistorySeries.addLast(null, median);

        ((TextView) findViewById(R.id.medianVector)).setText(" Median: "
                + df.format(median));
        boolean stepped = false;
        boolean timedOut = System.nanoTime() >= lastStep + stepTimeout;
        if ( /* medianAccels.getLast() - medianAccels.getFirst() <= 0.5 
                &&*/ medianAccels.getLast() > stepThreshold
                && timedOut) {
            ((TextView) findViewById(R.id.stepsTakenMedian))
                    .setText(" Median Steps: \n" + (++stepsTaken));
            stepped = true;
            lastStep = System.nanoTime();
            if (!meanLabels)
                stepHistorySeries.addLast(null,
                        medianAccels.get(medianAccels.size() / 2));
        }

        ((TextView) findViewById(R.id.meanVector)).setText(" Mean: "
                + df.format(mean));
        if (/*meanAccels.getLast() - meanAccels.getFirst() <= 0.5
                &&*/ meanAccels.getLast() > stepThreshold && timedOut) {
            ((TextView) findViewById(R.id.stepsTakenMean))
                    .setText(" Mean Steps:\n " + (++meanSteps));
            stepped = true;
            lastStep = System.nanoTime();
            if (meanLabels)
                stepHistorySeries.addLast(null,
                        medianAccels.getLast());
        }
        if (!stepped)
            stepHistorySeries.addLast(null, null);
        // redraw the Plots:
        aprHistoryPlot.redraw();

    }

    public void onAccuracyChanged(Sensor sensor, int i) {
        // Not interested in this event
    }
}