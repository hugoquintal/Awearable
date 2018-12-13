package com.example.administrator.awearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

import com.example.administrator.awearable.db.TableDataSource;
import com.example.administrator.awearable.model.Action;

import java.util.Date;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class AnalogWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "tes - WatchFace";

    //Update rate in milliseconds for interactive mode. We update once a second to advance the second hand.
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static Timer t;

    @Override
    public Engine onCreateEngine() {
        //Create BD and Open Connection
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine implements SensorEventListener { //TODO METER AKI IMPLEMENTACOES DOS SENSORES
        static final int MSG_UPDATE_TIME = 0;

        //TODO GLOBAL VARS 1 MAYBE DONT INICIALIZE VARS HERE AND DO IT ON INITIALIZEWATCHFACE FUNCTION

        Paint mHourPaint, mMinutePaint, mSecondPaint, mCirclePaint, mBackgroundColorPaint;
        Paint mDefault, mCurrentStepsPaint, mCurrentInactivePaint, mCurrentRunPaint, mCurrentStepsPaintLowBit, mCurrentInactivePaintLowBit, mCurrentRunPaintLowBit;
        Paint mCurrentStepsPaintComplete, mCurrentRunPaintComplete,mCurrentInactivePaintComplete;
        boolean mMute;
        Time mTime; // var that will be used to save current time
        int width, height; // vars that will store width and height of the device display
        String watch = "Analog"; // var that will store  type of watch being used(by default the watch starts as Digital)
        TableDataSource dataSource;
        long idGlobal = 0; // last action ID created (delete if not used and change function that gives it value)
        boolean interactiveRowCreated = false; // var that is used to only permit ambient glance to be stored if there is a interactive glance stored before
        long lastTapTime = 0; //stores the last time where the used taped on the display
        public PowerManager.WakeLock wakeL, wl; // used to "wake up" watch
        boolean w = true; // used to know when a wakelock was used in order to release it
        private int mTouchCommandTotal = 0, mTouchCancelCommandTotal = 0, mTapCommandTotal = 0; // stores the number of taps of each type


        //TODO meter valores a 0 dpx menos o dGoalSteps
        String drawInterface = "simpleGoal";

        int StepNumber = 10000;
        int counterSteps;
        int auxStepsNumber;

        int runningsteps;
        double runningkm;
        int inactiveTime;
        double inactiveTimeMin;


        int dGoalSteps = 12000; //10000 passos diários
        double dRunTime = 5500; //4 km em passos
        int dInacTime = 3600; //60 min em s
        int tapnumber = 0;


        float sGBarStepsExter = 0.085f;         //0.065
        float sGBarRunExter = 0.175f;           //0.075
        float sGBarInactiveExter = 0.265f;      //0.109

        float sGBarStepsInter = 0.055f;
        float sGBarRunInter = 0.145f;
        float sGBarInactiveInter = 0.235f;


        private SensorManager mSensorManager;
        private Sensor mStepCountSensor;

        //Handler to update the time once a second in interactive mode.
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };


        public synchronized void startPollingTimer() {
            if (t == null) {
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        updaterun();
                        updateinactive();
                        auxStepsNumber = StepNumber;
                    }
                };

                t = new Timer();
                t.scheduleAtFixedRate(task, 0, 30000);
            }
        }

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();

            }
        };


        boolean mRegisteredTimeZoneReceiver = false;



        //TODO GLOBAL VARS 2
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */

        boolean mLowBitAmbient;

        //-----------------------------------------Methods Regarding WatchFaceService---------------------------------

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);
            Log.d(TAG, "START");

            //add tap events, etc.... // change position of watchface base icons(charging, connection to handheld, etc...)
            setWatchFaceStyle(new WatchFaceStyle.Builder(AnalogWatchFaceService.this)
                    .setAcceptsTapEvents(true)
                    .build());

            //initialize sensors
            mSensorManager = ((SensorManager) getSystemService(Context.SENSOR_SERVICE));
            getStepCount();

            //start time var
            mTime = new Time();
            mTime.setToNow();
            long currentTime = mTime.toMillis(false);
            //TODO MAYBE USE THIS IF NOT WANT TO USE DEPRECATED TIME
            //long currentTime= System.currentTimeMillis();

            //Create BD and Open Connection
            dataSource = new TableDataSource(getApplicationContext());
            dataSource.open();

            // initialize drawables and paints that later will be used on onDrawMethod and other global vars
            initializeWatchFace();
            startPollingTimer();
        }

        // Method called when the app is closed
        @Override
        public void onDestroy() {
            //Log.d("tick", "ON DESTROY CALLED");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mSensorManager.unregisterListener(this);
            //Close Connection
            dataSource.close();
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            /* get device features (burn-in, low-bit ambient) */

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);
            }
        }

        // Method called each minute the time changed
        @Override
        public void onTimeTick() {
            super.onTimeTick();

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }

            mTime.setToNow();
            long tempTime = mTime.toMillis(false);

            // redraw canvas
            invalidate();

        }

        // Method called when watch changes from ambient to interactive mode and the other way around
        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            /* the wearable switched between modes */

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
            }

            // Local vars
            String actionGlobal = ""; // type of last glance (interactive or ambient)
            long otherAppsAccess = 0;


            mTime.setToNow();
            long tempTime = mTime.toMillis(false);


            if (!isInAmbientMode()) { //display is in interactive mode
                Log.d(TAG, "INTERACTIVE MODE");
                actionGlobal = "interactive";
                lastTapTime = tempTime;

                //setPaintValues();

                if (!w) { // if a wake lock was initiated on ambient mode then release it here
                    if (wakeL != null) {
                        wakeL.release();
                        wakeL = null;
                    }
                }
                w = true;

            } else { //display is in ambient mode
                Log.d(TAG, "AMBIENT MODE");
                actionGlobal = "ambient";

                //force "wake" watch on ambient mode to try and get sensor values when device is "sleeping"
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "My Tag");
                wl.acquire();
                wakeL = wl; //global var that stores the value of w1 to be able to release it after
                //TODO UNREGISTER SENSORS
                mSensorManager.unregisterListener(this);
                mSensorManager.registerListener(this, mStepCountSensor, SensorManager.SENSOR_DELAY_FASTEST);
                w = false; // allow to release wakeL in the next interactive mode

                //if passed more than 6 sec since last tap on smartwatch untill it turned in ambient mode then user is probably accessing other apps
                if (tempTime > (lastTapTime + 11000l)) {
                    otherAppsAccess = 1;
                    //Log.d("sql", "w1AcquireTime:" + w1AcquireTime + " lastTapTime:" + lastTapTime);
                }
            }


            //INSERT ON DB NEW ACTION
            long timeGlance = mTime.toMillis(false);
            if ((interactiveRowCreated && actionGlobal.equals("ambient"))) {
                idGlobal = insertRowActions(actionGlobal, timeGlance, otherAppsAccess);
                interactiveRowCreated = false;
            } else if (!interactiveRowCreated && actionGlobal.equals("interactive")) {
                idGlobal = insertRowActions(actionGlobal, timeGlance, otherAppsAccess);
                interactiveRowCreated = true;
            }

            // TODO TrY INVALIDATE AFTER UPDATETIMER?
            // call onDraw method to redraw interface
            invalidate();

            /* Whether the timer should be running depends on whether we're in ambient mode (as well
            as whether we're visible), so we may need to start or stop the timer. */
            updateTimer();
        }

        // Method called every time the user taps the display
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Tap Command: " + tapType);
            }

            mTime.setToNow();
            lastTapTime = mTime.toMillis(false);
            //mTouchCoordinateX = x;
            //mTouchCoordinateY = y;

            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    mTouchCommandTotal++;
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    mTouchCancelCommandTotal++;
                    break;
                case TAP_TYPE_TAP:
                    mTapCommandTotal++;
                    tapnumber = mTapCommandTotal;
                    if (mTapCommandTotal > 3)
                        mTapCommandTotal = 0;
                    break;
            }
            Log.d(TAG, "Tap Command:" + tapType + " mTouchCommandTotal:" + mTouchCommandTotal + " mTouchCancelCommandTotal:" + mTouchCancelCommandTotal + " mTapCommandTotal;" + mTapCommandTotal);
            invalidate();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);

                // call onDraw method to redraw interface
                invalidate();
            }
        }

        // Method that draws the interface (use invalidate() to force call it)
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            long tempTime = mTime.toMillis(false);
            //Log.d("draw", "DRAW AT: " + mTime.minute + ":" + mTime.second + " - milis = " + tempTime);
            //Log.d("tickDraw", " " + StepNumberTeste);

            width = bounds.width();
            height = bounds.height();

            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundColorPaint);
            /*Find the center. Ignore the window insets so that, on round watches with a "chin",
            the watch face is centered on the entire screen, not just the usable portion.*/
            float centerX = width / 2f;
            float centerY = height / 2f;

            int dG = dGoalSteps / 120;
            double dR = dRunTime / 120;
            int dI = dInacTime / 120;

            int nLineDrawS;
            double nLineDrawR;
            long nLineDrawI;
            float rotS, rotR, rotI, pX1S, pY1S, pX2S, pY2S, pX1R, pY1R, pX2R, pY2R, pX1I, pY1I, pX2I, pY2I;


            if (drawInterface.equals("simpleGoal")) {

                //performance draw
                float externalPointS = centerX - (int) (sGBarStepsExter * width); //0.065
                float externalPointR = centerX - (int) (sGBarRunExter * width); //0.075
                float externalPointI = centerX - (int) (sGBarInactiveExter * width); //0.109

                float internalPointS = centerX - (int) (sGBarStepsInter * width);
                float internalPointR = centerX - (int) (sGBarRunInter * width);
                float internalPointI = centerX - (int) (sGBarInactiveInter * width);

                //Log.d(TAG, "DRAW baseline - stepnumber - dgoalsteps" + StepNumber + " - " + dGoalSteps);
                if ((StepNumber) <= dGoalSteps || (runningsteps) <= dRunTime || (inactiveTime <= dInacTime)) {

                        if (tapnumber == 1) {
                            //steps text
                            mCurrentStepsPaint.setTypeface(Typeface.create("Roboto", Typeface.BOLD));
                            mCurrentStepsPaint.setTextSize(20);
                            String S = "S: " + (StepNumber);
                            float wS = mCurrentStepsPaint.measureText(S) / 2;
                            canvas.drawText(S, centerX - wS, height * 0.6f, mCurrentStepsPaint);
                        } else if (tapnumber == 2) {
                            mCurrentRunPaint.setTypeface(Typeface.create("Roboto", Typeface.BOLD));
                            mCurrentRunPaint.setTextSize(20);
                            String R = "R: " + (runningkm) + " km";
                            float wR = mCurrentRunPaint.measureText(R) / 2;
                            canvas.drawText(R, centerX - wR, height * 0.6f, mCurrentRunPaint);
                        } else if (tapnumber == 3) {
                            //inactive text
                            mCurrentInactivePaint.setTypeface(Typeface.create("Roboto", Typeface.BOLD));
                            mCurrentInactivePaint.setTextSize(20);
                            String I = "I: " + (inactiveTimeMin) + " min";
                            float wI = mCurrentInactivePaint.measureText(I) / 2;
                            canvas.drawText(I, centerX - wI, height * 0.6f, mCurrentInactivePaint);
                        }

                            nLineDrawS = Math.round((StepNumber) / dG);
                            nLineDrawR = Math.round((runningsteps) / dR);
                            nLineDrawI = Math.round((inactiveTime) / dI);

                            for (int i = 0; i < nLineDrawS; i++) {
                                //circle for steps
                                rotS = (float) i / 60f * (float) Math.PI;
                                pX1S = (float) Math.sin(rotS) * externalPointS;
                                pY1S = (float) -Math.cos(rotS) * externalPointS;
                                pX2S = (float) Math.sin(rotS) * internalPointS;
                                pY2S = (float) -Math.cos(rotS) * internalPointS;
                                canvas.drawLine(centerX + pX2S, centerY + pY2S, centerX + pX1S, centerY + pY1S, mCurrentStepsPaint);
                            }
                            for (int j = 0; j < nLineDrawR; j++) {
                                //circle for run
                                rotR = (float) j / 60f * (float) Math.PI;
                                pX1R = (float) Math.sin(rotR) * externalPointR;
                                pY1R = (float) -Math.cos(rotR) * externalPointR;
                                pX2R = (float) Math.sin(rotR) * internalPointR;
                                pY2R = (float) -Math.cos(rotR) * internalPointR;
                                canvas.drawLine(centerX + pX2R, centerY + pY2R, centerX + pX1R, centerY + pY1R, mCurrentRunPaint);
                            }
                            for (int k = 0; k < nLineDrawI; k++) {
                                //circle for inactive
                                rotI = (float) k / 60f * (float) Math.PI;
                                pX1I = (float) Math.sin(rotI) * externalPointI;
                                pY1I = (float) -Math.cos(rotI) * externalPointI;
                                pX2I = (float) Math.sin(rotI) * internalPointI;
                                pY2I = (float) -Math.cos(rotI) * internalPointI;
                                canvas.drawLine(centerX + pX2I, centerY + pY2I, centerX + pX1I, centerY + pY1I, mCurrentInactivePaint);
                            }

                } else {//IF DAILY GOAL IS COMPLETED ALREADY

                    nLineDrawS = Math.round((StepNumber) / dG);
                    nLineDrawR = Math.round((runningsteps) / dR);
                    nLineDrawI = Math.round((inactiveTime) / dI);


                    for (int i = 0; i < nLineDrawS; i++) {
                        //circle for steps
                        rotS = (float) i / 60f * (float) Math.PI;
                        pX1S = (float) Math.sin(rotS) * externalPointS;
                        pY1S = (float) -Math.cos(rotS) * externalPointS;
                        pX2S = (float) Math.sin(rotS) * internalPointS;
                        pY2S = (float) -Math.cos(rotS) * internalPointS;
                        canvas.drawLine(centerX + pX2S, centerY + pY2S, centerX + pX1S, centerY + pY1S, mCurrentStepsPaintComplete);
                    }

                    if (runningsteps > dRunTime) {
                        for (int j = 0; j < nLineDrawR; j++) {
                            //circle for run
                            rotR = (float) j / 60f * (float) Math.PI;
                            pX1R = (float) Math.sin(rotR) * externalPointR;
                            pY1R = (float) -Math.cos(rotR) * externalPointR;
                            pX2R = (float) Math.sin(rotR) * internalPointR;
                            pY2R = (float) -Math.cos(rotR) * internalPointR;
                            canvas.drawLine(centerX + pX2R, centerY + pY2R, centerX + pX1R, centerY + pY1R, mCurrentRunPaintComplete);
                        }
                    }
                    for (int k = 0; k < nLineDrawI; k++) {
                        //circle for inactive
                        rotI = (float) k / 60f * (float) Math.PI;
                        pX1I = (float) Math.sin(rotI) * externalPointI;
                        pY1I = (float) -Math.cos(rotI) * externalPointI;
                        pX2I = (float) Math.sin(rotI) * internalPointI;
                        pY2I = (float) -Math.cos(rotI) * internalPointI;
                        canvas.drawLine(centerX + pX2I, centerY + pY2I, centerX + pX1I, centerY + pY1I, mCurrentInactivePaintComplete);
                    }

                    //draws the bg circles while in active mode

                    nLineDrawS = Math.round((dGoalSteps) / dG);
                    nLineDrawR = Math.round((dRunTime) / dR);
                    nLineDrawI = Math.round((dInacTime) / dI);

                    for (int i = 0; i < nLineDrawS; i++) {
                        //bg circle for steps
                        rotS = (float) i / 60f * (float) Math.PI;
                        pX1S = (float) Math.sin(rotS) * externalPointS;
                        pY1S = (float) -Math.cos(rotS) * externalPointS;
                        pX2S = (float) Math.sin(rotS) * internalPointS;
                        pY2S = (float) -Math.cos(rotS) * internalPointS;
                        canvas.drawLine(centerX + pX2S, centerY + pY2S, centerX + pX1S, centerY + pY1S, mCurrentStepsPaintLowBit);
                    }
                    for (int j = 0; j < nLineDrawR; j++) {
                        //bg circle for run
                        rotR = (float) j / 60f * (float) Math.PI;
                        pX1R = (float) Math.sin(rotR) * externalPointR;
                        pY1R = (float) -Math.cos(rotR) * externalPointR;
                        pX2R = (float) Math.sin(rotR) * internalPointR;
                        pY2R = (float) -Math.cos(rotR) * internalPointR;
                        canvas.drawLine(centerX + pX2R, centerY + pY2R, centerX + pX1R, centerY + pY1R, mCurrentRunPaintLowBit);
                    }
                    for (int k = 0; k < nLineDrawI; k++) {
                        //bg circle for inactive
                        rotI = (float) k / 60f * (float) Math.PI;
                        pX1I = (float) Math.sin(rotI) * externalPointI;
                        pY1I = (float) -Math.cos(rotI) * externalPointI;
                        pX2I = (float) Math.sin(rotI) * internalPointI;
                        pY2I = (float) -Math.cos(rotI) * internalPointI;
                        canvas.drawLine(centerX + pX2I, centerY + pY2I, centerX + pX1I, centerY + pY1I, mCurrentInactivePaintLowBit);
                    }
                }

                if (watch.equals("Digital")) { // if digital watch is picked
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(80);
                    String s1;

                    String sH = "" + mTime.hour;
                    String sM = "" + mTime.minute;
                    if (mTime.hour <= 9)
                        sH = "0" + mTime.hour;
                    if (mTime.minute <= 9)
                        sM = "0" + mTime.minute;

                    s1 = sH + ":" + sM;
                    float w = paint.measureText(s1) / 2;
                    canvas.drawText(s1, centerX - w, height * 0.55f, paint);

                } else { // if Analog watch is picked
                    // Draw the ticks.

                    float secRot = mTime.second / 30f * (float) Math.PI;
                    int minutes = mTime.minute;
                    float minRot = minutes / 30f * (float) Math.PI;
                    float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

                    float secLength = centerX - width * 0.97f;
                    float minLength = centerX - width * 0.130f;
                    float hrLength = centerX - width * 0.22f;


                    float innerTickRadius = centerX - width * 0.03125f;
                    float outerTickRadius = centerX;
                    for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                        float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                        float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                        float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                        float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                        float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                    }

                    //draws bg circles while in ambient mode

                    nLineDrawS = Math.round((dGoalSteps) / dG);
                    nLineDrawR = Math.round((dRunTime) / dR);
                    nLineDrawI = Math.round((dInacTime) / dI);

                    for (int i = 0; i < nLineDrawS; i++) {
                        //bg circle for steps
                        rotS = (float) i / 60f * (float) Math.PI;
                        pX1S = (float) Math.sin(rotS) * externalPointS;
                        pY1S = (float) -Math.cos(rotS) * externalPointS;
                        pX2S = (float) Math.sin(rotS) * internalPointS;
                        pY2S = (float) -Math.cos(rotS) * internalPointS;
                        canvas.drawLine(centerX + pX2S, centerY + pY2S, centerX + pX1S, centerY + pY1S, mCurrentStepsPaintLowBit);
                    }
                    for (int j = 0; j < nLineDrawR; j++) {
                        //bg circle for run
                        rotR = (float) j / 60f * (float) Math.PI;
                        pX1R = (float) Math.sin(rotR) * externalPointR;
                        pY1R = (float) -Math.cos(rotR) * externalPointR;
                        pX2R = (float) Math.sin(rotR) * internalPointR;
                        pY2R = (float) -Math.cos(rotR) * internalPointR;
                        canvas.drawLine(centerX + pX2R, centerY + pY2R, centerX + pX1R, centerY + pY1R, mCurrentRunPaintLowBit);
                    }
                    for (int k = 0; k < nLineDrawI; k++) {
                        //bg circle for inactive
                        rotI = (float) k / 60f * (float) Math.PI;
                        pX1I = (float) Math.sin(rotI) * externalPointI;
                        pY1I = (float) -Math.cos(rotI) * externalPointI;
                        pX2I = (float) Math.sin(rotI) * internalPointI;
                        pY2I = (float) -Math.cos(rotI) * internalPointI;
                        canvas.drawLine(centerX + pX2I, centerY + pY2I, centerX + pX1I, centerY + pY1I, mCurrentInactivePaintLowBit);
                    }

                    //draws the goals indications when ambient display is on
                    nLineDrawS = Math.round((StepNumber) / dG);
                    nLineDrawR = Math.round((runningsteps) / dR);
                    nLineDrawI = Math.round((inactiveTime) / dI);
                    float rotS2 = 0f, rotR2 = 0f, rotI2 = 0f;

                    for (int i = 0; i < nLineDrawS; i++)
                        //circle for steps
                        rotS2 = (float) i / 60f * (float) Math.PI;
                    pX1S = (float) Math.sin(rotS2) * externalPointS;
                    pY1S = (float) -Math.cos(rotS2) * externalPointS;
                    pX2S = (float) Math.sin(rotS2) * internalPointS;
                    pY2S = (float) -Math.cos(rotS2) * internalPointS;

                    for (int j = 0; j < nLineDrawR; j++)
                        //circle for run
                        rotR2 = (float) j / 60f * (float) Math.PI;
                    pX1R = (float) Math.sin(rotR2) * externalPointR;
                    pY1R = (float) -Math.cos(rotR2) * externalPointR;
                    pX2R = (float) Math.sin(rotR2) * internalPointR;
                    pY2R = (float) -Math.cos(rotR2) * internalPointR;

                    for (int k = 0; k < nLineDrawI; k++)
                        //circle for inactive
                        rotI2 = (float) k / 60f * (float) Math.PI;
                    pX1I = (float) Math.sin(rotI2) * externalPointI;
                    pY1I = (float) -Math.cos(rotI2) * externalPointI;
                    pX2I = (float) Math.sin(rotI2) * internalPointI;
                    pY2I = (float) -Math.cos(rotI2) * internalPointI;


                    canvas.drawLine(centerX + pX2S, centerY + pY2S, centerX + pX1S, centerY + pY1S, mCurrentStepsPaint);
                    canvas.drawLine(centerX + pX2R, centerY + pY2R, centerX + pX1R, centerY + pY1R, mCurrentRunPaint);
                    canvas.drawLine(centerX + pX2I, centerY + pY2I, centerX + pX1I, centerY + pY1I, mCurrentInactivePaint);


                    //draw main pointers
                    if (!isInAmbientMode()) { //show only seconds bar if on interactive mode
                        float secX = (float) Math.sin(secRot) * secLength;
                        float secY = (float) -Math.cos(secRot) * secLength;
                        canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mSecondPaint);//draw sec pointer
                    }

                    float minX = (float) Math.sin(minRot) * minLength;
                    float minY = (float) -Math.cos(minRot) * minLength;
                    canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mMinutePaint); //draw minute pointer

                    float hrX = (float) Math.sin(hrRot) * hrLength;
                    float hrY = (float) -Math.cos(hrRot) * hrLength;
                    canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHourPaint); //draw hour pointer

                    canvas.drawCircle(centerX, centerY, (int) (0.023 * width), mCirclePaint); // around 3% of the display size (ex: 10 pixeis on a 320 display)
                }
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            /* the watch face became visible or invisible */

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible);
            }

            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        //----------------------------------Base Methods Regarding Sensors-----------------------------

        private void getStepCount() {
            //Sensor mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            mStepCountSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            //Sensor mStepDetectSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            //mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(this, mStepCountSensor, SensorManager.SENSOR_DELAY_FASTEST);
            //mSensorManager.registerListener(this, mStepDetectSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }


        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
        }

        private long sensorTimeReference = 0l;
        private long myTimeReference = 0l;


        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                if (counterSteps < 1) {
                    counterSteps = (int) event.values[0];
                }

                StepNumber = (int) event.values[0] - counterSteps;
            }
        }

        //----------------------------------Base Methods Regarding Time---------------------------------
        private void updaterun() {
            int steprun;
            steprun = StepNumber - auxStepsNumber;
            if(steprun>45) {            //máximo de movimento de 35 passos por 30s para não ser considerado corrida
                runningsteps += steprun;
            }
            steprun=0;
            runningkm = (double) runningsteps/1312;         //1312 aproximadamente o valor de passos num km
        }

        private void updateinactive() {
            int inacsteps;
            inacsteps = StepNumber - auxStepsNumber;
            if(inacsteps<15) {          //mínimo de movimento de 20 passos por 30s para não ser considerado inativo
                if(inacsteps == 0)
                {
                    inacsteps += 20;
                }
                inactiveTime += inacsteps;
            }
            inacsteps=0;
            inactiveTimeMin = inactiveTime/60;       //
        }


        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            AnalogWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            AnalogWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /*
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /*
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        //-------------------------------------------Global Functions -----------------------------------

        // initialize drawables and paints that later will be used on onDrawMethod and other global vars
        private void initializeWatchFace() {
            Resources resources = AnalogWatchFaceService.this.getResources();
            //Drawable backgroundDrawable = resources.getDrawable(R.drawable.bg);
            //mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();


            //Drawable mCircleDrawable = resources.getDrawable(R.drawable.middlecircle);
            //mCircleBitmap = ((BitmapDrawable) mCircleDrawable).getBitmap();

            mBackgroundColorPaint = new Paint();
            mHourPaint = new Paint();
            mMinutePaint = new Paint();
            mSecondPaint = new Paint();
            mCirclePaint = new Paint();

            //TODO QUANDO DA RESET VOLTA drawInterfaceAOS VALORES INICIAIS???? TLX CRIAR DB PARA GUARDAR VALORES

            mBackgroundColorPaint.setARGB(255, 0, 0, 0);

            mHourPaint.setARGB(255, 244, 255, 255);
            mHourPaint.setAlpha(200);
            mHourPaint.setStrokeWidth(8.f);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);

            mMinutePaint.setARGB(255, 37, 92, 240);
            mMinutePaint.setAlpha(170);
            mMinutePaint.setStrokeWidth(6.f);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);

            mSecondPaint.setARGB(255, 144, 202, 249);
            mSecondPaint.setStrokeWidth(2.f);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);

            mCirclePaint.setARGB(140, 255, 255, 255);

            mCurrentStepsPaint = new Paint();
            mCurrentStepsPaint.setARGB(255, 33, 175, 114);
            mCurrentStepsPaint.setStrokeWidth(15f);
            mCurrentStepsPaint.setAntiAlias(true);
            mCurrentStepsPaint.setStrokeCap(Paint.Cap.ROUND);

            mCurrentRunPaint = new Paint();
            mCurrentRunPaint.setARGB(255, 30, 135, 181);
            mCurrentRunPaint.setStrokeWidth(15f);
            mCurrentRunPaint.setAntiAlias(true);
            mCurrentRunPaint.setStrokeCap(Paint.Cap.ROUND);


            mCurrentInactivePaint = new Paint();
            mCurrentInactivePaint.setARGB(255, 154, 3, 30);
            mCurrentInactivePaint.setStrokeWidth(15f);
            mCurrentInactivePaint.setAntiAlias(true);
            mCurrentInactivePaint.setStrokeCap(Paint.Cap.ROUND);


            mCurrentStepsPaintLowBit = new Paint();
            mCurrentStepsPaintLowBit.setARGB(22, 33, 175, 114);
            mCurrentStepsPaintLowBit.setStrokeWidth(8f);
            mCurrentStepsPaintLowBit.setAntiAlias(true);


            mCurrentRunPaintLowBit = new Paint();
            mCurrentRunPaintLowBit.setARGB(22, 30, 135, 181);
            mCurrentRunPaintLowBit.setStrokeWidth(8f);
            mCurrentRunPaintLowBit.setAntiAlias(true);

            mCurrentInactivePaintLowBit = new Paint();
            mCurrentInactivePaintLowBit.setARGB(22, 154, 3, 30);
            mCurrentInactivePaintLowBit.setStrokeWidth(8f);
            mCurrentInactivePaintLowBit.setAntiAlias(true);

            mCurrentStepsPaintComplete = new Paint();
            mCurrentStepsPaintComplete.setARGB(255, 254, 245, 252);
            mCurrentStepsPaintComplete.setStrokeWidth(15f);
            mCurrentStepsPaintComplete.setAntiAlias(true);
            mCurrentStepsPaintComplete.setStrokeCap(Paint.Cap.ROUND);

            mCurrentRunPaintComplete = new Paint();
            mCurrentRunPaintComplete.setARGB(255, 254, 245, 252);
            mCurrentRunPaintComplete.setStrokeWidth(15f);
            mCurrentRunPaintComplete.setAntiAlias(true);
            mCurrentRunPaintComplete.setStrokeCap(Paint.Cap.ROUND);

            mCurrentInactivePaintComplete = new Paint();
            mCurrentInactivePaintComplete.setARGB(255, 254, 245, 252);
            mCurrentInactivePaintComplete.setStrokeWidth(15f);
            mCurrentInactivePaintComplete.setAntiAlias(true);
            mCurrentInactivePaintComplete.setStrokeCap(Paint.Cap.ROUND);

        }


        // change colour patterns of the interface picked
        public void setPaintValues() {
            //todo acede a net/busca string com mudanças/devide string em partes

            int a, r, g, b; // hour pointer argb values
            a = 255; r = 0; g = 0; b = 0; //TODO em vez de valores meter =à var k vamos dividir em pedacos
            mBackgroundColorPaint.setARGB(a, r, g, b);
            a = 255; r = 255; g = 255; b = 255; //TODO proximos valores
            mHourPaint.setARGB(a, r, g, b);
            a = 255; r = 255; g = 255; b = 255; //TODO proximos valores
            mMinutePaint.setARGB(a, r, g, b);
            a = 255; r = 255; g = 0; b = 0; //TODO proximos valores
            mSecondPaint.setARGB(a, r, g, b);
            a = 100; r = 255; g = 255; b = 255; //TODO proximos valores
            mCirclePaint.setARGB(a, r, g, b);

            if (drawInterface.equals("simpleGoal")) { // TODO tlx tirar isto
                a = 255; r = 93; g = 181; b = 166; //TODO proximos valores
                mCurrentStepsPaint.setARGB(a, r, g, b);
                sGBarStepsInter = 0.5f; // TODO meter = ao valor k foi dado
            }


        }



        // insert info regarding the beginning or end of a glance (action)
        public long insertRowActions(String a, long t, long otherAppsAccess) {
            Action action = new Action();
            action.setAction(a);
            action.setTime(t);
            action.setSet(0L);
            action.setAccessOtherApps(otherAppsAccess);
            action = dataSource.createAction(action);
            Date d = new Date(t);
            Log.d(TAG, "Action Row Created with id " + action.getId() + " Action: " + a  + "OtherAppsAccess: " + otherAppsAccess + " Time: " + d);
            return action.getId();
        }

    }
}






