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
        final int imageW = 613;
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

        // Coordinates are taken directly from the supplied 613x287 reference image.
        final View touchPad = transparentHit(panel, 0, 0, 563, 210, hitViews);
        final View close = transparentHit(panel, 563, 0, 50, 105, hitViews);
        final View drag = transparentHit(panel, 563, 105, 50, 105, hitViews);
        final View left = transparentHit(panel, 0, 210, 169, 77, hitViews);
        final View wheel1 = transparentHit(panel, 169, 210, 74, 77, hitViews);
        final View auto = transparentHit(panel, 243, 210, 80, 77, hitViews);
        final View wheel2 = transparentHit(panel, 323, 210, 75, 77, hitViews);
        final View right = transparentHit(panel, 398, 210, 114, 77, hitViews);
        final View select = transparentHit(panel, 512, 210, 101, 77, hitViews);
        final View resize = transparentHit(panel, 0, 0, 70, 45, hitViews);

        // Reposition hit areas whenever the panel size changes, preserving the exact
        // proportions of the supplied image on different displays.
        panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int w = r-l, h = b-t;
            setHit(panel, touchPad, 0,0,563,210,w,h);
            setHit(panel, close, 563,0,50,105,w,h);
            setHit(panel, drag, 563,105,50,105,w,h);
            setHit(panel, left, 0,210,169,77,w,h);
            setHit(panel, wheel1, 169,210,74,77,w,h);
            setHit(panel, auto, 243,210,80,77,w,h);
            setHit(panel, wheel2, 323,210,75,77,w,h);
            setHit(panel, right, 398,210,114,77,w,h);
            setHit(panel, select, 512,210,101,77,w,h);
            setHit(panel, resize, 0,0,70,45,w,h);
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

        left.setOnClickListener(v -> click(false));
        right.setOnClickListener(v -> click(true));
        wheel1.setOnClickListener(v -> scroll(-1));
        wheel2.setOnClickListener(v -> scroll(1));
        auto.setOnClickListener(v -> toggleAutoTargetMode());
        select.setOnClickListener(v -> selectMode = !selectMode);

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
        lp.leftMargin=Math.round(x*pw/613f);
        lp.topMargin=Math.round(y*ph/287f);
        lp.width=Math.max(1,Math.round(w*pw/613f));
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

    private boolean clickFileButton() {
        if (Build.VERSION.SDK_INT < 21) return false;

        final String ownPackage = getPackageName();
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> candidates =
                new java.util.ArrayList<>();

        android.view.accessibility.AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && !ownPackage.equals(active.getPackageName())) {
            collectFileCandidates(active, candidates);
        }
        if (candidates.isEmpty()) {
            try {
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    if (w == null) continue;
                    android.view.accessibility.AccessibilityNodeInfo root = w.getRoot();
                    if (root == null || ownPackage.equals(root.getPackageName())) continue;
                    collectFileCandidates(root, candidates);
                }
            } catch (Exception ignored) {}
        }

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
            // ChatGPT and similar sites may expose the attachment control as a
            // "Add files and more" (+) menu first. After opening it, click the
            // site's own "Add files" menu item. This never launches a generic
            // Android document picker from the keyboard.
            handler.postDelayed(this::clickFileMenuItem, 280);
        }
        return ok;
    }

    private boolean clickFileMenuItem() {
        if (Build.VERSION.SDK_INT < 21) return false;
        final String ownPackage = getPackageName();
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> candidates = new java.util.ArrayList<>();
        try {
            android.view.accessibility.AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null && !ownPackage.equals(active.getPackageName())) collectFileMenuCandidates(active, candidates);
            if (candidates.isEmpty()) {
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    if (w == null) continue;
                    android.view.accessibility.AccessibilityNodeInfo root = w.getRoot();
                    if (root == null || ownPackage.equals(root.getPackageName())) continue;
                    collectFileMenuCandidates(root, candidates);
                }
            }
        } catch (Exception ignored) {}
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

        final String ownPackage = getPackageName();
        java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> candidates =
                new java.util.ArrayList<>();

        // Prefer the active window, then inspect other interactive windows.
        android.view.accessibility.AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && !ownPackage.equals(active.getPackageName())) {
            collectSendCandidates(active, candidates);
        }
        if (candidates.isEmpty() && Build.VERSION.SDK_INT >= 21) {
            try {
                for (android.view.accessibility.AccessibilityWindowInfo w : getWindows()) {
                    if (w == null) continue;
                    android.view.accessibility.AccessibilityNodeInfo root = w.getRoot();
                    if (root == null || ownPackage.equals(root.getPackageName())) continue;
                    collectSendCandidates(root, candidates);
                }
            } catch (Exception ignored) {}
        }

        android.view.accessibility.AccessibilityNodeInfo best = chooseBestSendCandidate(candidates);
        for (android.view.accessibility.AccessibilityNodeInfo n : candidates) {
            if (n != best) { try { n.recycle(); } catch (Exception ignored) {} }
        }

        // First try the real accessible Send control.
        boolean ok = false;
        if (best != null) {
            try {
                if (best.isEnabled()) {
                    ok = best.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
                    if (!ok && Build.VERSION.SDK_INT >= 21) {
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
            if (ok) return true;
        }

        // Some browser/WebView versions expose ChatGPT's blue Send icon without
        // any text or content-description. In that case locate the message
        // composer and tap its lower-right action area with an accessibility gesture.
        return tapComposerSendFallback(active);
    }

    private boolean tapComposerSendFallback(android.view.accessibility.AccessibilityNodeInfo active) {
        if (Build.VERSION.SDK_INT < 24 || active == null) return false;
        final android.graphics.Rect composer = new android.graphics.Rect();
        if (!findComposerBounds(active, composer)) return false;
        if (composer.width() <= 40 || composer.height() <= 20) return false;

        // Prefer an unlabeled clickable control close to the composer's lower-right corner.
        android.view.accessibility.AccessibilityNodeInfo candidate =
                findRightBottomClickable(active, composer);
        float x;
        float y;
        if (candidate != null) {
            android.graphics.Rect r = new android.graphics.Rect();
            candidate.getBoundsInScreen(r);
            x = r.centerX();
            y = r.centerY();
            try { candidate.recycle(); } catch (Exception ignored) {}
        } else {
            // Last-resort geometry used only when the WebView exposes no button node.
            x = composer.right - Math.max(24, Math.min(42, composer.height() / 2));
            y = composer.centerY();
        }
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
                all.contains("sendbutton") || all.contains("submit") ||
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
        if(s!=null){s.dragMode=false;}
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
