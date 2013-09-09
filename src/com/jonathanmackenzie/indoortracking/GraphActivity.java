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

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.collections4.list.FixedSizeList;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.androidplot.util.PlotStatistics;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

// Monitor the phone's orientation sensor and plot the resulting azimuth pitch and roll values.
// See: http://developer.android.com/reference/android/hardware/SensorEvent.html
public class GraphActivity extends Activity implements SensorEventListener {

    /**
     * A simple formatter to convert bar indexes into sensor names.
     */
    private class APRIndexFormat extends Format {
        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo,
                FieldPosition pos) {
            Number num = (Number) obj;

            // using num.intValue() will floor the value, so we add 0.5 to round
            // instead:
            int roundNum = (int) (num.floatValue() + 0.5f);
            switch (roundNum) {
            case 0:
                toAppendTo.append("Azimuth");
                break;
            case 1:
                toAppendTo.append("Pitch");
                break;
            case 2:
                toAppendTo.append("Roll");
                break;
            default:
                toAppendTo.append("Unknown");
            }
            return toAppendTo;
        }

        @Override
        public Object parseObject(String source, ParsePosition pos) {
            return null; // We don't use this so just return null for now.
        }
    }

    private static final int HISTORY_SIZE = 100; // number of points to plot in
                                                 // history
    private SensorManager sensorMgr = null;
    private Sensor orSensor = null;

    private XYPlot aprHistoryPlot = null;

    private SimpleXYSeries aprLevelsSeries = null;
    private SimpleXYSeries vectorAccelHistorySeries = null;
    private SimpleXYSeries medianAccelHistorySeries = null;
    private LinkedList<Double> lastAccels;
    private int stepsTaken = 0;
    private double g=0;
    private static final int WINDOW_SIZE = 3;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // setup the APR Levels plot:

        lastAccels = new LinkedList<Double>();
        aprLevelsSeries = new SimpleXYSeries("APR Levels");
        aprLevelsSeries.useImplicitXVals();

        // setup the APR History plot:
        aprHistoryPlot = (XYPlot) findViewById(R.id.accelHistoryPlot);

        vectorAccelHistorySeries = new SimpleXYSeries("Vector");
        vectorAccelHistorySeries.useImplicitXVals();

        medianAccelHistorySeries = new SimpleXYSeries("Median Filtered");
        medianAccelHistorySeries.useImplicitXVals();

        aprHistoryPlot.setRangeBoundaries(-10, 10, BoundaryMode.FIXED);
        aprHistoryPlot.setDomainStepValue(2);

        aprHistoryPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);

        /*
         * aprHistoryPlot.addSeries(xAccelHistorySeries, new
         * LineAndPointFormatter(Color.rgb(100, 100, 200), Color.BLACK, null,
         * null)); aprHistoryPlot.addSeries(yAccelHistorySeries, new
         * LineAndPointFormatter(Color.rgb(100, 200, 100), Color.BLACK, null,
         * null)); aprHistoryPlot.addSeries(zAccelHistorySeries, new
         * LineAndPointFormatter(Color.rgb(200, 100, 100), Color.BLACK, null,
         * null)); aprHistoryPlot .addSeries(zAccelHistorySeries, new
         * LineAndPointFormatter( Color.YELLOW, Color.BLACK, null, null));
         */
        aprHistoryPlot.addSeries(vectorAccelHistorySeries,
                new LineAndPointFormatter(Color.MAGENTA, Color.BLACK, null,
                        null));
        aprHistoryPlot
                .addSeries(medianAccelHistorySeries, new LineAndPointFormatter(
                        Color.WHITE, Color.BLACK, null, null));
        aprHistoryPlot.setDomainStepValue(2);
        aprHistoryPlot.setTicksPerRangeLabel(1);
        aprHistoryPlot.setDomainLabel("Sample Index");
        aprHistoryPlot.getDomainLabelWidget().pack();
        aprHistoryPlot.setRangeLabel("Accel (m/s/s)");
        aprHistoryPlot.getRangeLabelWidget().pack();

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

    }

    private void cleanup() {
        // aunregister with the orientation sensor before exiting:
        sensorMgr.unregisterListener(this);
        finish();
    }

    // Called whenever a new orSensor reading is taken.
    public synchronized void onSensorChanged(SensorEvent sensorEvent) {

        // update instantaneous data:
        Number[] series1Numbers = { sensorEvent.values[0],
                sensorEvent.values[1], sensorEvent.values[2] };
        aprLevelsSeries.setModel(Arrays.asList(series1Numbers),
                SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

        // get rid the oldest sample in history:
        if (vectorAccelHistorySeries.size() > HISTORY_SIZE) {
            vectorAccelHistorySeries.removeFirst();
            medianAccelHistorySeries.removeFirst();
        }

        // add the latest history sample:
        double vectorAccel = Math.sqrt(sensorEvent.values[0]
                * sensorEvent.values[0] + sensorEvent.values[1]
                * sensorEvent.values[1] + sensorEvent.values[2]
                * sensorEvent.values[2]) -9.81;
        vectorAccelHistorySeries.addLast(null, vectorAccel);
       // Median holds the median of the last 3 vectorAccels
       
        
        lastAccels.addLast(vectorAccel);
        while(lastAccels.size() > WINDOW_SIZE) {
            lastAccels.removeFirst();
        }
        LinkedList<Double> medianList = new LinkedList<Double>(lastAccels);
        Collections.sort(medianList);
        
        // Get the median values
        double median = medianList.get(medianList.size()/2);
        g = 0.9*g + 0.1*median;
        double v = vectorAccel - g;
        medianAccelHistorySeries.addLast(null, v);
       
        ((TextView)findViewById(R.id.medianVector)).setText("Median: "+median);
        ((TextView)findViewById(R.id.medianLowPassVector)).setText("Filtered Median: "+v);
        if(lastAccels.getLast()-lastAccels.getFirst() <= 0.5 && lastAccels.get(lastAccels.size()/2) > 1.8) {
            ((TextView)findViewById(R.id.stepsTaken)).setText("Steps: "+(stepsTaken++));
        }
        // redraw the Plots:
        aprHistoryPlot.redraw();
    }

    public void onAccuracyChanged(Sensor sensor, int i) {
        // Not interested in this event
    }
}