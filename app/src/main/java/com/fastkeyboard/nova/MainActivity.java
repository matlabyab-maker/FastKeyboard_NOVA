package com.fastkeyboard.nova;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.ViewGroup;
import android.view.Gravity;

public class MainActivity extends Activity {
    public static final String EXTRA_REQUEST_POINT_ZOOM_CAPTURE = "request_point_zoom_capture";
    private static final int REQ_POINT_ZOOM_CAPTURE = 4318;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        handlePointZoomRequest(getIntent());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 48, 32, 32);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Fast Keyboard Nova — Fast Keyboard");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(23,61,112));
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView info = new TextView(this);
        info.setText("\nبرای استفاده از کیبورد، ابتدا آن را در تنظیمات Android فعال کنید، سپس Fast Keyboard Nova را به عنوان کیبورد انتخاب کنید.");
        info.setTextSize(18);
        info.setGravity(Gravity.CENTER);
        box.addView(info, new LinearLayout.LayoutParams(-1, -2));

        Button settings = new Button(this);
        settings.setText("تنظیمات کیبورد");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        box.addView(settings, new LinearLayout.LayoutParams(-1, 64));

        Button usage = new Button(this);
        usage.setText("دسترسی Usage access");
        usage.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        box.addView(usage, new LinearLayout.LayoutParams(-1, 64));

        Button modify = new Button(this);
        modify.setText("دسترسی Modify system settings");
        modify.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        });
        box.addView(modify, new LinearLayout.LayoutParams(-1, 64));

        Button accessibility = new Button(this);
        accessibility.setText("فعال‌سازی موس سیستمی");
        accessibility.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        box.addView(accessibility, new LinearLayout.LayoutParams(-1, 64));

        Button picker = new Button(this);
        picker.setText("انتخاب کیبورد");
        picker.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        box.addView(picker, new LinearLayout.LayoutParams(-1, 64));

        setContentView(box);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePointZoomRequest(intent);
    }

    private void handlePointZoomRequest(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_REQUEST_POINT_ZOOM_CAPTURE, false)) return;
        try {
            android.media.projection.MediaProjectionManager mpm =
                    (android.media.projection.MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_POINT_ZOOM_CAPTURE);
        } catch (Exception ignored) {}
        intent.removeExtra(EXTRA_REQUEST_POINT_ZOOM_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_POINT_ZOOM_CAPTURE) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent s = new Intent(this, ScreenCaptureService.class);
            s.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
            s.putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(s);
                else startService(s);
            } catch (Exception ignored) {}
        } else {
            try { MouseAccessibilityService.cancelPointZoom(); } catch (Exception ignored) {}
        }
        finish();
    }
}
