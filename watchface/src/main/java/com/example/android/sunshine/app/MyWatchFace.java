/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    private static final String LOG_TAG = MyWatchFace.class.getSimpleName();

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for active mode (non-ambient).
     */
    private static final long ACTIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);


    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


        private static final int COLOR_TEXT_HOURS_MINS = Color.WHITE;
        private static final int COLOR_TEXT_AM_PM = Color.WHITE;
        private static final int COLOR_TEXT_COLON = Color.WHITE;


        private static final String COLON_STRING = ":";

        private static final int MSG_UPDATE_TIME = 0;

        /* Handler to update the time periodically in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        Log.v(LOG_TAG, "updating time");
                        invalidate();
                        if (shouldUpdateTimeHandlerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    ACTIVE_INTERVAL_MS - (timeMs % ACTIVE_INTERVAL_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };


        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mAmPmPaint;
        private Paint mColonPaint;
        private Paint mIconPaint;
        private Paint mHighPaint;
        private Paint mLowPaint;

        private float mColonWidth;

        private Calendar mCalendar;


        private float mTimeYOffset;
        private float mIconYOffset;
        private float mTemperatureYOffset;
        private double mIconMultiplier;


        private String mAmString;
        private String mPmString;
        private boolean mAmbientMode = false;
        private String mHighTemperature;
        private String mLowTemperature;
        private int mWeatherId;
        GoogleApiClient googleApiClient;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;


        @Override
        public void onConnected(Bundle bundle) {
        }

        @Override
        public void onConnectionSuspended(int i) {
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
        }


        public class MessageReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {

                if (intent.getExtras() != null && intent.getExtras().containsKey("message")) {
                    String message = intent.getStringExtra("message");
                    String[] splitMessage = message.split("\\s+");
                    mHighTemperature = splitMessage[0];
                    mLowTemperature = splitMessage[1];
                    mWeatherId = Integer.parseInt(splitMessage[2]);
                } else {
                    mCalendar.setTimeZone(TimeZone.getDefault());
                }
                invalidate();
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {

            googleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            googleApiClient.connect();

            super.onCreate(holder);


            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = getResources();


            mAmString = resources.getString(R.string.AM);
            mPmString = resources.getString(R.string.PM);

            mHourPaint = createTextPaint(COLOR_TEXT_HOURS_MINS);
            mMinutePaint = createTextPaint(COLOR_TEXT_HOURS_MINS);
            mAmPmPaint = createTextPaint(COLOR_TEXT_AM_PM);
            mColonPaint = createTextPaint(COLOR_TEXT_COLON);
            mHighPaint = createTextPaint(Color.WHITE);
            mLowPaint = createTextPaint(Color.WHITE);


            mIconPaint = new Paint();
            mIconPaint.setColor(Color.BLACK);

            mCalendar = Calendar.getInstance();

            IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
            MessageReceiver messageReceiver = new MessageReceiver();
            LocalBroadcastManager.getInstance(MyWatchFace.this)
                    .registerReceiver(messageReceiver, messageFilter);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            googleApiClient.disconnect();
            super.onDestroy();

        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d(LOG_TAG, "onVisibilityChanged: " + visible);

            super.onVisibilityChanged(visible);

            if (visible) {
                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }


        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            Log.d(LOG_TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));

            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            float textSize = resources.getDimension(isRound
                    ? R.dimen.weather_text_size_round : R.dimen.weather_text_size);
            float amPmSize = resources.getDimension(isRound
                    ? R.dimen.weather_am_pm_size_round : R.dimen.weather_am_pm_size);

            mTimeYOffset = resources.getDimension(isRound
                    ? R.dimen.time_y_offset_round : R.dimen.time_y_offset);
            mIconYOffset = resources.getDimension(isRound
                    ? R.dimen.icon_y_offset_round : R.dimen.icon_y_offset);
            mTemperatureYOffset = resources.getDimension(isRound
                    ? R.dimen.temperature_y_offset_round : R.dimen.temperature_y_offset);

            mIconMultiplier = isRound ? 1.5 : 1;


            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mAmPmPaint.setTextSize(amPmSize);
            mColonPaint.setTextSize(textSize);
            mHighPaint.setTextSize(textSize);
            mLowPaint.setTextSize(amPmSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mHourPaint.setTypeface(NORMAL_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            Log.d(LOG_TAG, "onTimeTick: ambient = " + isInAmbientMode());
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(LOG_TAG, "onAmbientModeChanged: " + inAmbientMode);
            mAmbientMode = inAmbientMode;
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                ;
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mAmPmPaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
                mHighPaint.setAntiAlias(antiAlias);
                mLowPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (mHighTemperature == null) {
                new SendData("/update_path").start();
            }
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            boolean is24Hour = DateFormat.is24HourFormat(MyWatchFace.this);

            // Draw the background.
            if (mAmbientMode) {
                canvas.drawColor(Color.BLACK);
            } else canvas.drawColor(getColor(R.color.primary));


            float newX = 0;
            String hourString;

            // Build Strings
            if (is24Hour) {
                hourString = String.format("%02d",mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }

            String minuteString = String.format("%02d",mCalendar.get(Calendar.MINUTE));
            String amPmString = getAmPmString(mCalendar.get(Calendar.AM_PM));
            newX += mHourPaint.measureText(hourString);
            newX += mColonWidth;
            newX += mMinutePaint.measureText(minuteString);
            if (!is24Hour) {
                newX += mHourPaint.measureText(hourString);
            }
            float x = bounds.centerX() - newX / 2;
            float xTemperature = 0;
            float xIcon = 0;
            if (mHighTemperature != null && mLowTemperature != null) {
                xTemperature = bounds.centerX() - (mHighPaint.measureText(
                        mHighTemperature + " " + mLowTemperature) / 2);
            }


            // Draw hour
            canvas.drawText(hourString, x, mTimeYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);

            // Draw first colon (between hour and minute).
            canvas.drawText(COLON_STRING, x, mTimeYOffset, mColonPaint);
            x += mColonWidth;

            // Draw the minutes.
            canvas.drawText(minuteString, x, mTimeYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString);

            // If we're in 12-hour mode, draw AM/PM
            if (!is24Hour) {
                x += mColonWidth;
                canvas.drawText(amPmString, x, mTimeYOffset, mAmPmPaint);
            }

            // Only render info if there is no peek card, so they do not bleed into each other
            // in ambient mode.
            /**
             * Handle drawing additional info here
             **/
            if (getPeekCardPosition().isEmpty() && !mAmbientMode) {
                if (mHighTemperature != null && mLowTemperature != null) {
                    canvas.drawText(
                            mHighTemperature,
                            xTemperature,
                            mTemperatureYOffset,
                            mHighPaint);

                    xTemperature += mHighPaint.measureText(mHighTemperature + " ");

                    canvas.drawText(mLowTemperature,
                            xTemperature,
                            mTemperatureYOffset,
                            mLowPaint);
                }
                if (mWeatherId > 0) {
                    Drawable drawable = getResources().getDrawable(Utility.getWeatherIcon(mWeatherId));
                    Bitmap weatherIcon = ((BitmapDrawable) drawable).getBitmap();

                    Bitmap scaledWeatherIcon =
                            Bitmap.createScaledBitmap(weatherIcon,
                                    (int) (mIconMultiplier * mHighPaint.getTextSize()),
                                    (int) (mIconMultiplier * mHighPaint.getTextSize()), true);

                    canvas.drawBitmap(scaledWeatherIcon,
                            bounds.centerY() - scaledWeatherIcon.getHeight() / 2,
                            mIconYOffset,
                            null);
                }

            }


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            Log.d(LOG_TAG, "updateTimer");

            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldUpdateTimeHandlerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldUpdateTimeHandlerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        class SendData extends Thread {
            String path;
            String message;

            // Constructor to send a message to the data layer
            SendData(String message) {
                path = message;
                message = "updater";
            }

            public void run() {
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), path, message.getBytes()).await();
                    if (result.getStatus().isSuccess()) {
                    } else {
                    }
                }
            }
        }


    }
}
