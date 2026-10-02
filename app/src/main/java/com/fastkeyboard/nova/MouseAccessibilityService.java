package com.fastkeyboard.nova;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.util.DisplayMetrics;
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
import android.view.accessibility.AccessibilityNodeInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
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
    private AccessibilityNodeInfo snappedTarget;
    private android.graphics.Rect snappedTargetRect;
    private boolean snapReleased = true;
    private Button autoTargetButton;
    private View magnifierView;
    private WindowManager.LayoutParams magnifierLp;
    private boolean magnifierEnabled=false;
    private boolean selectMode=false;
    private int cursorSizeStep=1;
    private final int[] cursorSizeDp={30,42,58,76};
    private final java.util.ArrayList<Button> mouseButtons=new java.util.ArrayList<>();
    private static final int LIGHT_BLUE=0xFF8FD3FF, LIGHT_CREAM=0xFFFFF0B3, LIGHT_GREEN=0xFFBFE8BF, LIGHT_YELLOW=0xFFFFE48A, LIGHT_ORANGE=0xFFFFB18A;
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
        // Use the real physical display bounds so the cursor can travel through
        // the Android status bar and navigation bar as well as the app area.
        try {
            android.view.Display display = wm.getDefaultDisplay();
            android.util.DisplayMetrics real = new android.util.DisplayMetrics();
            display.getRealMetrics(real);
            screenW = real.widthPixels;
            screenH = real.heightPixels;
        } catch (Throwable ignored) {}
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
        if (mousePanel != null) return;
        showCursor();

        // Quick Settings uses the user's exact supplied mouse-window image as the
        // visual base. Interactive transparent hit areas are placed over the image,
        // so the appearance stays unchanged while every control remains functional.
        final int imageW = 639;
        final int imageH = 287;
        final int panelW = Math.min(dp(imageW), Math.max(dp(200), screenW - dp(8)));
        final int panelH = Math.max(dp(132), Math.round(panelW * imageH / (float) imageW));

        final FrameLayout panel = new FrameLayout(this);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setBackgroundColor(Color.WHITE);

        ImageView reference = new ImageView(this);
        reference.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.mouse_reference));
        reference.setScaleType(ImageView.ScaleType.FIT_XY);
        reference.setClickable(false);
        panel.addView(reference, new FrameLayout.LayoutParams(-1, -1));

        final float sx = 1f / imageW;
        final float sy = 1f / imageH;
        final java.util.ArrayList<View> hitViews = new java.util.ArrayList<>();

        // Coordinates are taken directly from the supplied 639x287 reference image.
        // Exact controls from the new 639x287 reference image.
        // Top-right: Close / Drag. Bottom: Left Click, Left, Right, Up, Down, Point Zoom.
        final View touchPad = transparentHit(panel, 0, 0, 563, 210, hitViews);
        final View close = transparentHit(panel, 563, 0, 76, 105, hitViews);
        final View drag = transparentHit(panel, 563, 105, 76, 105, hitViews);
        final View leftClick = transparentHit(panel, 0, 210, 126, 77, hitViews);
        final View moveLeft = transparentHit(panel, 126, 210, 89, 77, hitViews);
        final View moveRight = transparentHit(panel, 215, 210, 87, 77, hitViews);
        final View moveUp = transparentHit(panel, 302, 210, 92, 77, hitViews);
        final View moveDown = transparentHit(panel, 394, 210, 87, 77, hitViews);
        final View pointZoom = transparentHit(panel, 481, 210, 82, 77, hitViews);
        final View resize = transparentHit(panel, 0, 0, 70, 45, hitViews);
        final View resizeBottomRight = transparentHit(panel, 600, 250, 39, 37, hitViews);

        // Reposition hit areas whenever the panel size changes, preserving the exact
        // proportions of the supplied image on different displays.
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int w = r-l, h = b-t;
            setHit(panel, touchPad, 0,0,563,210,w,h);
            setHit(panel, close, 563,0,76,105,w,h);
            setHit(panel, drag, 563,105,76,105,w,h);
            setHit(panel, leftClick, 0,210,126,77,w,h);
            setHit(panel, moveLeft, 126,210,89,77,w,h);
            setHit(panel, moveRight, 215,210,87,77,w,h);
            setHit(panel, moveUp, 302,210,92,77,w,h);
            setHit(panel, moveDown, 394,210,87,77,w,h);
            setHit(panel, pointZoom, 481,210,82,77,w,h);
            setHit(panel, resize, 0,0,70,45,w,h);
            setHit(panel, resizeBottomRight, 600,250,39,37,w,h);
        });

        close.setOnClickListener(v -> hideMouseOverlay());

        final float[] padLast = {0f,0f};
        final boolean[] padMoving = {false};
        touchPad.setOnTouchListener((v,e)->{
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                padLast[0]=e.getRawX(); padLast[1]=e.getRawY(); padMoving[0]=true; return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE && padMoving[0]) {
                float dx=e.getRawX()-padLast[0], dy=e.getRawY()-padLast[1];
                if (Math.abs(dx)>=0.5f || Math.abs(dy)>=0.5f) {
                    moveRelative(dx*2f,dy*2f);
                    padLast[0]=e.getRawX(); padLast[1]=e.getRawY();
                }
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                padMoving[0]=false; return true;
            }
            return true;
        });

        // Drag moves the Quick Settings mouse window itself.
        final float[] dragLast = {0f,0f};
        drag.setOnTouchListener((v,e)->{
            if (mousePanelLp == null || wm == null) return true;
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                dragLast[0]=e.getRawX(); dragLast[1]=e.getRawY(); return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE) {
                float dx=e.getRawX()-dragLast[0], dy=e.getRawY()-dragLast[1];
                mousePanelLp.x += Math.round(dx);
                mousePanelLp.y += Math.round(dy);
                mousePanelLp.x=Math.max(0,Math.min(screenW-mousePanelLp.width,mousePanelLp.x));
                mousePanelLp.y=Math.max(0,Math.min(screenH-mousePanelLp.height,mousePanelLp.y));
                try { wm.updateViewLayout(mousePanel,mousePanelLp); } catch(Exception ignored) {}
                dragLast[0]=e.getRawX(); dragLast[1]=e.getRawY();
                return true;
            }
            return true;
        });

        leftClick.setOnClickListener(v -> click(false));

        // The four arrow buttons perform direct cursor movement.
        final float arrowStep = Math.max(dp(24), 48f * getResources().getDisplayMetrics().density);
        moveLeft.setOnClickListener(v -> moveRelative(-arrowStep, 0));
        moveRight.setOnClickListener(v -> moveRelative(arrowStep, 0));
        moveUp.setOnClickListener(v -> moveRelative(0, -arrowStep));
        moveDown.setOnClickListener(v -> moveRelative(0, arrowStep));

        // "Point Zoom" magnifies only the screen area immediately under the mouse pointer.
        pointZoom.setOnClickListener(v -> { magnifierEnabled = true; updateMagnifier(); handler.postDelayed(this::updateMagnifier, 180); });

        // One functional resize grip at the same top-left location shown in the image.
        final float[] resizeLast = {0f,0f};
        final int[] resizeBase = {panelW,panelH};
        resize.setOnTouchListener((v,e)->{
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                resizeLast[0]=e.getRawX(); resizeLast[1]=e.getRawY();
                resizeBase[0]=mousePanelLp != null ? mousePanelLp.width : panelW;
                resizeBase[1]=mousePanelLp != null ? mousePanelLp.height : panelH;
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE && mousePanelLp != null && wm != null) {
                int nw=Math.max(dp(200),resizeBase[0]-Math.round(e.getRawX()-resizeLast[0]));
                int nh=Math.max(dp(132),Math.round(nw*imageH/(float)imageW));
                mousePanelLp.width=nw; mousePanelLp.height=nh;
                try { wm.updateViewLayout(mousePanel,mousePanelLp); } catch(Exception ignored) {}
                return true;
            }
            return true;
        });
        // Bottom-right resize grip: drag outward/inward to resize the whole mouse window.
        final float[] brLast = {0f,0f};
        final int[] brBase = {panelW,panelH};
        resizeBottomRight.setOnTouchListener((v,e)->{
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                brLast[0]=e.getRawX(); brLast[1]=e.getRawY();
                brBase[0]=mousePanelLp != null ? mousePanelLp.width : panelW;
                brBase[1]=mousePanelLp != null ? mousePanelLp.height : panelH;
                return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE && mousePanelLp != null && wm != null) {
                int nw=Math.max(dp(200),Math.min(screenW-dp(8),brBase[0]+Math.round(e.getRawX()-brLast[0])));
                int nh=Math.max(dp(132),Math.min(screenH-dp(8),brBase[1]+Math.round(e.getRawY()-brLast[1])));
                mousePanelLp.width=nw; mousePanelLp.height=nh;
                try { wm.updateViewLayout(mousePanel,mousePanelLp); } catch(Exception ignored) {}
                brLast[0]=e.getRawX(); brLast[1]=e.getRawY();
                return true;
            }
            return true;
        });

        mousePanel = panel;
        int xPos = Math.max(0, screenW-panelW-dp(8));
        int yPos = Math.max(0, dp(72));
        mousePanelLp = new WindowManager.LayoutParams(
            panelW, panelH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        mousePanelLp.gravity = Gravity.TOP | Gravity.LEFT;
        mousePanelLp.x=xPos; mousePanelLp.y=yPos;
        panel.setElevation(30f);
        wm.addView(panel,mousePanelLp);
    }

    private View transparentHit(FrameLayout parent,int x,int y,int w,int h,java.util.ArrayList<View> list){
        View v=new View(this);
        v.setBackgroundColor(Color.TRANSPARENT);
        v.setClickable(true);
        parent.addView(v,new FrameLayout.LayoutParams(1,1));
        list.add(v);
        return v;
    }

    private void setHit(FrameLayout parent,View v,int x,int y,int w,int h,int pw,int ph){
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)v.getLayoutParams();
        lp.leftMargin=Math.round(x*pw/639f);
        lp.topMargin=Math.round(y*ph/287f);
        lp.width=Math.max(1,Math.round(w*pw/639f));
        lp.height=Math.max(1,Math.round(h*ph/287f));
        v.setLayoutParams(lp);
    }

    private void installMouseTap(Button b, int color, final Runnable action){
        b.setEnabled(true);
        b.setClickable(true);
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){ flashAllMouseButtons(color); return true; }
            if(e.getAction()==MotionEvent.ACTION_UP){ if(action!=null) action.run(); return true; }
            if(e.getAction()==MotionEvent.ACTION_CANCEL){ return true; }
            return true;
        });
    }

    private void flashAllMouseButtons(int color){
        android.graphics.drawable.ColorDrawable glow=new android.graphics.drawable.ColorDrawable(color);
        glow.setAlpha(95);
        for(Button b:mouseButtons){ if(b!=null) b.setForeground(glow.getConstantState()!=null ? glow.getConstantState().newDrawable() : new android.graphics.drawable.ColorDrawable(color)); }
        handler.removeCallbacks(clearMouseButtonLights);
        handler.postDelayed(clearMouseButtonLights,180);
    }
    private final Runnable clearMouseButtonLights=()->{ for(Button b:mouseButtons){ if(b!=null) b.setForeground(null); } };

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
                    int radius=Math.max(36,Math.round(cursorSize*2.8f));
                    int cx=Math.max(0,Math.min(source.getWidth()-1,Math.round(x+cursorSize/2f)));
                    int cy=Math.max(0,Math.min(source.getHeight()-1,Math.round(y+cursorSize/2f)));
                    int left=Math.max(0,Math.min(source.getWidth()-1,cx-radius));
                    int top=Math.max(0,Math.min(source.getHeight()-1,cy-radius));
                    int right=Math.min(source.getWidth(),left+radius*2);
                    int bottom=Math.min(source.getHeight(),top+radius*2);
                    Bitmap crop=Bitmap.createBitmap(source,left,top,Math.max(1,right-left),Math.max(1,bottom-top));
                    Bitmap scaled=Bitmap.createScaledBitmap(crop,dp(210),dp(210),true);
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
            magnifierLp=new WindowManager.LayoutParams(dp(220),dp(220),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
            magnifierLp.gravity=Gravity.TOP|Gravity.LEFT;
            try{wm.addView(magnifierView,magnifierLp);}catch(Exception ignored){magnifierView=null;return;}
        }
        ((ImageView)magnifierView).setImageBitmap(bitmap);
        // Keep the zoom lens centered on the pointer position. The source crop is
        // centered on the pointer, so this is a true point-zoom rather than a
        // general page/screen zoom.
        float cx = x + cursorSize / 2f;
        float cy = y + cursorSize / 2f;
        magnifierLp.x=Math.max(0,Math.min(screenW-magnifierLp.width,Math.round(cx-magnifierLp.width/2f)));
        magnifierLp.y=Math.max(0,Math.min(screenH-magnifierLp.height,Math.round(cy-magnifierLp.height/2f)));
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
        releaseSnap();
        if (autoTargetMode) {
            // Small, precise snap zone; the cursor is never pulled across the screen.
            snapToNearbyClickable();
        }
    }

    private void stopAutoTargetMode() {
        autoTargetMode = false;
        handler.removeCallbacks(autoTargetRunnable);
        releaseSnap();
        if (autoTargetButton != null) autoTargetButton.setText("حرکت خودکار: خاموش");
    }

    private void snapToNearbyClickable() {
        if (!autoTargetMode || cursor == null || dragMode) return;
        if (!snapReleased && snappedTargetRect != null) {
            float cx = x + cursorSize / 2f;
            float cy = y + cursorSize / 2f;
            float dx = Math.max(snappedTargetRect.left - cx, Math.max(0f, cx - snappedTargetRect.right));
            float dy = Math.max(snappedTargetRect.top - cy, Math.max(0f, cy - snappedTargetRect.bottom));
            float dist = (float)Math.hypot(dx, dy);
            if (dist < 24f) return;
            releaseSnap();
        }
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> targets = new java.util.ArrayList<>();
        try {
            collectClickableTargets(root, targets);
        } finally {
            root.recycle();
        }
        if (targets.isEmpty()) return;

        // Precise snap radius: about 3 mm, not 30 mm. The old 30 mm zone was
        // far too strong on phones and made nearby controls capture the cursor.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float pxPerMmX = Math.max(1f, dm.xdpi / 25.4f);
        float pxPerMmY = Math.max(1f, dm.ydpi / 25.4f);
        float thresholdX = pxPerMmX * 3f;
        float thresholdY = pxPerMmY * 3f;
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
                // Lock the first snapped target until the cursor is deliberately
                // dragged away. This prevents immediate re-capture of the same control.
                releaseSnap();
                snappedTarget = AccessibilityNodeInfo.obtain(best);
                snappedTargetRect = new android.graphics.Rect(bestRect);
                snapReleased = false;
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
        // x/y are the cursor hotspot, so do not subtract cursorSize here.
        // This permits the hotspot itself to reach the last pixel of the
        // navigation bar instead of stopping one cursor-width above it.
        float maxX = Math.max(0, screenW - 1f);
        float maxY = Math.max(0, screenH - 1f);
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
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

    /**
     * Click the active application's accessible Send button. This is deliberately
     * separate from the keyboard Enter key: the goal is to perform the same UI
     * action as the blue Send button shown by sites such as ChatGPT.
     */
    public static boolean clickSendButtonFromKeyboard() {
        MouseAccessibilityService s = instance;
        return s != null && s.clickSendButton();
    }

    /**
     * Click the active application's accessible file/attachment button.
     * The site's own handler then opens its normal Android file chooser/storage explorer.
     */
    public static boolean clickFileButtonFromKeyboard() {
        MouseAccessibilityService s = instance;
        return s != null && s.clickFileButton();
    }

    private String getForegroundPackageFromUsage() {
        try {
            if (Build.VERSION.SDK_INT < 21) return "";
            UsageStatsManager usm = (UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
            if (usm == null) return "";
            long end = System.currentTimeMillis();
            java.util.List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 30000L, end);
            if (stats == null || stats.isEmpty()) return "";
            String own = getPackageName(); UsageStats best = null;
            for (UsageStats u : stats) {
                if (u == null || u.getPackageName() == null || own.equals(u.getPackageName())) continue;
                if (best == null || u.getLastTimeUsed() > best.getLastTimeUsed()) best = u;
            }
            return best == null ? "" : best.getPackageName();
        } catch (Exception ignored) { return ""; }
    }

    private java.util.ArrayList<AccessibilityNodeInfo> targetRoots() {
        java.util.ArrayList<AccessibilityNodeInfo> all = new java.util.ArrayList<>();
        String own = getPackageName(); String fg = getForegroundPackageFromUsage();
        try {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null && !own.equals(active.getPackageName())) all.add(active);
        } catch (Exception ignored) {}
        try {
            for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                if (w == null) continue; AccessibilityNodeInfo root = w.getRoot();
                if (root == null || own.equals(root.getPackageName())) continue; all.add(root);
            }
        } catch (Exception ignored) {}
        if (!fg.isEmpty()) {
            java.util.ArrayList<AccessibilityNodeInfo> filtered = new java.util.ArrayList<>();
            for (AccessibilityNodeInfo r : all) {
                if (r != null && fg.equals(r.getPackageName())) filtered.add(r);
                else try { if (r != null) r.recycle(); } catch (Exception ignored) {}
            }
            if (!filtered.isEmpty()) return filtered;
        }
        return all;
    }

    private boolean performNodeClick(AccessibilityNodeInfo n) {
        if (n == null) return false;
        try {
            if (!n.isEnabled()) return false;
            if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            for (AccessibilityNodeInfo.AccessibilityAction a : n.getActionList())
                if (a.getId() == AccessibilityNodeInfo.ACTION_CLICK && n.performAction(a.getId())) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private void scheduleFileMenuClicks() {
        handler.postDelayed(this::clickFileMenuItem, 250);
        handler.postDelayed(this::clickFileMenuItem, 600);
        handler.postDelayed(this::clickFileMenuItem, 1000);
        handler.postDelayed(this::clickFileMenuItem, 1500);
    }

    private boolean clickFileButton() {
        if (Build.VERSION.SDK_INT < 21) return false;

        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> candidates =
                new java.util.ArrayList<>();
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> roots = targetRoots();
        for (android.view.accessibility.AccessibilityNodeInfo root : roots) collectFileCandidates(root, candidates);
        for (android.view.accessibility.AccessibilityNodeInfo root : roots) try { root.recycle(); } catch (Exception ignored) {}

        android.view.accessibility.AccessibilityNodeInfo best = chooseBestFileCandidate(candidates);
        for (android.view.accessibility.AccessibilityNodeInfo n : candidates) {
            if (n != best) { try { n.recycle(); } catch (Exception ignored) {} }
        }
        if (best == null) return false;

        boolean ok = false;
        try {
            if (best.isEnabled()) {
                ok = best.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                if (!ok) {
                    for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction a : best.getActionList()) {
                        if (a.getId() == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                            ok = best.performAction(a.getId());
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try { best.recycle(); } catch (Exception ignored) {}
        if (ok) {
            // The site's own attachment control may open a second menu.
            scheduleFileMenuClicks();
            return true;
        }

        // WebViews frequently expose only the composer/editor, not the attachment
        // icon. Tap the site's own lower-left composer area as a final fallback.
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> roots2 = targetRoots();
        android.graphics.Rect composer = new android.graphics.Rect();
        for (android.view.accessibility.AccessibilityNodeInfo root : roots2) findComposerBounds(root, composer);
        for (android.view.accessibility.AccessibilityNodeInfo root : roots2) try { root.recycle(); } catch (Exception ignored) {}
        if (composer.width() > 40 && composer.height() > 20) {
            // The site's attachment control is normally inside the lower-left
            // edge of the composer. The previous version tapped outside the
            // composer, so the site's button was missed.
            float x = composer.left + Math.max(20f, Math.min(46f, composer.height() * 0.55f));
            float y = composer.bottom - Math.max(18f, Math.min(40f, composer.height() * 0.32f));
            boolean tapped = tapScreenPoint(x, y);
            if (tapped) {
                handler.postDelayed(() -> tapScreenPoint(x + 18f, y), 180);
                scheduleFileMenuClicks();
                handler.postDelayed(this::clickFileMenuItem, 1900);
            }
            return tapped;
        }
        return false;
    }

    private boolean clickFileMenuItem() {
        if (Build.VERSION.SDK_INT < 21) return false;
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> candidates = new java.util.ArrayList<>();
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> roots = targetRoots();
        for (android.view.accessibility.AccessibilityNodeInfo root : roots) collectFileMenuCandidates(root, candidates);
        for (android.view.accessibility.AccessibilityNodeInfo root : roots) try { root.recycle(); } catch (Exception ignored) {}
        android.view.accessibility.AccessibilityNodeInfo best = chooseBestFileMenuCandidate(candidates);
        for (android.view.accessibility.AccessibilityNodeInfo n : candidates) if (n != best) try { n.recycle(); } catch (Exception ignored) {}
        if (best == null) return false;
        boolean ok = false;
        try { if (best.isEnabled()) ok = best.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); } catch (Exception ignored) {}
        try { best.recycle(); } catch (Exception ignored) {}
        return ok;
    }

    private void collectFileMenuCandidates(android.view.accessibility.AccessibilityNodeInfo node, java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> out) {
        if (node == null) return;
        try {
            String text = node.getText() == null ? "" : node.getText().toString().toLowerCase(java.util.Locale.ROOT);
            String desc = node.getContentDescription() == null ? "" : node.getContentDescription().toString().toLowerCase(java.util.Locale.ROOT);
            String all = text + " " + desc;
            boolean hit = all.contains("add files") || all.contains("add file") || all.contains("attach files") || all.contains("choose file") || all.contains("select file") || all.contains("انتخاب فایل") || all.contains("افزودن فایل") || all.contains("پیوست") || all.contains("ضمیمه");
            if (hit && node.isVisibleToUser() && node.isEnabled() && node.isClickable()) out.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node));
            for (int i=0;i<node.getChildCount();i++) { android.view.accessibility.AccessibilityNodeInfo c=node.getChild(i); if(c!=null){ collectFileMenuCandidates(c,out); try{c.recycle();}catch(Exception ignored){} } }
        } catch (Exception ignored) {}
    }

    private android.view.accessibility.AccessibilityNodeInfo chooseBestFileMenuCandidate(java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> list) {
        android.view.accessibility.AccessibilityNodeInfo best=null; int score=Integer.MIN_VALUE;
        for (android.view.accessibility.AccessibilityNodeInfo n:list) {
            String t=n.getText()==null?"":n.getText().toString().toLowerCase(java.util.Locale.ROOT);
            String d=n.getContentDescription()==null?"":n.getContentDescription().toString().toLowerCase(java.util.Locale.ROOT);
            int s=0; if(t.contains("add files")||d.contains("add files"))s+=150; if(t.contains("add file")||d.contains("add file"))s+=130; if(d.contains("attach files"))s+=120; if(t.contains("انتخاب فایل")||d.contains("انتخاب فایل"))s+=120; if(n.isClickable())s+=20; if(s>score){score=s;best=n;}
        }
        return best;
    }

    private void collectFileCandidates(android.view.accessibility.AccessibilityNodeInfo node,
                                       java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> out) {
        if (node == null) return;
        try {
            if (isFileNode(node)) out.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node));
            for (int i = 0; i < node.getChildCount(); i++) {
                android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    collectFileCandidates(child, out);
                    try { child.recycle(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean isFileNode(android.view.accessibility.AccessibilityNodeInfo n) {
        if (!n.isVisibleToUser() || !n.isEnabled()) return false;
        String text = n.getText() == null ? "" : n.getText().toString().trim();
        String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim();
        String viewId = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName();
        String all = (text + " " + desc + " " + viewId).toLowerCase(java.util.Locale.ROOT);
        if (all.isEmpty()) return false;

        boolean label =
                all.contains("add files and more") || all.contains("add photos and files") ||
                all.contains("attach files") || all.contains("attach file") ||
                all.contains("add files") || all.contains("add file") ||
                all.contains("upload files") || all.contains("upload file") ||
                all.contains("choose file") || all.contains("select file") ||
                all.contains("انتخاب فایل") || all.contains("افزودن فایل") ||
                all.contains("ضمیمه") || all.contains("پیوست") || all.contains("بارگذاری فایل");
        boolean idHint =
                all.contains("attach") || all.contains("attachment") ||
                all.contains("file-upload") || all.contains("file_upload") ||
                all.contains("upload-file") || all.contains("upload_file");
        if (!label && !idHint) return false;

        boolean actionClick = n.isClickable();
        if (!actionClick) {
            for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction a : n.getActionList()) {
                if (a.getId() == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                    actionClick = true; break;
                }
            }
        }
        return actionClick;
    }

    private android.view.accessibility.AccessibilityNodeInfo chooseBestFileCandidate(
            java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> list) {
        if (list.isEmpty()) return null;
        android.view.accessibility.AccessibilityNodeInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        int h = screenH > 0 ? screenH : getResources().getDisplayMetrics().heightPixels;
        for (android.view.accessibility.AccessibilityNodeInfo n : list) {
            int score = 0;
            String text = n.getText() == null ? "" : n.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim().toLowerCase(java.util.Locale.ROOT);
            String id = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName().toLowerCase(java.util.Locale.ROOT);
            if (desc.contains("add files and more") || desc.contains("add photos and files")) score += 150;
            if (desc.contains("attach files") || desc.contains("attach file")) score += 125;
            if (text.contains("انتخاب فایل") || desc.contains("انتخاب فایل")) score += 115;
            if (desc.contains("add files") || desc.contains("add file")) score += 110;
            if (desc.contains("upload files") || desc.contains("upload file")) score += 100;
            if (id.contains("attach") || id.contains("attachment") || id.contains("file-upload") || id.contains("file_upload")) score += 80;
            if (n.isClickable()) score += 20;
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            // Attachment controls are normally near the lower message composer.
            if (r.centerY() > h * 0.55f) score += 15;
            if (r.width() > 0 && r.height() > 0) score += 5;
            if (score > bestScore) { bestScore = score; best = n; }
        }
        return best;
    }

    private boolean clickSendButton() {
        if (Build.VERSION.SDK_INT < 21) return false;
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> candidates = new java.util.ArrayList<>();
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> roots = targetRoots();
        for (android.view.accessibility.AccessibilityNodeInfo root : roots) collectSendCandidates(root, candidates);
        for (android.view.accessibility.AccessibilityNodeInfo root : roots) try { root.recycle(); } catch (Exception ignored) {}
        android.view.accessibility.AccessibilityNodeInfo best = chooseBestSendCandidate(candidates);
        for (android.view.accessibility.AccessibilityNodeInfo n : candidates) {
            if (n != best) { try { n.recycle(); } catch (Exception ignored) {} }
        }
        if (best != null) {
            try {
                if (best.isEnabled() && best.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                    best.recycle(); return true;
                }
            } catch (Exception ignored) {}
            try { best.recycle(); } catch (Exception ignored) {}
        }
        // WebView often exposes no accessible Send node. Search every interactive
        // window for the largest lower editable composer, then tap its lower-right
        // action area. This is still a site UI gesture, never an Enter key.
        android.graphics.Rect composer = new android.graphics.Rect();
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> roots2 = targetRoots();
        for (android.view.accessibility.AccessibilityNodeInfo root : roots2) findComposerBounds(root, composer);
        for (android.view.accessibility.AccessibilityNodeInfo root : roots2) try { root.recycle(); } catch (Exception ignored) {}
        if (composer.width() <= 40 || composer.height() <= 20) return false;
        boolean tapped = tapComposerSendFallback(null, composer);
        if (tapped) {
            android.graphics.Rect c = new android.graphics.Rect(composer);
            handler.postDelayed(() -> tapComposerSendFallback(null, c), 180);
            handler.postDelayed(() -> tapComposerSendFallback(null, c), 360);
        }
        return tapped;
    }

    private boolean tapComposerSendFallback(android.view.accessibility.AccessibilityNodeInfo ignored, android.graphics.Rect composer) {
        if (Build.VERSION.SDK_INT < 24) return false;
        if (composer == null || composer.width() <= 40 || composer.height() <= 20) return false;
        float x = composer.right - Math.max(24, Math.min(72, composer.height() * 0.70f));
        float y = composer.bottom - Math.max(16, Math.min(48, composer.height() * 0.38f));
        return tapScreenPoint(x, y);
    }

    private boolean findComposerBounds(android.view.accessibility.AccessibilityNodeInfo node,
                                       android.graphics.Rect out) {
        if (node == null) return false;
        try {
            CharSequence cls = node.getClassName();
            String c = cls == null ? "" : cls.toString().toLowerCase(java.util.Locale.ROOT);
            boolean editable = node.isEditable() || c.contains("edittext") ||
                    c.contains("editable") || c.contains("textinput");
            if (editable && node.isVisibleToUser()) {
                android.graphics.Rect r = new android.graphics.Rect();
                node.getBoundsInScreen(r);
                if (r.width() > out.width() && r.height() > out.height()) out.set(r);
            }
            for (int i=0;i<node.getChildCount();i++) {
                android.view.accessibility.AccessibilityNodeInfo ch=node.getChild(i);
                if(ch!=null){ findComposerBounds(ch,out); try{ch.recycle();}catch(Exception ignored){} }
            }
        } catch(Exception ignored){}
        return out.width()>40 && out.height()>20;
    }

    private android.view.accessibility.AccessibilityNodeInfo findRightBottomClickable(
            android.view.accessibility.AccessibilityNodeInfo node, android.graphics.Rect composer) {
        if (node == null) return null;
        android.view.accessibility.AccessibilityNodeInfo best=null;
        int bestScore=Integer.MIN_VALUE;
        try {
            if (node.isVisibleToUser() && node.isEnabled() && node.isClickable()) {
                android.graphics.Rect r=new android.graphics.Rect(); node.getBoundsInScreen(r);
                int score=-10000;
                if(r.width()>0 && r.height()>0 && r.centerX()>=composer.left && r.centerX()<=composer.right+30 &&
                        r.centerY()>=composer.top && r.centerY()<=composer.bottom+30){
                    score=100;
                    int dx=Math.abs(composer.right-r.right);
                    int dy=Math.abs(composer.bottom-r.bottom);
                    score-=Math.min(80,dx*2+dy*2);
                    String d=(String.valueOf(node.getContentDescription())+" "+String.valueOf(node.getViewIdResourceName())).toLowerCase(java.util.Locale.ROOT);
                    if(d.contains("send")||d.contains("submit")||d.contains("ارسال")) score+=200;
                }
                if(score>bestScore){bestScore=score;best=android.view.accessibility.AccessibilityNodeInfo.obtain(node);}
            }
            for(int i=0;i<node.getChildCount();i++){
                android.view.accessibility.AccessibilityNodeInfo ch=node.getChild(i);
                if(ch!=null){
                    android.view.accessibility.AccessibilityNodeInfo got=findRightBottomClickable(ch,composer);
                    if(got!=null){
                        android.graphics.Rect rr=new android.graphics.Rect();got.getBoundsInScreen(rr);
                        int dx=Math.abs(composer.right-rr.right), dy=Math.abs(composer.bottom-rr.bottom);
                        int sc=100-Math.min(80,dx*2+dy*2);
                        String d=(String.valueOf(got.getContentDescription())+" "+String.valueOf(got.getViewIdResourceName())).toLowerCase(java.util.Locale.ROOT);
                        if(d.contains("send")||d.contains("submit")||d.contains("ارسال")) sc+=200;
                        if(sc>bestScore){if(best!=null)try{best.recycle();}catch(Exception ignored){} best=got;bestScore=sc;}else try{got.recycle();}catch(Exception ignored){}
                    }
                    try{ch.recycle();}catch(Exception ignored){}
                }
            }
        } catch(Exception ignored){}
        return bestScore>-9000?best:null;
    }

    private boolean tapScreenPoint(float x,float y){
        if(Build.VERSION.SDK_INT<24)return false;
        try{
            android.graphics.Path p=new android.graphics.Path();p.moveTo(x,y);
            GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(p,0,80);
            return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),null,null);
        }catch(Exception e){return false;}
    }

    private void collectSendCandidates(android.view.accessibility.AccessibilityNodeInfo node,
                                       java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> out) {
        if (node == null) return;
        try {
            if (isSendNode(node)) out.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node));
            for (int i = 0; i < node.getChildCount(); i++) {
                android.view.accessibility.AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    collectSendCandidates(child, out);
                    try { child.recycle(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean isSendNode(android.view.accessibility.AccessibilityNodeInfo n) {
        if (!n.isVisibleToUser() || !n.isEnabled()) return false;
        String text = n.getText() == null ? "" : n.getText().toString().trim();
        String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim();
        String viewId = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName();
        String all = (text + " " + desc + " " + viewId).toLowerCase(java.util.Locale.ROOT);
        if (all.isEmpty()) return false;

        // Exact/near-exact labels used by ChatGPT and common web/app UIs.
        boolean label =
                all.equals("send") || all.equals("ارسال") ||
                all.contains("send message") || all.contains("send prompt") ||
                all.contains("send_button") || all.contains("send-button") ||
                all.contains("sendbutton") || all.contains("send prompt") || all.contains("send message") ||
                all.contains("submit") ||
                all.contains("ارسال پیام") || all.contains("ارسال") ||
                all.contains("ارسال پیام") || all.contains("submit message");
        if (!label) return false;

        boolean actionClick = n.isClickable();
        if (!actionClick && Build.VERSION.SDK_INT >= 21) {
            for (android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction a : n.getActionList()) {
                if (a.getId() == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) {
                    actionClick = true; break;
                }
            }
        }
        return actionClick;
    }

    private android.view.accessibility.AccessibilityNodeInfo chooseBestSendCandidate(
            java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> list) {
        if (list.isEmpty()) return null;
        android.view.accessibility.AccessibilityNodeInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        int h = screenH > 0 ? screenH : getResources().getDisplayMetrics().heightPixels;
        for (android.view.accessibility.AccessibilityNodeInfo n : list) {
            int score = 0;
            String text = n.getText() == null ? "" : n.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim().toLowerCase(java.util.Locale.ROOT);
            if (text.equals("send") || text.equals("ارسال")) score += 100;
            if (desc.contains("send prompt") || desc.contains("send message") || desc.contains("ارسال پیام")) score += 90;
            if (n.isClickable()) score += 20;
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            // Send controls are normally in the lower input area. This is only a
            // tie-breaker and never replaces label matching.
            if (r.centerY() > h * 0.55f) score += 15;
            if (r.width() > 0 && r.height() > 0) score += 5;
            if (score > bestScore) { bestScore = score; best = n; }
        }
        return best;
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
        if(s!=null){s.dragMode=false; s.releaseSnap();}
    }

    public static boolean toggleSelectFromKeyboard() {
        MouseAccessibilityService s=instance;
        if(s==null) return false;
        s.selectMode = !s.selectMode;
        return s.selectMode;
    }

    public static boolean isSelectModeFromKeyboard() {
        MouseAccessibilityService s=instance;
        return s != null && s.selectMode;
    }

    public static void scrollFromKeyboard(int direction) {
        MouseAccessibilityService s=instance;
        if(s!=null) s.scroll(direction);
    }

    public static void toggleAutoTargetFromKeyboard() {
        MouseAccessibilityService s=instance;
        if(s!=null) s.toggleAutoTargetMode();
    }

    private void scroll(int direction) {
        if (Build.VERSION.SDK_INT < 24) return;
        if (cursor == null) showCursor();
        float cx=x+cursorSize/2f;
        float cy=y+cursorSize/2f;
        float amount=direction<0 ? -90f : 90f;
        Path path=new Path();
        path.moveTo(cx,cy);
        path.lineTo(cx,cy);
        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path,0,60);
        // Accessibility has no universal synthetic mouse-wheel event. A short
        // swipe at the cursor is used as the compatibility fallback for scrollable views.
        Path scrollPath=new Path();
        scrollPath.moveTo(cx,cy);
        scrollPath.lineTo(cx,cy-amount);
        GestureDescription.StrokeDescription scrollStroke =
            new GestureDescription.StrokeDescription(scrollPath,0,180);
        dispatchGesture(new GestureDescription.Builder().addStroke(scrollStroke).build(),null,null);
    }

    private void moveRelative(float dx, float dy) {
        if (cursor == null) showCursor();
        float oldX=x, oldY=y;
        // Keep the pointer hotspot inside the complete physical display.
        // This explicitly includes the status and navigation bars.
        float maxX = Math.max(0, screenW - 1f);
        float maxY = Math.max(0, screenH - 1f);
        x = Math.max(0, Math.min(maxX, x + dx));
        y = Math.max(0, Math.min(maxY, y + dy));
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams)cursor.getTag();
        lp.x = Math.round(x);
        lp.y = Math.round(y);
        wm.updateViewLayout(cursor, lp);
        if (autoTargetMode && !dragMode) snapToNearbyClickable();
        if (magnifierEnabled) updateMagnifier();
        if(dragMode && Build.VERSION.SDK_INT>=24){
            dispatchSwipe(oldX+3f,oldY+3f,x+3f,y+3f,12);
        }
    }

    private void releaseSnap() {
        if (snappedTarget != null) { try { snappedTarget.recycle(); } catch (Exception ignored) {} }
        snappedTarget = null;
        snappedTargetRect = null;
        snapReleased = true;
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
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,Math.max(12,duration));
        dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),null,null);
    }

    private void click(boolean right) {
        if (cursor == null) showCursor();
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
            if (right) {
                // Compatibility fallback: a long press behaves like a context/right click
                // for many Android and WebView controls.
                GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(p, 0, 650);
                dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
            } else {
                p.lineTo(cx + 1f, cy + 1f);
                GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(p, 0, 70);
                dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(), null, null);
            }
        }
    }

    private boolean performNodeClickAt(int px, int py, boolean right) {
        if (Build.VERSION.SDK_INT < 21) return false;
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        android.view.accessibility.AccessibilityNodeInfo node = findNodeAt(root, px, py);
        if (node == null) return false;
        try {
            if (right && Build.VERSION.SDK_INT >= 23) {
                // Prefer the real Android context-click action for a right click.
                try {
                    // Some SDK/API combinations do not expose ACTION_CONTEXT_CLICK; use long-click fallback.
                    if (node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true;
                } catch (Throwable ignored) {}
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
