/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.watchface.simpleelegance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {
  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private class Engine extends CanvasWatchFaceService.Engine {
    private static final long INTERACTIVE_UPDATE_RATE_MS = 1000 / 30;
    private static final long AMBIENT_UPDATE_RATE_MS = 20 * 1000;

    /* Handler to update the time once a second in interactive mode. */
    private final Handler updateTimeHandler = new Handler() {
      @Override
      public void handleMessage(Message message) {
        if (R.id.message_update == message.what) {
          invalidate();
          if (shouldTimerBeRunning()) {
            long timeMs = System.currentTimeMillis();
            if (isInAmbientMode()) {
              long delayMs = AMBIENT_UPDATE_RATE_MS
                      - (timeMs % AMBIENT_UPDATE_RATE_MS);
              updateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
            } else {
              long delayMs = INTERACTIVE_UPDATE_RATE_MS
                      - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
              updateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
            }
          }
        }
      }
    };

    private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        calendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      }
    };

    private boolean registeredTimeZoneReceiver = false;
    private float handleWidth;
    private float timeUnitWidth;
    private Calendar calendar;
    private Intent batteryStatus;
    private Paint backgroundPaint;
    private Paint hoursHandPaint;
    private Paint minutesHandPaint;
    private Paint secondsHandPaint;
    private Paint timeUnitPaint;
    private Paint batteryPaint;
    private Paint dayNamePaint;
    private Paint datePaint;
    private RectF batteryRect;
    private boolean ambient;
    private float hourHandLength;
    private float minuteHandLength;
    private float secondHandLength;
    private int totalWidth;
    private int totalHeight;
    private float centerX;
    private float centerY;

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this).build());

      TypedValue typedValue = new TypedValue();

      getResources().getValue(R.dimen.handle_width, typedValue, true);
      handleWidth = typedValue.getFloat();

      getResources().getValue(R.dimen.time_unit_handle_width, typedValue, true);
      timeUnitWidth = typedValue.getFloat();

      backgroundPaint = new Paint();
      backgroundPaint.setColor(getResources().getColor(R.color.background));

      hoursHandPaint = new Paint();
      hoursHandPaint.setColor(getResources().getColor(R.color.hours_handle));
      hoursHandPaint.setStrokeWidth(handleWidth);
      hoursHandPaint.setAntiAlias(true);
      hoursHandPaint.setStrokeCap(Paint.Cap.ROUND);

      minutesHandPaint = new Paint();
      minutesHandPaint.setColor(getResources().getColor(R.color.minutes_handle));
      minutesHandPaint.setStrokeWidth(handleWidth);
      minutesHandPaint.setAntiAlias(true);
      minutesHandPaint.setStrokeCap(Paint.Cap.ROUND);

      secondsHandPaint = new Paint();
      secondsHandPaint.setColor(getResources().getColor(R.color.seconds_handle));
      secondsHandPaint.setStrokeWidth(handleWidth);
      secondsHandPaint.setAntiAlias(true);
      secondsHandPaint.setStrokeCap(Paint.Cap.ROUND);

      timeUnitPaint = new Paint();
      timeUnitPaint.setColor(getResources().getColor(R.color.time_unit_handle));
      timeUnitPaint.setStrokeWidth(timeUnitWidth);
      timeUnitPaint.setAntiAlias(true);
      timeUnitPaint.setStrokeCap(Paint.Cap.ROUND);

      batteryPaint = new Paint();
      batteryPaint.setStrokeWidth(10f);
      batteryPaint.setAntiAlias(true);
      batteryPaint.setStrokeCap(Paint.Cap.ROUND);
      batteryPaint.setStyle(Paint.Style.STROKE);

      batteryRect = new RectF();

      calendar = Calendar.getInstance();

      IntentFilter batteryStatusFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
      batteryStatus = getApplicationContext().registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          batteryStatus = intent;
        }
      }, batteryStatusFilter);

      dayNamePaint = new Paint();
      dayNamePaint.setColor(Color.WHITE);
      dayNamePaint.setTextSize((float) 20);
      dayNamePaint.setTextAlign(Paint.Align.RIGHT);
      dayNamePaint.setAntiAlias(true);
      dayNamePaint.setSubpixelText(true);

      datePaint = new Paint();
      datePaint.setColor(Color.WHITE);
      datePaint.setTextSize((float) 20);
      datePaint.setTextAlign(Paint.Align.CENTER);
      datePaint.setAntiAlias(true);
      datePaint.setSubpixelText(true);
    }

    @Override
    public void onDestroy() {
      updateTimeHandler.removeMessages(R.id.message_update);
      super.onDestroy();
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();
      invalidate();
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
      if (ambient != inAmbientMode) {
        ambient = inAmbientMode;
        invalidate();
      }

      /*
       * Whether the timer should be running depends on whether we're visible (as well as
       * whether we're in ambient mode), so we may need to start or stop the timer.
       */
      updateTimer();
    }

    @Override
    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      super.onSurfaceChanged(holder, format, width, height);
      totalWidth = width;
      totalHeight = height;
      /*
       * Find the coordinates of the center point on the screen.
       * Ignore the window insets so that, on round watches
       * with a "chin", the watch face is centered on the entire screen,
       * not just the usable portion.
       */
      centerX = totalWidth / 2f;
      centerY = totalHeight / 2f;
      /*
       * Calculate the lengths of the watch hands and store them in member variables.
       */
      hourHandLength = centerX - 80;
      minuteHandLength = centerX - 60;
      secondHandLength = centerX - 40;

      batteryRect.set(0, 0, width, height);
    }

    private void drawTimeUnits(Canvas canvas) {
      canvas.save();

      if (isInAmbientMode()) {
        timeUnitPaint.setAntiAlias(false);
      } else {
        timeUnitPaint.setAntiAlias(true);
      }

      //draw time units...
      for (int timeUnit = 0; timeUnit < 12; timeUnit++) {
        canvas.drawLine(centerX, 0, centerX, getResources().getInteger(R.integer.time_unit_handle_height), timeUnitPaint);
        canvas.rotate(30, centerX, centerY);
      }

      canvas.restore();
    }

    private void drawBattery(Canvas canvas) {
      int batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
      boolean isCharging = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING) == BatteryManager.BATTERY_STATUS_CHARGING;

      if (isInAmbientMode()) {
        batteryPaint.setAntiAlias(false);
      } else {
        batteryPaint.setAntiAlias(true);
      }

      if (isCharging) {
        batteryPaint.setColor(getResources().getColor(R.color.battery_charging));
      } else if (batteryLevel > 75) {
        batteryPaint.setColor(getResources().getColor(R.color.battery_charged));
      } else if (batteryLevel > 40) {
        batteryPaint.setColor(getResources().getColor(R.color.battery_halfcharged));
      } else {
        batteryPaint.setColor(getResources().getColor(R.color.battery_discharged));
      }
      float currentArcDegree = (float) (3.6 * batteryLevel);
      canvas.drawArc(batteryRect, 270, currentArcDegree, false, batteryPaint);
    }

    private void drawDayName(Canvas canvas) {
      if (isInAmbientMode()) {
        dayNamePaint.setAntiAlias(false);
      } else {
        dayNamePaint.setAntiAlias(true);
      }
      canvas.drawText(
              calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH),
              totalWidth - 40,
              centerY,
              dayNamePaint
      );
    }

    private void drawDate(Canvas canvas) {
      if (isInAmbientMode()) {
        datePaint.setAntiAlias(false);
      } else {
        datePaint.setAntiAlias(true);
      }
      canvas.drawText(
              SimpleDateFormat.getDateInstance().format(calendar.getTime()),
//              calendar.getDisplayName(Calendar.DATE, Calendar.SHORT, Locale.ENGLISH),
              centerX,
              totalHeight - 40,
              datePaint
      );
    }

    private void drawHandles(Canvas canvas) {
      final float minutesRotation = calendar.get(Calendar.MINUTE) * 6f;
      final float hourHandOffset = calendar.get(Calendar.MINUTE) / 2f;
      final float hoursRotation = (calendar.get(Calendar.HOUR) * 30) + hourHandOffset;

      if (isInAmbientMode()) {
        hoursHandPaint.setAntiAlias(false);
        minutesHandPaint.setAntiAlias(false);
      } else {
        hoursHandPaint.setAntiAlias(true);
        minutesHandPaint.setAntiAlias(true);
      }


      // save the canvas state before we begin to rotate it
      canvas.save();

      canvas.rotate(hoursRotation, centerX, centerY);
      canvas.drawLine(centerX, centerY, centerX, centerY - hourHandLength, hoursHandPaint);

      canvas.rotate(minutesRotation - hoursRotation, centerX, centerY);
      canvas.drawLine(centerX, centerY, centerX, centerY - minuteHandLength, minutesHandPaint);

      if (!ambient) {
        /*
         * These calculations reflect the rotation in degrees per unit of time, e.g.,
         * 360 / 60 = 6 and 360 / 12 = 30.
         * TODO: wobbling animation
         */
        float diff = calendar.get(Calendar.MILLISECOND);
        float add = 0;
        if (diff <= 400) {
          add = (float) (Math.sin(Math.toRadians(diff)) / 3);
        }
        float seconds = (calendar.get(Calendar.SECOND) + add);
        // continuous handle animation
        //final float seconds = (calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f);
        final float secondsRotation = seconds * 6f;

        canvas.rotate(secondsRotation - minutesRotation, centerX, centerY);
        canvas.drawLine(centerX, centerY, centerX, centerY - secondHandLength, secondsHandPaint);
      }

      canvas.restore();
    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      long now = System.currentTimeMillis();
      calendar.setTimeInMillis(now);

      // Draw the background.
      canvas.drawRect(0, 0, totalWidth, totalHeight, backgroundPaint);

      drawBattery(canvas);
      drawTimeUnits(canvas);
      drawDayName(canvas);
      drawDate(canvas);
      drawHandles(canvas);
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      if (visible) {
        registerReceiver();
        // Update time zone in case it changed while we weren't visible.
        calendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      } else {
        unregisterReceiver();
      }

      /*
       * Whether the timer should be running depends on whether we're visible
       * (as well as whether we're in ambient mode),
       * so we may need to start or stop the timer.
       */
      updateTimer();
    }

    private void registerReceiver() {
      if (registeredTimeZoneReceiver) {
        return;
      }
      registeredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private void unregisterReceiver() {
      if (!registeredTimeZoneReceiver) {
        return;
      }
      registeredTimeZoneReceiver = false;
      MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
    }

    private void updateTimer() {
      updateTimeHandler.removeMessages(R.id.message_update);
      if (shouldTimerBeRunning()) {
        updateTimeHandler.sendEmptyMessage(R.id.message_update);
      }
    }

    /**
     * Returns whether the {@link #updateTimeHandler} timer should be running. The timer
     * should only run when we're visible and in interactive mode.
     */
    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }
  }
}
