package com.fastkeyboard.nova;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.widget.ImageView;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.view.MotionEvent;
import android.graphics.Typeface;
import java.util.concurrent.Executor;

public class MouseAccessibilityService extends AccessibilityService {
    private static MouseAccessibilityService instance;
    private WindowManager wm;
    private ComputerCursorView cursor;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float x = -1, y = -1;
    private int screenW, screenH;
    private int cursorSize;
    private boolean dragMode=false;
    private View mousePanel;
    private WindowManager.LayoutParams mousePanelLp;
    private boolean autoTargetMode = false;
    private Button autoTargetButton;
    private View magnifierView;
    private WindowManager.LayoutParams magnifierLp;
    private boolean magnifierEnabled=false;
    private boolean selectMode=false;
    private int cursorSizeStep=1;
    private final int[] cursorSizeDp={30,42,58,76};
    private final Runnable autoTargetRunnable = new Runnable() {
        @Override public void run() {
            // Snap is checked directly after pointer movement; no periodic tree scan.
            if (autoTargetMode) handler.postDelayed(this, 250);
        }
    };

    public static MouseAccessibilityService getInstance() { return instance; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        cursorSize = Math.max(42, Math.round(42 * dm.density));
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            if (Build.VERSION.SDK_INT >= 21) {
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            }
            setServiceInfo(info);
        }
    }

    public static void showCursorFromKeyboard() {
        MouseAccessibilityService s=instance;
        if(s!=null) s.showCursor();
    }

    public static void hideCursorFromKeyboard() {
        MouseAccessibilityService s=instance;
        if(s!=null) s.hideCursor();
    }

    public boolean isMouseOverlayShown() { return mousePanel != null; }

    public void showMouseOverlay() {
        if (wm == null) return;
        showCursor();
        if (mousePanel != null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(4),dp(4),dp(4),dp(4));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.argb(245, 250, 250, 250));
        panelBg.setStroke(dp(2), Color.rgb(45,45,55));
        panelBg.setCornerRadius(dp(6));
        root.setBackground(panelBg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.rgb(238,242,248));
        headerBg.setStroke(dp(1), Color.rgb(70,70,80));
        header.setBackground(headerBg);

        TextView title = new TextView(this);
        title.setText("موس Fast Keyboard Nova");
        title.setTextSize(16);
        title.setTextColor(Color.rgb(10,38,92));
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1f));

        Button dragHandle = new Button(this);
        dragHandle.setText("Drag");
        dragHandle.setTextSize(16);
        dragHandle.setAllCaps(false);
        dragHandle.setGravity(Gravity.CENTER);
        header.addView(dragHandle, new LinearLayout.LayoutParams(dp(72), dp(58)));

        Button closeButton = new Button(this);
        closeButton.setText("Close");
        closeButton.setTextSize(15);
        closeButton.setAllCaps(false);
        closeButton.setGravity(Gravity.CENTER);
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(72), dp(58)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(62)));
        closeButton.setOnClickListener(v -> hideMouseOverlay());

        final float[] panelLast = {0f,0f};
        final boolean[] panelMoving = {false};
        final boolean[] panelDragged = {false};
        dragHandle.setOnTouchListener((v,e)->{
            if (mousePanelLp == null || wm == null) return true;
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                panelLast[0]=e.getRawX();
                panelLast[1]=e.getRawY();
                panelMoving[0]=true;
                panelDragged[0]=false;
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE && panelMoving[0]) {
                float dx=e.getRawX()-panelLast[0];
                float dy=e.getRawY()-panelLast[1];
                if (Math.abs(dx)>=2f || Math.abs(dy)>=2f) {
                    panelDragged[0]=true;
                    mousePanelLp.x -= Math.round(dx);
                    mousePanelLp.y -= Math.round(dy);
                    int maxX=Math.max(0, screenW-mousePanel.getWidth()-dp(4));
                    int maxY=Math.max(0, screenH-mousePanel.getHeight()-dp(4));
                    mousePanelLp.x=Math.max(0,Math.min(maxX,mousePanelLp.x));
                    mousePanelLp.y=Math.max(0,Math.min(maxY,mousePanelLp.y));
                    try { wm.updateViewLayout(mousePanel,mousePanelLp); } catch(Exception ignored) {}
                    panelLast[0]=e.getRawX();
                    panelLast[1]=e.getRawY();
                }
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                panelMoving[0]=false;
                // Consume the entire gesture. Never forward a drag gesture to the
                // keyboard beneath the accessibility window.
                return true;
            }
            return true;
        });

        final TextView pad = new TextView(this);
        pad.setText("میدان لمسی\nحرکت نشانگر");
        pad.setTextSize(16);
        pad.setTextColor(Color.rgb(190,194,202));
        pad.setGravity(Gravity.CENTER);
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(Color.rgb(242,244,248));
        pg.setStroke(dp(1), Color.rgb(190,194,202));
        pad.setBackground(pg);
        root.addView(pad, new LinearLayout.LayoutParams(-1, 125));

        final float[] last = {0,0};
        final boolean[] moving = {false};
        pad.setOnTouchListener((v,e)->{
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                last[0]=e.getRawX(); last[1]=e.getRawY(); moving[0]=true; return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE && moving[0]) {
                float dx=e.getRawX()-last[0], dy=e.getRawY()-last[1];
                if (Math.abs(dx)>=0.5f || Math.abs(dy)>=0.5f) {
                    moveRelative(dx*2.0f,dy*2.0f);
                    last[0]=e.getRawX(); last[1]=e.getRawY();
                }
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) { moving[0]=false; return true; }
            return true;
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button left = new Button(this); left.setText("کلیک چپ"); left.setTextSize(15);
        Button pointerSize = new Button(this); pointerSize.setText("↕"); pointerSize.setTextSize(25);
        Button auto = new Button(this); auto.setText("حرکت\nخودکار"); auto.setTextSize(11);
        Button magnify = new Button(this); magnify.setText("↕"); magnify.setTextSize(25);
        Button right = new Button(this); right.setText("کلیک راست"); right.setTextSize(15);
        Button select = new Button(this); select.setText("Select"); select.setTextSize(14); select.setAllCaps(false);
        row.addView(left,new LinearLayout.LayoutParams(0,58,2.1f));
        row.addView(pointerSize,new LinearLayout.LayoutParams(0,58,.58f));
        row.addView(auto,new LinearLayout.LayoutParams(0,58,1.0f));
        row.addView(magnify,new LinearLayout.LayoutParams(0,58,.58f));
        row.addView(right,new LinearLayout.LayoutParams(0,58,2.1f));
        row.addView(select,new LinearLayout.LayoutParams(0,58,1.25f));
        root.addView(row);

        pointerSize.setOnClickListener(v -> cycleCursorSize());
        magnify.setOnClickListener(v -> toggleMagnifier());
        auto.setOnClickListener(v -> toggleAutoTargetMode());
        select.setOnClickListener(v -> {
            selectMode=!selectMode;
            select.setText(selectMode ? "Select ✓" : "Select");
            if (!selectMode) dragMode=false;
        });

        // Hold left while moving the touch field to perform drag/select.
        left.setOnTouchListener((v,e)->{
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                beginDragFromKeyboard();
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                endDragFromKeyboard();
                if (e.getAction()==MotionEvent.ACTION_UP && !selectMode) click(false);
                return true;
            }
            return true;
        });
        right.setOnClickListener(v->click(true));

        LinearLayout transparencyRow = new LinearLayout(this);
        transparencyRow.setOrientation(LinearLayout.HORIZONTAL);
        transparencyRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView transparencyLabel = new TextView(this);
        transparencyLabel.setText("شفافیت");
        transparencyLabel.setTextSize(13);
        transparencyLabel.setTextColor(Color.rgb(10,38,92));
        transparencyRow.addView(transparencyLabel, new LinearLayout.LayoutParams(dp(58), dp(42)));
        SeekBar transparencyBar = new SeekBar(this);
        transparencyBar.setMax(80);
        int savedTransparency = getSharedPreferences("mouse_settings", MODE_PRIVATE).getInt("panel_transparency", 100);
        savedTransparency = Math.max(20, Math.min(100, savedTransparency));
        transparencyBar.setProgress(savedTransparency - 20);
        transparencyRow.addView(transparencyBar, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView transparencyValue = new TextView(this);
        transparencyValue.setText(savedTransparency + "%");
        transparencyValue.setTextSize(12);
        transparencyValue.setGravity(Gravity.CENTER);
        transparencyValue.setTextColor(Color.rgb(10,38,92));
        transparencyRow.addView(transparencyValue, new LinearLayout.LayoutParams(dp(48), dp(42)));
        root.addView(transparencyRow, new LinearLayout.LayoutParams(-1, dp(46)));
        root.setAlpha(savedTransparency / 100f);
        transparencyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int value = Math.max(20, Math.min(100, progress + 20));
                root.setAlpha(value / 100f);
                transparencyValue.setText(value + "%");
                if (fromUser) getSharedPreferences("mouse_settings", MODE_PRIVATE).edit().putInt("panel_transparency", value).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        autoTargetButton = auto;

        FrameLayout panel = new FrameLayout(this);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.addView(root, new FrameLayout.LayoutParams(-1, -1));
        addResizeHandle(panel, Gravity.LEFT | Gravity.TOP, -1, -1);
        addResizeHandle(panel, Gravity.RIGHT | Gravity.TOP, 1, -1);
        addResizeHandle(panel, Gravity.LEFT | Gravity.BOTTOM, -1, 1);
        addResizeHandle(panel, Gravity.RIGHT | Gravity.BOTTOM, 1, 1);

        mousePanel = panel;
        int initialW = Math.min(dp(430), screenW - dp(16));
        int initialH = dp(390);
        mousePanelLp = new WindowManager.LayoutParams(
            initialW, initialH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        mousePanelLp.gravity = Gravity.TOP | Gravity.LEFT;
        mousePanelLp.x = Math.max(0, screenW - initialW - dp(8));
        mousePanelLp.y = dp(72);
        panel.setElevation(30f);
        wm.addView(panel, mousePanelLp);
    }

    private void cycleCursorSize() {
        cursorSizeStep=(cursorSizeStep+1)%cursorSizeDp.length;
        int newSize=dp(cursorSizeDp[cursorSizeStep]);
        cursorSize=newSize;
        if(cursor==null || wm==null) return;
        WindowManager.LayoutParams lp=(WindowManager.LayoutParams)cursor.getTag();
        float centerX=x+((int)lp.width)/2f, centerY=y+((int)lp.height)/2f;
        lp.width=newSize; lp.height=newSize;
        x=Math.max(0,Math.min(screenW-newSize,centerX-newSize/2f));
        y=Math.max(0,Math.min(screenH-newSize,centerY-newSize/2f));
        lp.x=Math.round(x); lp.y=Math.round(y);
        try{wm.updateViewLayout(cursor,lp);}catch(Exception ignored){}
    }

    private void toggleMagnifier() {
        magnifierEnabled=!magnifierEnabled;
        if(magnifierEnabled) updateMagnifier(); else hideMagnifier();
    }

    private void updateMagnifier() {
        if(!magnifierEnabled || Build.VERSION.SDK_INT<30) return;
        try {
            Executor ex = command -> handler.post(command);
            takeScreenshot(Display.DEFAULT_DISPLAY, ex, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                HardwareBuffer hb=null;
                Bitmap source=null;
                try {
                    hb=result.getHardwareBuffer();
                    ColorSpace cs=result.getColorSpace();
                    if(hb!=null){
                        Bitmap hw=Bitmap.wrapHardwareBuffer(hb,cs);
                        if(hw!=null){source=hw.copy(Bitmap.Config.ARGB_8888,false);hw.recycle();}
                    }
                    if(source==null)return;
                    int radius=Math.max(30,Math.round(cursorSize*1.2f));
                    int cx=Math.max(0,Math.min(source.getWidth()-1,Math.round(x+cursorSize/2f)));
                    int cy=Math.max(0,Math.min(source.getHeight()-1,Math.round(y+cursorSize/2f)));
                    int left=Math.max(0,Math.min(source.getWidth()-1,cx-radius));
                    int top=Math.max(0,Math.min(source.getHeight()-1,cy-radius));
                    int right=Math.min(source.getWidth(),left+radius*2);
                    int bottom=Math.min(source.getHeight(),top+radius*2);
                    Bitmap crop=Bitmap.createBitmap(source,left,top,Math.max(1,right-left),Math.max(1,bottom-top));
                    Bitmap scaled=Bitmap.createScaledBitmap(crop,dp(180),dp(180),true);
                    crop.recycle();
                    Bitmap finalBitmap=scaled;
                    handler.post(()->showMagnifierBitmap(finalBitmap));
                } catch(Exception ignored) {} finally {
                    if(source!=null)source.recycle();
                    if(hb!=null)hb.close();
                }
                }
                @Override public void onFailure(int errorCode) { }
            });
        } catch(Exception ignored) {}
    }

    private void showMagnifierBitmap(Bitmap bitmap) {
        if(!magnifierEnabled || wm==null || bitmap==null)return;
        if(magnifierView==null){
            ImageView iv=new ImageView(this);
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(Color.WHITE); bg.setStroke(dp(2),Color.rgb(45,45,55)); bg.setCornerRadius(dp(8));
            iv.setBackground(bg); iv.setPadding(dp(2),dp(2),dp(2),dp(2));
            magnifierView=iv;
            magnifierLp=new WindowManager.LayoutParams(dp(190),dp(190),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            magnifierLp.gravity=Gravity.TOP|Gravity.LEFT;
            try{wm.addView(magnifierView,magnifierLp);}catch(Exception ignored){magnifierView=null;return;}
        }
        ((ImageView)magnifierView).setImageBitmap(bitmap);
        magnifierLp.x=Math.max(0,Math.min(screenW-magnifierLp.width,Math.round(x+cursorSize+dp(8))));
        magnifierLp.y=Math.max(0,Math.min(screenH-magnifierLp.height,Math.round(y-dp(8)-magnifierLp.height)));
        try{wm.updateViewLayout(magnifierView,magnifierLp);}catch(Exception ignored){}
    }

    private void hideMagnifier(){
        if(magnifierView!=null&&wm!=null){try{wm.removeView(magnifierView);}catch(Exception ignored){}}
        magnifierView=null;magnifierLp=null;
    }

    private void addResizeHandle(FrameLayout panel, int gravity, int horizontalDir, int verticalDir) {
        TextView handle = new TextView(this);
        String glyph;
        if (horizontalDir < 0 && verticalDir < 0) glyph = "↖";
        else if (horizontalDir > 0 && verticalDir < 0) glyph = "↗";
        else if (horizontalDir < 0 && verticalDir > 0) glyph = "↙";
        else glyph = "↘";
        handle.setText(glyph);
        handle.setTextSize(18);
        handle.setTextColor(Color.rgb(10,38,92));
        handle.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235,225,232,242));
        bg.setStroke(dp(1), Color.rgb(45,45,55));
        bg.setCornerRadius(dp(6));
        handle.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(38), dp(38));
        lp.gravity = gravity;
        panel.addView(handle, lp);

        final float[] last = {0f,0f};
        handle.setOnTouchListener((v,e)->{
            if (mousePanelLp == null || wm == null) return false;
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                last[0]=e.getRawX(); last[1]=e.getRawY();
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE) {
                float dx=e.getRawX()-last[0], dy=e.getRawY()-last[1];
                int minW=dp(260), minH=dp(230);
                int maxW=Math.max(minW,screenW-dp(12));
                int maxH=Math.max(minH,screenH-dp(12));
                int oldW=mousePanelLp.width, oldH=mousePanelLp.height;
                int newW=oldW, newH=oldH;
                if (horizontalDir > 0) newW += Math.round(dx);
                else newW -= Math.round(dx);
                if (verticalDir > 0) newH += Math.round(dy);
                else newH -= Math.round(dy);
                newW=Math.max(minW,Math.min(maxW,newW));
                newH=Math.max(minH,Math.min(maxH,newH));
                if (horizontalDir < 0) mousePanelLp.x += oldW-newW;
                if (verticalDir < 0) mousePanelLp.y += oldH-newH;
                mousePanelLp.width=newW;
                mousePanelLp.height=newH;
                mousePanelLp.x=Math.max(0,Math.min(screenW-newW,mousePanelLp.x));
                mousePanelLp.y=Math.max(0,Math.min(screenH-newH,mousePanelLp.y));
                try { wm.updateViewLayout(mousePanel,mousePanelLp); } catch(Exception ignored) {}
                last[0]=e.getRawX(); last[1]=e.getRawY();
                return true;
            }
            return true;
        });
    }

    private void toggleAutoTargetMode() {
        autoTargetMode = !autoTargetMode;
        if (autoTargetButton != null) {
            autoTargetButton.setText(autoTargetMode ? "حرکت خودکار: روشن" : "حرکت خودکار: خاموش");
        }
        handler.removeCallbacks(autoTargetRunnable);
        if (autoTargetMode) {
            // Do not jump to distant controls. Snapping happens only when the
            // cursor is within about 1 mm of a clickable item's bounds.
            snapToNearbyClickable();
        }
    }

    private void stopAutoTargetMode() {
        autoTargetMode = false;
        handler.removeCallbacks(autoTargetRunnable);
        if (autoTargetButton != null) autoTargetButton.setText("حرکت خودکار: خاموش");
    }

    private void snapToNearbyClickable() {
        if (!autoTargetMode || cursor == null) return;
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> targets = new java.util.ArrayList<>();
        try {
            collectClickableTargets(root, targets);
        } finally {
            root.recycle();
        }
        if (targets.isEmpty()) return;

        // Requested snap radius: 30 mm from the clickable bounds.
        // Use the physical display density so the distance is approximately
        // the same physical size across different screens.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float pxPerMmX = Math.max(1f, dm.xdpi / 25.4f);
        float pxPerMmY = Math.max(1f, dm.ydpi / 25.4f);
        float thresholdX = pxPerMmX * 30f;
        float thresholdY = pxPerMmY * 30f;
        float hx = x + 2f;
        float hy = y + 2f;
        android.view.accessibility.AccessibilityNodeInfo best = null;
        android.graphics.Rect bestRect = null;
        float bestDist = Float.MAX_VALUE;
        try {
            for (android.view.accessibility.AccessibilityNodeInfo n : targets) {
                android.graphics.Rect r = new android.graphics.Rect();
                n.getBoundsInScreen(r);
                if (r.width() <= 0 || r.height() <= 0) continue;
                float nx = Math.max(r.left, Math.min(hx, r.right));
                float ny = Math.max(r.top, Math.min(hy, r.bottom));
                float dx = hx - nx;
                float dy = hy - ny;
                float normalizedX = dx / Math.max(1f, thresholdX);
                float normalizedY = dy / Math.max(1f, thresholdY);
                float dist = (float)Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
                if (dist <= 1f && dist < bestDist) {
                    bestDist = dist;
                    best = n;
                    bestRect = r;
                }
            }
            if (best != null && bestRect != null) {
                // Put the hotspot on the nearest point inside the clickable bounds.
                float nx = Math.max(bestRect.left, Math.min(hx, bestRect.right));
                float ny = Math.max(bestRect.top, Math.min(hy, bestRect.bottom));
                moveCursorToScreenPoint(nx - 2f, ny - 2f);
            }
        } finally {
            for (android.view.accessibility.AccessibilityNodeInfo n : targets) {
                try { n.recycle(); } catch (Exception ignored) {}
            }
        }
    }

    private void moveToNextClickableTarget() {
        if (cursor == null || wm == null) return;
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> targets = new java.util.ArrayList<>();
        try {
            collectClickableTargets(root, targets);
        } finally {
            root.recycle();
        }
        if (targets.isEmpty()) return;

        // Choose the next target in screen order. Keep an index in a field so
        // repeated timer ticks walk through links/buttons instead of staying put.
        int start = autoTargetIndex % targets.size();
        android.view.accessibility.AccessibilityNodeInfo target = targets.get(start);
        autoTargetIndex = (start + 1) % targets.size();

        android.graphics.Rect r = new android.graphics.Rect();
        target.getBoundsInScreen(r);
        target.recycle();

        if (r.width() <= 0 || r.height() <= 0) return;
        float tx = r.left + Math.min(r.width() / 2f, 20f);
        float ty = r.top + Math.min(r.height() / 2f, 20f);
        moveCursorToScreenPoint(tx, ty);
    }

    private int autoTargetIndex = 0;

    private void collectClickableTargets(android.view.accessibility.AccessibilityNodeInfo node,
                                         java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> out) {
        if (node == null) return;
        android.graphics.Rect r = new android.graphics.Rect();
        node.getBoundsInScreen(r);
        boolean usable = node.isVisibleToUser() && r.width() > 4 && r.height() > 4;
        boolean clickable = node.isClickable();
        if (!clickable && Build.VERSION.SDK_INT >= 21) {
            java.util.List<android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
            for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction a : actions) {
                if (a.getId() == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                    clickable = true;
                    break;
                }
            }
        }
        if (usable && clickable) out.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node));

        for (int i = 0; i < node.getChildCount(); i++) {
            android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectClickableTargets(child, out);
                child.recycle();
            }
        }
    }

    private void moveCursorToScreenPoint(float tx, float ty) {
        if (cursor == null || wm == null) return;
        float maxX = Math.max(0, screenW - cursorSize);
        float maxY = Math.max(0, screenH - cursorSize);
        x = Math.max(0, Math.min(maxX, tx));
        y = Math.max(0, Math.min(maxY, ty));
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) cursor.getTag();
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        try { wm.updateViewLayout(cursor, lp); } catch (Exception ignored) {}
    }

    public void hideMouseOverlay() {
        stopAutoTargetMode();
        magnifierEnabled=false;
        hideMagnifier();
        if (mousePanel != null && wm != null) { try { wm.removeView(mousePanel); } catch(Exception ignored) {} }
        mousePanel = null; mousePanelLp = null;
        hideCursor();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void hideCursor() {
        if(cursor!=null && wm!=null){
            try{wm.removeView(cursor);}catch(Exception ignored){}
        }
        cursor=null;
    }

    private void showCursor() {
        if (cursor != null || wm == null) return;
        cursor = new ComputerCursorView(this);
        cursor.setElevation(20f);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            cursorSize, cursorSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        // x/y are the cursor hotspot (the sharp arrow tip), not its center.
        x = Math.max(0, screenW / 2f - cursorSize / 2f);
        y = Math.max(0, screenH / 2f - cursorSize / 2f);
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        cursor.setTag(lp);
        wm.addView(cursor, lp);
        updateCursorAppearance(Color.BLACK);
    }

    private void updateCursorAppearance(int bg) {
        if (cursor == null) return;
        // Fixed desktop-style appearance requested for Fast Keyboard Nova:
        // deep navy arrow with a darker navy outline.
        int c = Color.rgb(10, 38, 92);
        int outline = Color.rgb(2, 12, 34);
        cursor.setCursorColors(c, outline);
    }

    public static void moveRelativeFromKeyboard(float dx, float dy) {
        MouseAccessibilityService s = instance;
        if (s != null) s.moveRelative(dx, dy);
    }

    public static void clickFromKeyboard(boolean right) {
        MouseAccessibilityService s = instance;
        if (s != null) s.click(right);
    }

    public static void resetFromKeyboard() {
        MouseAccessibilityService s = instance;
        if (s != null) s.resetCursor();
    }

    public static void beginDragFromKeyboard() {
        MouseAccessibilityService s=instance;
        if(s!=null){s.dragMode=true;}
    }

    public static void endDragFromKeyboard() {
        MouseAccessibilityService s=instance;
        if(s!=null){s.dragMode=false;}
    }

    private void moveRelative(float dx, float dy) {
        if (cursor == null) showCursor();
        float oldX=x, oldY=y;
        float maxX = Math.max(0, screenW - cursorSize);
        float maxY = Math.max(0, screenH - cursorSize);
        x = Math.max(0, Math.min(maxX, x + dx));
        y = Math.max(0, Math.min(maxY, y + dy));
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams)cursor.getTag();
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        wm.updateViewLayout(cursor, lp);
        if (autoTargetMode) snapToNearbyClickable();
        if (magnifierEnabled) updateMagnifier();
        if(dragMode && Build.VERSION.SDK_INT>=24){
            dispatchSwipe(oldX+3f,oldY+3f,x+3f,y+3f,35);
        }
    }

    private void resetCursor() {
        x = Math.max(0, screenW / 2f - cursorSize / 2f);
        y = Math.max(0, screenH / 2f - cursorSize / 2f);
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams)cursor.getTag();
        lp.x = Math.round(x); lp.y = Math.round(y);
        wm.updateViewLayout(cursor, lp);
    }

    private void dispatchSwipe(float sx,float sy,float ex,float ey,long duration){
        Path path=new Path();
        path.moveTo(sx,sy);
        path.lineTo(ex,ey);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,Math.max(40,duration));
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),null,null);
    }

    private void click(boolean right) {
        if (cursor == null) return;
        float cx = x + 2f;
        float cy = y + 2f;

        // First try the accessibility node actually under the cursor. This is more
        // reliable for browser links, buttons and other accessible controls than
        // relying only on a synthetic screen gesture.
        boolean nodeHandled = performNodeClickAt(Math.round(cx), Math.round(cy), right);
        if (nodeHandled) return;

        // Fallback for arbitrary screen content: inject a real short touch gesture.
        if (Build.VERSION.SDK_INT >= 24) {
            Path p = new Path();
            p.moveTo(cx, cy);
            p.lineTo(cx + 1f, cy + 1f);
            GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(p, 0, 70);
            dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
        }
    }

    private boolean performNodeClickAt(int px, int py, boolean right) {
        if (Build.VERSION.SDK_INT < 21) return false;
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        android.view.accessibility.AccessibilityNodeInfo node = findNodeAt(root, px, py);
        if (node == null) return false;
        try {
            if (right && Build.VERSION.SDK_INT >= 24 &&
                node.getActionList().toString().contains("ACTION_CONTEXT_CLICK")) {
                /* ACTION_CONTEXT_CLICK is not available in this compile SDK; use gesture fallback for right-click. */
            }
            if (node.isClickable() && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true;
            // Some browser controls expose ACTION_CLICK without reporting clickable.
            if (node.getActionList().toString().contains("ACTION_CLICK")) {
                return node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            }
        } finally {
            node.recycle();
        }
        return false;
    }

    private android.view.accessibility.AccessibilityNodeInfo findNodeAt(android.view.accessibility.AccessibilityNodeInfo node, int px, int py) {
        android.graphics.Rect r = new android.graphics.Rect();
        node.getBoundsInScreen(r);
        if (!r.contains(px, py)) return null;
        // Search children first so the most specific control at the cursor wins.
        for (int i = node.getChildCount() - 1; i >= 0; i--) {
            android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            android.view.accessibility.AccessibilityNodeInfo hit = findNodeAt(child, px, py);
            if (hit != null) return hit;
            child.recycle();
        }
        return android.view.accessibility.AccessibilityNodeInfo.obtain(node);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        stopAutoTargetMode();
        hideMagnifier();
        instance = null;
        if (cursor != null && wm != null) {
            try { wm.removeView(cursor); } catch (Exception ignored) {}
        }
        cursor = null;
        if (mousePanel != null && wm != null) { try { wm.removeView(mousePanel); } catch(Exception ignored) {} }
        mousePanel = null;
        super.onDestroy();
    }
}
