package com.fastkeyboard.nova;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Dedicated accessibility service for Fast Keyboard's site actions.
 * This is intentionally separate from the mouse service so Android Accessibility
 * settings show a clearly named keyboard entry that can be enabled independently.
 */
public class KeyboardAccessibilityService extends AccessibilityService {
    private static KeyboardAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int screenH;

    public static KeyboardAccessibilityService getInstance() { return instance; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        screenH = getResources().getDisplayMetrics().heightPixels;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static boolean clickSendButtonFromKeyboard() {
        KeyboardAccessibilityService s = instance;
        return s != null && s.clickSendButton();
    }

    public static boolean clickFileButtonFromKeyboard() {
        KeyboardAccessibilityService s = instance;
        return s != null && s.clickFileButton();
    }

    private ArrayList<AccessibilityNodeInfo> roots() {
        ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
        String own = getPackageName();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null && !own.equals(active.getPackageName())) roots.add(active);
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                if (w == null) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root != null && !own.equals(root.getPackageName())) roots.add(root);
            }
        } catch (Exception ignored) { }
        return roots;
    }

    private boolean clickFileButton() {
        ArrayList<AccessibilityNodeInfo> candidates = new ArrayList<>();
        ArrayList<AccessibilityNodeInfo> roots = roots();
        for (AccessibilityNodeInfo root : roots) {
            collectFileCandidates(root, candidates);
        }
        recycleRoots(roots);
        AccessibilityNodeInfo best = chooseBestFileCandidate(candidates);
        recycleExcept(candidates, best);
        if (best != null) {
            boolean ok = performClick(best);
            try { best.recycle(); } catch (Exception ignored) { }
            if (ok) {
                handler.postDelayed(this::clickFileMenuItem, 250);
                handler.postDelayed(this::clickFileMenuItem, 650);
                handler.postDelayed(this::clickFileMenuItem, 1100);
                return true;
            }
        }

        // Some WebViews expose no accessible label for the attachment icon.
        // In that case use the site's own composer: tap its lower-left attachment
        // area, never launching Android's generic file chooser.
        roots = roots();
        Rect composer = new Rect();
        for (AccessibilityNodeInfo root : roots) findComposerBounds(root, composer);
        AccessibilityNodeInfo leftChild = null;
        for (AccessibilityNodeInfo root : roots) {
            AccessibilityNodeInfo c = findLeftBottomClickable(root, composer);
            if (c != null) { leftChild = c; break; }
        }
        recycleRoots(roots);
        if (leftChild != null) {
            boolean ok = performClick(leftChild);
            try { leftChild.recycle(); } catch (Exception ignored) { }
            if (ok) {
                handler.postDelayed(this::clickFileMenuItem, 250);
                handler.postDelayed(this::clickFileMenuItem, 650);
                handler.postDelayed(this::clickFileMenuItem, 1100);
                return true;
            }
        }
        if (composer.width() > 80 && composer.height() > 20) {
            // Attachment controls in WebView-based composers are commonly just
            // inside the lower-left edge of the composer. Do not tap to the
            // left of the composer (that can miss the site's own button).
            float x = composer.left + Math.max(20, Math.min(46, composer.height() * 0.55f));
            float y = composer.bottom - Math.max(18, Math.min(40, composer.height() * 0.32f));
            boolean tapped = tapScreenPoint(x, y);
            if (tapped) {
                handler.postDelayed(() -> tapScreenPoint(x + 18, y), 180);
                handler.postDelayed(this::clickFileMenuItem, 300);
                handler.postDelayed(this::clickFileMenuItem, 650);
                handler.postDelayed(this::clickFileMenuItem, 1000);
                handler.postDelayed(this::clickFileMenuItem, 1400);
            }
            return tapped;
        }
        return false;
    }

    private boolean clickFileMenuItem() {
        ArrayList<AccessibilityNodeInfo> candidates = new ArrayList<>();
        ArrayList<AccessibilityNodeInfo> roots = roots();
        for (AccessibilityNodeInfo root : roots) collectFileMenuCandidates(root, candidates);
        recycleRoots(roots);
        AccessibilityNodeInfo best = chooseBestFileMenuCandidate(candidates);
        recycleExcept(candidates, best);
        if (best == null) return false;
        boolean ok = performClick(best);
        try { best.recycle(); } catch (Exception ignored) { }
        return ok;
    }

    private void collectFileCandidates(AccessibilityNodeInfo n, ArrayList<AccessibilityNodeInfo> out) {
        if (n == null) return;
        try {
            if (isVisibleEnabled(n) && isFileNode(n)) out.add(AccessibilityNodeInfo.obtain(n));
            for (int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo c=n.getChild(i);
                if(c!=null){ collectFileCandidates(c,out); try{c.recycle();}catch(Exception ignored){} }
            }
        } catch(Exception ignored){}
    }

    private boolean isFileNode(AccessibilityNodeInfo n) {
        String all = nodeText(n);
        if (all.isEmpty()) return false;
        boolean label =
                all.contains("add files and more") || all.contains("add photos and files") ||
                all.contains("attach files") || all.contains("attach file") ||
                all.contains("add files") || all.contains("add file") ||
                all.contains("upload files") || all.contains("upload file") ||
                all.contains("choose file") || all.contains("select file") ||
                all.contains("انتخاب فایل") || all.contains("افزودن فایل") ||
                all.contains("ضمیمه") || all.contains("پیوست") || all.contains("بارگذاری فایل");
        boolean idHint = all.contains("attach") || all.contains("attachment") ||
                all.contains("file-upload") || all.contains("file_upload") ||
                all.contains("upload-file") || all.contains("upload_file");
        return label || idHint;
    }

    private AccessibilityNodeInfo chooseBestFileCandidate(ArrayList<AccessibilityNodeInfo> list) {
        AccessibilityNodeInfo best=null; int bestScore=Integer.MIN_VALUE;
        int h=screenH>0?screenH:getResources().getDisplayMetrics().heightPixels;
        for(AccessibilityNodeInfo n:list){
            String t=lower(n.getText()), d=lower(n.getContentDescription()), id=lower(n.getViewIdResourceName());
            String all=t+" "+d+" "+id; int s=0;
            if(d.contains("add files and more")||d.contains("add photos and files"))s+=180;
            if(all.contains("attach files")||all.contains("attach file"))s+=150;
            if(all.contains("add files")||all.contains("add file"))s+=140;
            if(all.contains("انتخاب فایل"))s+=130;
            if(all.contains("upload files")||all.contains("upload file"))s+=120;
            if(id.contains("attach")||id.contains("attachment")||id.contains("file-upload")||id.contains("file_upload"))s+=90;
            if(n.isClickable())s+=30;
            Rect r=new Rect();n.getBoundsInScreen(r);
            if(r.centerY()>h*0.50f)s+=20;
            if(r.width()>0&&r.height()>0)s+=5;
            if(s>bestScore){bestScore=s;best=n;}
        }
        return best;
    }

    private void collectFileMenuCandidates(AccessibilityNodeInfo n, ArrayList<AccessibilityNodeInfo> out) {
        if(n==null)return;
        try{
            String all=nodeText(n);
            boolean hit=all.contains("add files")||all.contains("add file")||all.contains("attach files")||
                    all.contains("choose file")||all.contains("select file")||all.contains("انتخاب فایل")||
                    all.contains("افزودن فایل")||all.contains("پیوست")||all.contains("ضمیمه");
            if(hit&&isVisibleEnabled(n)&&(n.isClickable()||hasClickAction(n)))out.add(AccessibilityNodeInfo.obtain(n));
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){collectFileMenuCandidates(c,out);try{c.recycle();}catch(Exception ignored){}}}
        }catch(Exception ignored){}
    }

    private AccessibilityNodeInfo chooseBestFileMenuCandidate(ArrayList<AccessibilityNodeInfo> list){
        AccessibilityNodeInfo best=null;int score=Integer.MIN_VALUE;
        for(AccessibilityNodeInfo n:list){String all=nodeText(n);int s=0;
            if(all.contains("add files"))s+=180;if(all.contains("add file"))s+=160;if(all.contains("attach files"))s+=140;
            if(all.contains("انتخاب فایل"))s+=140;if(all.contains("افزودن فایل"))s+=130;if(n.isClickable())s+=20;
            if(s>score){score=s;best=n;}}
        return best;
    }

    private boolean clickSendButton() {
        ArrayList<AccessibilityNodeInfo> candidates=new ArrayList<>();
        ArrayList<AccessibilityNodeInfo> roots=roots();
        for(AccessibilityNodeInfo root:roots)collectSendCandidates(root,candidates);
        recycleRoots(roots);
        AccessibilityNodeInfo best=chooseBestSendCandidate(candidates);
        recycleExcept(candidates,best);
        if(best!=null){
            boolean ok=performClick(best);
            try{best.recycle();}catch(Exception ignored){}
            if(ok)return true;
        }

        // Some WebViews expose only the composer. Prefer a real clickable child
        // in the lower-right corner before using a coordinate gesture fallback.
        roots=roots();
        Rect composer=new Rect();
        for(AccessibilityNodeInfo root:roots)findComposerBounds(root,composer);
        AccessibilityNodeInfo clickChild=null;
        for(AccessibilityNodeInfo root:roots){
            AccessibilityNodeInfo c=findRightBottomClickable(root,composer);
            if(c!=null){clickChild=c;break;}
        }
        recycleRoots(roots);
        if(clickChild!=null){boolean ok=performClick(clickChild);try{clickChild.recycle();}catch(Exception ignored){}if(ok)return true;}

        if(composer.width()>40&&composer.height()>20){
            float x=composer.right-Math.max(26,Math.min(58,composer.height()*0.60f));
            float y=composer.bottom-Math.max(18,Math.min(42,composer.height()*0.34f));
            boolean tapped=tapScreenPoint(x,y);
            if(tapped){
                handler.postDelayed(() -> tapScreenPoint(x-18,y),180);
                handler.postDelayed(() -> tapScreenPoint(x,y-18),360);
            }
            return tapped;
        }
        return false;
    }

    private void collectSendCandidates(AccessibilityNodeInfo n,ArrayList<AccessibilityNodeInfo> out){
        if(n==null)return;try{
            if(isVisibleEnabled(n)&&isSendNode(n))out.add(AccessibilityNodeInfo.obtain(n));
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){collectSendCandidates(c,out);try{c.recycle();}catch(Exception ignored){}}}
        }catch(Exception ignored){}
    }

    private boolean isSendNode(AccessibilityNodeInfo n){
        String all=nodeText(n);if(all.isEmpty())return false;
        return all.equals("send")||all.equals("ارسال")||all.contains("send message")||all.contains("send prompt")||
                all.contains("send-button")||all.contains("send_button")||all.contains("sendbutton")||all.contains("submit")||
                all.contains("submit message")||all.contains("ارسال پیام");
    }

    private AccessibilityNodeInfo chooseBestSendCandidate(ArrayList<AccessibilityNodeInfo> list){
        AccessibilityNodeInfo best=null;int score=Integer.MIN_VALUE;int h=screenH>0?screenH:getResources().getDisplayMetrics().heightPixels;
        for(AccessibilityNodeInfo n:list){String t=lower(n.getText()),d=lower(n.getContentDescription()),id=lower(n.getViewIdResourceName());int s=0;
            if(t.equals("send")||t.equals("ارسال"))s+=140;if(d.contains("send prompt")||d.contains("send message")||d.contains("ارسال پیام"))s+=130;
            if(id.contains("send"))s+=100;if(n.isClickable())s+=30;Rect r=new Rect();n.getBoundsInScreen(r);if(r.centerY()>h*.5f)s+=20;if(r.width()>0&&r.height()>0)s+=5;
            if(s>score){score=s;best=n;}}
        return best;
    }

    private void findComposerBounds(AccessibilityNodeInfo n,Rect out){
        if(n==null)return;try{
            String cls=lower(n.getClassName());boolean editable=n.isEditable()||cls.contains("edittext")||cls.contains("editable")||cls.contains("textinput");
            if(editable&&n.isVisibleToUser()){Rect r=new Rect();n.getBoundsInScreen(r);if(r.width()>out.width()&&r.height()>out.height())out.set(r);}
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){findComposerBounds(c,out);try{c.recycle();}catch(Exception ignored){}}}
        }catch(Exception ignored){}
    }

    private AccessibilityNodeInfo findLeftBottomClickable(AccessibilityNodeInfo n,Rect composer){
        if(n==null||composer==null||composer.width()<=40)return null;
        AccessibilityNodeInfo best=null; int bestScore=-10000;
        try{
            if(isVisibleEnabled(n)&&(n.isClickable()||hasClickAction(n))){
                Rect r=new Rect(); n.getBoundsInScreen(r);
                if(r.centerX()>=composer.left-40&&r.centerX()<=composer.left+Math.max(90,composer.width()*0.30f) &&
                   r.centerY()>=composer.top&&r.centerY()<=composer.bottom+40){
                    String all=nodeText(n); int s=80-Math.min(60,Math.abs(composer.left-r.left)*2+Math.abs(composer.bottom-r.bottom)*2);
                    if(all.contains("attach")||all.contains("file")||all.contains("upload")||all.contains("پیوست")||all.contains("فایل")||all.contains("ضمیمه"))s+=260;
                    if(s>bestScore){bestScore=s;best=AccessibilityNodeInfo.obtain(n);}
                }
            }
            for(int i=0;i<n.getChildCount();i++){
                AccessibilityNodeInfo c=n.getChild(i);
                if(c!=null){
                    AccessibilityNodeInfo got=findLeftBottomClickable(c,composer);
                    if(got!=null){
                        Rect r=new Rect(); got.getBoundsInScreen(r); String all=nodeText(got);
                        int s=80-Math.min(60,Math.abs(composer.left-r.left)*2+Math.abs(composer.bottom-r.bottom)*2);
                        if(all.contains("attach")||all.contains("file")||all.contains("upload")||all.contains("پیوست")||all.contains("فایل")||all.contains("ضمیمه"))s+=260;
                        if(s>bestScore){if(best!=null)try{best.recycle();}catch(Exception ignored){} best=got;bestScore=s;} else try{got.recycle();}catch(Exception ignored){}
                    }
                    try{c.recycle();}catch(Exception ignored){}
                }
            }
        }catch(Exception ignored){}
        return bestScore>-10000?best:null;
    }

    private AccessibilityNodeInfo findRightBottomClickable(AccessibilityNodeInfo n,Rect composer){
        if(n==null||composer==null||composer.width()<=40)return null;AccessibilityNodeInfo best=null;int bestScore=-10000;
        try{
            if(isVisibleEnabled(n)&&(n.isClickable()||hasClickAction(n))){Rect r=new Rect();n.getBoundsInScreen(r);
                if(r.centerX()>=composer.left&&r.centerX()<=composer.right+40&&r.centerY()>=composer.top&&r.centerY()<=composer.bottom+40){
                    int s=100-Math.min(80,Math.abs(composer.right-r.right)*2+Math.abs(composer.bottom-r.bottom)*2);String all=nodeText(n);
                    if(all.contains("send")||all.contains("submit")||all.contains("ارسال"))s+=250;
                    if(s>bestScore){bestScore=s;best=AccessibilityNodeInfo.obtain(n);}
                }
            }
            for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){AccessibilityNodeInfo got=findRightBottomClickable(c,composer);if(got!=null){Rect r=new Rect();got.getBoundsInScreen(r);int s=100-Math.min(80,Math.abs(composer.right-r.right)*2+Math.abs(composer.bottom-r.bottom)*2);String all=nodeText(got);if(all.contains("send")||all.contains("submit")||all.contains("ارسال"))s+=250;if(s>bestScore){if(best!=null)try{best.recycle();}catch(Exception ignored){}best=got;bestScore=s;}else try{got.recycle();}catch(Exception ignored){}}try{c.recycle();}catch(Exception ignored){}}}
        }catch(Exception ignored){}
        return bestScore>-10000?best:null;
    }

    private boolean performClick(AccessibilityNodeInfo n){
        try{if(!n.isEnabled())return false;if(n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;for(AccessibilityNodeInfo.AccessibilityAction a:n.getActionList())if(a.getId()==AccessibilityNodeInfo.ACTION_CLICK&&n.performAction(a.getId()))return true;}catch(Exception ignored){}return false;
    }

    private boolean tapScreenPoint(float x,float y){
        if(Build.VERSION.SDK_INT<24)return false;try{Path p=new Path();p.moveTo(x,y);GestureDescription.StrokeDescription s=new GestureDescription.StrokeDescription(p,0,80);return dispatchGesture(new GestureDescription.Builder().addStroke(s).build(),null,null);}catch(Exception e){return false;}
    }

    private boolean isVisibleEnabled(AccessibilityNodeInfo n){return n!=null&&n.isVisibleToUser()&&n.isEnabled();}
    private boolean hasClickAction(AccessibilityNodeInfo n){try{for(AccessibilityNodeInfo.AccessibilityAction a:n.getActionList())if(a.getId()==AccessibilityNodeInfo.ACTION_CLICK)return true;}catch(Exception ignored){}return false;}
    private String lower(CharSequence s){return s==null?"":s.toString().trim().toLowerCase(Locale.ROOT);}
    private String nodeText(AccessibilityNodeInfo n){if(n==null)return "";return (lower(n.getText())+" "+lower(n.getContentDescription())+" "+lower(n.getViewIdResourceName())).trim();}
    private void recycleRoots(ArrayList<AccessibilityNodeInfo> roots){for(AccessibilityNodeInfo r:roots)try{r.recycle();}catch(Exception ignored){}}
    private void recycleExcept(ArrayList<AccessibilityNodeInfo> list,AccessibilityNodeInfo keep){for(AccessibilityNodeInfo n:list)if(n!=keep)try{n.recycle();}catch(Exception ignored){}}
}
