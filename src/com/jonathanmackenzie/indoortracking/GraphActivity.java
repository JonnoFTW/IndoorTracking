/*
 * Copyright 2013 AndroidPlot.com
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
 *    
 *    Taken from: https://bitbucket.org/androidplot/androidplot/src/b2049f098412f756e9a6717c0dc62dac8260aaca/Examples/DemoApp/src/com/androidplot/demos/DynamicXYPlotActivity.java?at=master
 */

package com.jonathanmackenzie.indoortracking;

import java.text.DecimalFormat;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.androidplot.Plot;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;

public class GraphActivity extends Activity implements SensorEventListener {

    // redraws a plot whenever an update is received:
    private class MyPlotUpdater implements Observer {
        Plot plot;

        public MyPlotUpdater(Plot plot) {
            this.plot = plot;
        }

        public void update(Observable o, Object arg) {
            plot.redraw();
        }
    }

    private XYPlot dynamicPlot;
    private XYPlot staticPlot;
    private MyPlotUpdater plotUpdater;
    SampleDynamicXYDatasource data;
    private Thread myThread;
    private SensorManager mSensorManager;
    private Object mAccelerometer;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        // android boilerplate stuff
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        // get handles to our View defined in layout.xml:
        dynamicPlot = (XYPlot) findViewById(R.id.accelPlot);

        plotUpdater = new MyPlotUpdater(dynamicPlot);

        // only display whole numbers in domain labels
        dynamicPlot.getGraphWidget().setDomainValueFormat(
                new DecimalFormat("0"));

        // getInstance and position datasets:
        data = new SampleDynamicXYDatasource();
        SampleDynamicSeries sine1Series = new SampleDynamicSeries(data, 0,
                "Sine 1");

        dynamicPlot.addSeries(
                sine1Series,
                new LineAndPointFormatter(Color.rgb(0, 0, 0), null, Color.rgb(
                        0, 80, 0),null));

        // create a series using a formatter with some transparency applied:
        LineAndPointFormatter f1 = new LineAndPointFormatter(Color.rgb(0, 0,
                200), null, Color.rgb(0, 0, 80), (PointLabelFormatter) null);

        f1.getFillPaint().setAlpha(220);

        // hook up the plotUpdater to the data model:
        data.addObserver(plotUpdater);

        dynamicPlot.setDomainStepMode(XYStepMode.SUBDIVIDE);
        dynamicPlot.setDomainStepValue(sine1Series.size());

        // thin out domain/range tick labels so they dont overlap each other:
        dynamicPlot.setTicksPerDomainLabel(5);
        dynamicPlot.setTicksPerRangeLabel(3);

        // uncomment this line to freeze the range boundaries:
        dynamicPlot.setRangeBoundaries(-100, 100, BoundaryMode.FIXED);
    }

    /**
     * Set up the {@link android.app.ActionBar}.
     */
    private void setupActionBar() {

        getActionBar().setDisplayHomeAsUpEnabled(true);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.help, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. Use NavUtils to allow users
            // to navigate up one level in the application structure. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        // kick off the data generating thread:
        myThread = new Thread(data);
        myThread.start();
        super.onResume();
    }

    @Override
    public void onPause() {
        data.stopThread();
        super.onPause();
    }

    class SampleDynamicXYDatasource implements Runnable {

        // encapsulates management of the observers watching this datasource for
        // update events:
        class MyObservable extends Observable {
            @Override
            public void notifyObservers() {
                setChanged();
                super.notifyObservers();
            }
        }

        private static final int MAX_AMP_SEED = 100;
        private static final int MIN_AMP_SEED = 10;
        private static final int AMP_STEP = 5;
        public static final int SINE1 = 0;
        public static final int SINE2 = 1;
        private static final int SAMPLE_SIZE = 30;
        private int phase = 0;
        private int sinAmp = 20;
        private MyObservable notifier;
        private boolean keepRunning = false;

        {
            notifier = new MyObservable();
        }

        public void stopThread() {
            keepRunning = false;
        }

        // @Override
        public void run() {
            try {
                keepRunning = true;
                boolean isRising = true;
                while (keepRunning) {

                    Thread.sleep(50); // decrease or remove to speed up the
                                      // refresh rate.
                    phase++;
                    if (sinAmp >= MAX_AMP_SEED) {
                        isRising = false;
                    } else if (sinAmp <= MIN_AMP_SEED) {
                        isRising = true;
                    }

                    if (isRising) {
                        sinAmp += AMP_STEP;
                    } else {
                        sinAmp -= AMP_STEP;
                    }
                    notifier.notifyObservers();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public int getItemCount(int series) {
            return 30;
        }

        public Number getX(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }
            return index;
        }

        public Number getY(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }
            double amp = sinAmp * Math.sin(index + phase + 4);
            switch (series) {
            case SINE1:
                return amp;
            case SINE2:
                return -amp;
            default:
                throw new IllegalArgumentException();
            }
        }

        public void addObserver(Observer observer) {
            notifier.addObserver(observer);
        }

        public void removeObserver(Observer observer) {
            notifier.deleteObserver(observer);
        }

    }

    class SampleDynamicSeries implements XYSeries {
        private SampleDynamicXYDatasource datasource;
        private int seriesIndex;
        private String title;

        public SampleDynamicSeries(SampleDynamicXYDatasource datasource,
                int seriesIndex, String title) {
            this.datasource = datasource;
            this.seriesIndex = seriesIndex;
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public int size() {
            return datasource.getItemCount(seriesIndex);
        }

        public Number getX(int index) {
            return datasource.getX(seriesIndex, index);
        }

        public Number getY(int index) {
            return datasource.getY(seriesIndex, index);
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
        
    }

    public void onSensorChanged(SensorEvent event) {
        // TODO Auto-generated method stub
        
    }
}