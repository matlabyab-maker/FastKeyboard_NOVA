package com.fastkeyboard.nova;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;

/**
 * Android 10/11 compatibility capture source for Point Zoom.
 * It is only started after the user explicitly grants screen-capture permission.
 */
public class ScreenCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final int NOTIFICATION_ID = 4317;
    private static final String CHANNEL_ID = "point_zoom_capture";

    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int width, height;
    private int densityDpi;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent == null ? 0 : intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        android.os.Parcelable raw = intent == null ? null : intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode == 0 || raw == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                startForeground(NOTIFICATION_ID, notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } catch (Exception e) {
                try { startForeground(NOTIFICATION_ID, notification()); } catch (Exception ignored) {}
            }
        } else {
            startForeground(NOTIFICATION_ID, notification());
        }

        if (projection != null) return START_STICKY;

        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(resultCode, (Intent)raw);
            if (projection == null) { stopSelf(); return START_NOT_STICKY; }

            DisplayMetrics dm = getResources().getDisplayMetrics();
            width = dm.widthPixels;
            height = dm.heightPixels;
            densityDpi = dm.densityDpi;
            if (width <= 0 || height <= 0) { stopSelf(); return START_NOT_STICKY; }

            reader = ImageReader.newInstance(width, height,
                    android.graphics.PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(this::onImageAvailable, handler);
            display = projection.createVirtualDisplay(
                    "FastKeyboard Point Zoom",
                    width, height, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, handler);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, handler);
        } catch (Exception e) {
            stopSelf();
        }
        return START_STICKY;
    }

    private void onImageAvailable(ImageReader source) {
        Image image = null;
        Bitmap full = null;
        try {
            image = source.acquireLatestImage();
            if (image == null) return;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            int bitmapWidth = width + Math.max(0, rowPadding / Math.max(1, pixelStride));
            full = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888);
            buffer.rewind();
            full.copyPixelsFromBuffer(buffer);

            float cx = MouseAccessibilityService.getPointZoomCenterX();
            float cy = MouseAccessibilityService.getPointZoomCenterY();
            if (cx < 0 || cy < 0) return;

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int outW = physicalPx(dm, 30f, true);
            int outH = physicalPx(dm, 20f, false);
            int sourceW = Math.max(2, physicalPx(dm, 12f, true));
            int sourceH = Math.max(2, physicalPx(dm, 8f, false));
            int centerX = Math.max(0, Math.min(full.getWidth()-1, Math.round(cx)));
            int centerY = Math.max(0, Math.min(full.getHeight()-1, Math.round(cy)));
            int left = Math.max(0, Math.min(full.getWidth()-sourceW, centerX-sourceW/2));
            int top = Math.max(0, Math.min(full.getHeight()-sourceH, centerY-sourceH/2));
            Bitmap crop = Bitmap.createBitmap(full, left, top,
                    Math.min(sourceW, full.getWidth()-left), Math.min(sourceH, full.getHeight()-top));
            Bitmap scaled = Bitmap.createScaledBitmap(crop, Math.max(1,outW-2), Math.max(1,outH-2), true);
            crop.recycle();
            MouseAccessibilityService.pushPointZoomBitmap(scaled);
        } catch (Exception ignored) {
        } finally {
            if (image != null) image.close();
            if (full != null) full.recycle();
        }
    }

    private int physicalPx(DisplayMetrics dm, float mm, boolean horizontal) {
        float dpi = horizontal ? dm.xdpi : dm.ydpi;
        if (dpi <= 0 || Float.isNaN(dpi) || Float.isInfinite(dpi)) dpi = dm.density * 160f;
        return Math.max(1, Math.round(mm * dpi / 25.4f));
    }

    private Notification notification() {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Fast Keyboard Nova")
                    .setContentText("Point Zoom فعال است")
                    .setSmallIcon(android.R.drawable.ic_menu_search)
                    .setOngoing(true)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("Fast Keyboard Nova")
                .setContentText("Point Zoom فعال است")
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID,
                    "Fast Keyboard Point Zoom", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    @Override public void onDestroy() {
        try { if (display != null) display.release(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        display = null; reader = null; projection = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
