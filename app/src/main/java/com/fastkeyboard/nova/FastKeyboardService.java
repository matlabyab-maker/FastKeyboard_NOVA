package com.fastkeyboard.nova;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;

public class FastKeyboardService extends InputMethodService {
    private static final int NAVY = Color.rgb(23,61,112);
    private static final int BROWN = Color.rgb(117,61,18);
    private static final int RED = Color.rgb(215,20,20);
    private static final int CREAM = Color.rgb(250,249,242);
    private static final int YELLOW = Color.rgb(255,224,128);
    private static final int BLUE = Color.rgb(25,95,170);
    private static final int PINK = Color.rgb(252,220,220);

    private boolean caps=false, capsLocked=false, symbols=false, english=false;
    private long lastCapsTap=0;
    private int resizeLevel=0;
    private int normalWindowHeight=0;
    private final ArrayList<String> history=new ArrayList<>();
    private final ArrayList<String> clipboardHistory=new ArrayList<>();
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipboardListener;
    private SharedPreferences prefs;
    private LinearLayout currentRoot;
    private int keyboardColor=CREAM;
    private final Handler handler=new Handler();
    private final ExecutorService suggestionExecutor=Executors.newSingleThreadExecutor();
    private ToneGenerator backspaceTone;
    private final Predictor predictor=new Predictor();
    private final ArrayList<Button> suggestionButtons=new ArrayList<>();
    private PopupWindow activePopup;
    private int mousePopupX=0, mousePopupY=0;
    private final Runnable suggestionUpdateRunnable=()->updateSuggestionsNow();

    private static final String[] PERSIAN_NUMBERS={"۱","۲","۳","۴","۵","۶","۷","۸","۹","۰"};
    private static final String[] NUMBER_MARKS={"!","@","#","$","%","^","&","*","(",")"};
    private static final String[] PERSIAN_R1={"ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"};
    private static final String[] PERSIAN_R2={"ش","س","ی","ب","ل","ا","ت","ن","م","ک","گ"};
    private static final String[] PERSIAN_R3={"ظ","ط","ژ","ز","ر","ذ","د","پ","و","چ"};
    private static final String[] PERSIAN_MARKS_R1={"!","@","#","$","%","^","&","*","(",")","["};
    private static final String[] PERSIAN_MARKS_R2={"]","{","}","<",">","=","+","-","_","/","\\"};
    private static final String[] EN_R1={"q","w","e","r","t","y","u","i","o","p","["};
    private static final String[] EN_R2={"a","s","d","f","g","h","j","k","l",";","'"};
    private static final String[] EN_R3={"z","x","c","v","b","n","m",",",".","/"};
    private static final String[] EN_MARKS_R1={"!","@","#","$","%","^","&","*","(",")","["};
    private static final String[] EN_MARKS_R2={"@","#","$","%","&","*","-","_",";",":","'"};
    private static final String[] EN_MARKS_R3={"<",">","{","}","[","]","\\","|","?","/"};

    private static final String[] ALIF_VARIANTS={"ا","آ","أ","إ","ٱ","ؤ","ئ"};
    private static final String[] YEH_VARIANTS={"ی","ي","ى","ئ","ې","ے"};
    private static final String[] VAV_VARIANTS={"و","ؤ","ۆ","ۇ","ۈ","ۋ"};
    // Long-press variants for the ± key. Exact requested symbols are included,
    // together with closely related typographic forms where useful.
    private static final String[] PM_VARIANTS={"«","»","_","-","!",":","+",";","\"","=","×","[","]","؛","≤","≥","~"};
    private static final String[] ARABIC_MARKS={"َ","ِ","ُ","ً","ٍ","ٌ","ْ","ّ","ٰ","ٔ","ٕ","ٖ","ٗ","٘","ٙ","ٚ","ٛ","ٜ","ٝ","ٞ","ٟ","ـ","ء","آ","أ","ؤ","إ","ئ","ة","ى","لا"};

    private static final String[] EMOJIS={"😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕","🤑","🤠","😈","👿","👹","👺","🤡","💩","👻","💀","☠️","👽","👾","🤖","🎃","😺","😸","😹","😻","😼","😽","🙀","😿","😾","🙈","🙉","🙊","💋","💘","💝","💖","💗","💓","💞","💕","💟","❣️","💔","❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💯","💥","💫","💦","💨","💣","💬","👋","🤚","🖐️","✋","🖖","👌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","👇","☝️","👍","👎","✊","👊","🤝","🙏","👏","🙌","💪","👀","🧠","👄","👅","👂","👃","👶","🧒","👦","👧","🧑","👨","👩","🧓","👴","👵","🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🐔","🐧","🐦","🐤","🦄","🐝","🦋","🐌","🐞","🐜","🐢","🐍","🦎","🦂","🐙","🦀","🐠","🐟","🐬","🐳","🐊","🐘","🦏","🦒","🦓","🐎","🐕","🐈","🐓","🦜","🦢","🌹","🌷","🌻","🌞","🌈","☀️","⭐","🌟","✨","⚡","❄️","🔥","🌊","🍎","🍊","🍋","🍉","🍇","🍓","🍒","🍑","🍍","🥝","🍅","🥑","🍞","🧀","🍔","🍕","🍟","🌭","🍿","🍩","🍪","🎂","🍰","🍫","🍬","☕","🍵","⚽","🏀","🏈","⚾","🎾","🏐","🏆","🥇","🚗","🚕","🚌","🚓","🚑","🚒","✈️","🚁","🚀","🚲","🏠","🏢","🏥","🏫","⛪","🕌","🛒","📱","💻","⌚","📷","📺","🎧","🎵","🎶","🎸","🎹","🎮","🎲","🎯","🎁","🎈","🎉","🎊","📌","📍","🔑","🔒","🔓","⚙️","🔔","🔍","🔎","💡","📁","📂","🗂️","🗃️","🗄️","📦","🗑️","📝","📄","📋","📎","📚","📖"};
    private static final String[] FLAGS={"🇮🇷","🇺🇸","🇬🇧","🇨🇦","🇦🇺","🇩🇪","🇫🇷","🇮🇹","🇪🇸","🇵🇹","🇹🇷","🇷🇺","🇺🇦","🇨🇳","🇯🇵","🇰🇷","🇮🇳","🇵🇰","🇦🇫","🇮🇶","🇸🇦","🇦🇪","🇶🇦","🇰🇼","🇧🇭","🇴🇲","🇪🇬","🇯🇴","🇱🇧","🇸🇾","🇵🇸","🇬🇷","🇳🇱","🇧🇪","🇨🇭","🇦🇹","🇸🇪","🇳🇴","🇩🇰","🇫🇮","🇵🇱","🇨🇿","🇭🇺","🇷🇴","🇧🇬","🇷🇸","🇭🇷","🇦🇱","🇧🇦","🇬🇪","🇦🇲","🇦🇿","🇰🇿","🇺🇿","🇹🇯","🇹🇲","🇰🇬","🇳🇿","🇿🇦","🇳🇬","🇰🇪","🇲🇦","🇩🇿","🇹🇳","🇧🇷","🇦🇷","🇨🇱","🇨🇴","🇲🇽","🇺🇾","🇻🇪","🇵🇪","🇨🇺","🇯🇲","🇸🇬","🇲🇾","🇮🇩","🇹🇭","🇻🇳","🇵🇭"};
    private static final String[] SYMBOLS={"!","@","#","$","%","^","&","*","(",")","-","_","+","=","[","]","{","}","\\","|",";",":","'","\"",",",".","<",">","/","?","~","`","§","¶","©","®","™","€","£","¥","₽","₹","₺","₩","₴","₦","₱","₲","₵","₡","₫","฿","∞","≈","≠","≤","≥","±","×","÷","√","∑","∏","∆","∇","∂","∫","∮","π","µ","Ω","α","β","γ","δ","θ","λ","σ","φ","ψ","ω","←","↑","→","↓","↔","↕","↖","↗","↘","↙","⇐","⇑","⇒","⇓","↻","↺","✓","✔","✕","✖","✗","✘","★","☆","●","○","■","□","◆","◇","▲","△","▼","▽","♥","♡","♦","♢","♣","♤","♧","☀","☁","☂","☃","☄","☎","☑","☒","☐","⚠","⚡","⚙","⚓","⚽","♠","♣","♥","♦","♪","♫","†","‡","‰","′","″","↪","↩","⌂","⌘","⌫","⏎","␣","◀","▶","⏪","⏩","⏮","⏭","⏸","⏹","⏺","🔒","🔓","🔑","🔔","🔕","🔗","🗝️"};

    @Override public void onCreate(){super.onCreate();try{backspaceTone=new ToneGenerator(AudioManager.STREAM_SYSTEM,75);}catch(Exception ignored){}prefs=getSharedPreferences("fkp2",Context.MODE_PRIVATE);loadHistory();loadClipboardHistory();clipboardManager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboardListener=()->capturePrimaryClip();if(clipboardManager!=null){clipboardManager.addPrimaryClipChangedListener(clipboardListener);capturePrimaryClip();}keyboardColor=prefs.getInt("keyboardColor",CREAM); if(!prefs.getBoolean("suggestions_cleared_v19",false)){prefs.edit().remove("predictor").remove("suggestions_seed").putBoolean("suggestions_cleared_v19",true).apply();} predictor.load(prefs); loadSuggestionAssets();}
    @Override public View onCreateInputView(){return buildKeyboard();}
    @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);if(restarting)rebuild(); scheduleSuggestions();}
    @Override public void onFinishInputView(boolean finishingInput){super.onFinishInputView(finishingInput);MouseAccessibilityService.hideCursorFromKeyboard();}

    private void loadSuggestionAssets(){
        loadSuggestionAsset("suggestions_fa.txt");
        loadSuggestionAsset("suggestions_en.txt");
    }
    private void loadSuggestionAsset(String name){
        try(BufferedReader br=new BufferedReader(new InputStreamReader(getAssets().open(name),"UTF-8"))){
            String line;
            while((line=br.readLine())!=null){
                String w=line.trim();
                if(!w.isEmpty() && !w.startsWith("#")) predictor.seedCommon(w,3);
            }
        }catch(Exception ignored){}
    }

    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(backspaceTone!=null){try{backspaceTone.release();}catch(Exception ignored){}backspaceTone=null;}suggestionExecutor.shutdownNow();if(clipboardManager!=null&&clipboardListener!=null){try{clipboardManager.removePrimaryClipChangedListener(clipboardListener);}catch(Exception ignored){}}MouseAccessibilityService.hideCursorFromKeyboard();super.onDestroy();}
    @Override public void onUpdateSelection(int oldSelStart,int oldSelEnd,int newSelStart,int newSelEnd,int candidatesStart,int candidatesEnd){super.onUpdateSelection(oldSelStart,oldSelEnd,newSelStart,newSelEnd,candidatesStart,candidatesEnd);scheduleSuggestions();}

    private LinearLayout buildKeyboard(){
        LinearLayout root=new LinearLayout(this);currentRoot=root;root.setOrientation(LinearLayout.VERTICAL);root.setPadding(1,1,1,1);root.setBackgroundColor(keyboardColor);root.setLayoutParams(new ViewGroup.LayoutParams(-1,-1));
        root.post(() -> { if(normalWindowHeight<=0 && root.getHeight()>0) normalWindowHeight=root.getHeight(); });
        LinearLayout tools=row(1.05f);
        String[] labels={"Paste","Copy\nAll","Copy\nScreen","Cut","Undo","Redo","100\nHistory","امکانات","➤","Resize"};
        String[] icons={"▣","⧉","▣","✂","↶","↷","▤","⚙","➤","↕"};
        for(int i=0;i<labels.length;i++){Button b=keyWithIcon(labels[i],icons[i],12,NAVY,CREAM);tools.addView(b,weight(1));final int n=i;switch(n){case 0:b.setOnClickListener(v->paste());break;case 1:b.setOnClickListener(v->copyAll());break;case 2:b.setOnClickListener(v->copyAll());break;case 3:b.setOnClickListener(v->cut());break;case 4:b.setOnClickListener(v->ctrlKey(KeyEvent.KEYCODE_Z));break;case 5:b.setOnClickListener(v->ctrlKey(KeyEvent.KEYCODE_Y));break;case 6:b.setOnClickListener(v->showHistory(v));break;case 7:b.setOnClickListener(v->showTools(v));break;case 8:b.setOnClickListener(this::showMouse);break;default:b.setOnClickListener(v->toggleResize());}}
        root.addView(tools);
        LinearLayout suggestions=row(0f); suggestions.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(44),0f)); suggestionButtons.clear(); for(int i=0;i<6;i++){Button b=key("",14,NAVY,CREAM); b.setSingleLine(true); b.setMaxLines(1); b.setEllipsize(android.text.TextUtils.TruncateAt.END); b.setHorizontallyScrolling(true); b.setIncludeFontPadding(false); b.setMinHeight(0); b.setMinWidth(0); b.setGravity(Gravity.CENTER); suggestions.addView(b,weight(1)); suggestionButtons.add(b); b.setOnClickListener(v->{String text=((Button)v).getText().toString(); if(!text.isEmpty()) applySuggestion(text);});} Button send=key("ارسال",14,Color.rgb(25,95,170),CREAM); send.setSingleLine(true); send.setMaxLines(1); send.setEllipsize(android.text.TextUtils.TruncateAt.END); send.setHorizontallyScrolling(true); send.setIncludeFontPadding(false); send.setMinHeight(0); send.setMinWidth(0); send.setGravity(Gravity.CENTER); send.setTag(CREAM); send.setBackground(makeBg(CREAM)); send.setContentDescription("ارسال"); send.setOnClickListener(v->performSendAction()); suggestions.addView(send,weight(1));
        Button file=key("فایل",14,Color.rgb(28,130,65),CREAM); file.setSingleLine(true); file.setMaxLines(1); file.setEllipsize(android.text.TextUtils.TruncateAt.END); file.setHorizontallyScrolling(true); file.setIncludeFontPadding(false); file.setMinHeight(0); file.setMinWidth(0); file.setGravity(Gravity.CENTER); file.setTag(CREAM); file.setBackground(makeBg(CREAM)); file.setContentDescription("فایل"); file.setOnClickListener(v->performFileAction()); suggestions.addView(file,weight(1));
        root.addView(suggestions); root.post(this::scheduleSuggestions);
        LinearLayout nums=row(1f);String[] numsText=english?new String[]{"1","2","3","4","5","6","7","8","9","0"}:PERSIAN_NUMBERS;for(int i=0;i<10;i++){Button b=dualKey(numsText[i],NUMBER_MARKS[i],19,BROWN,RED,CREAM);nums.addView(b,weight(1));addDualKeyBehavior(b, numsText[i], NUMBER_MARKS[i]);}Button back=key("⌫",22,NAVY,PINK);nums.addView(back,weight(1.45f));addBackspaceRepeat(back);root.addView(nums);
        LinearLayout letters=new LinearLayout(this);letters.setOrientation(LinearLayout.HORIZONTAL);letters.setLayoutParams(new LinearLayout.LayoutParams(-1,0,2f));
        LinearLayout letterRows=new LinearLayout(this);letterRows.setOrientation(LinearLayout.VERTICAL);letterRows.setLayoutParams(new LinearLayout.LayoutParams(0,-1,11f));
        addLetterRow(letterRows,english?EN_R1:PERSIAN_R1,english?EN_MARKS_R1:PERSIAN_MARKS_R1);addLetterRow(letterRows,english?EN_R2:PERSIAN_R2,english?EN_MARKS_R2:PERSIAN_MARKS_R2);
        letters.addView(letterRows);Button enter=key("Enter",16,NAVY,Color.rgb(214,232,255));letters.addView(enter,new LinearLayout.LayoutParams(0,-1,1.2f));enter.setOnClickListener(v->sendKey(KeyEvent.KEYCODE_ENTER));root.addView(letters);
        LinearLayout third=row(1f);Button capsB=key(capsLocked?"Caps 🔒":"Caps",16,NAVY,caps?YELLOW:CREAM);third.addView(capsB,weight(1.2f));capsB.setOnClickListener(v->{long now=android.os.SystemClock.uptimeMillis();if(now-lastCapsTap<450){capsLocked=!capsLocked;caps=capsLocked;lastCapsTap=0;}else{caps=!caps;lastCapsTap=now;}rebuild();});String[] r3=english?EN_R3:PERSIAN_R3;for(int i=0;i<r3.length;i++){String s=caps?r3[i].toUpperCase():r3[i];Button b=key(s,20,NAVY,CREAM);third.addView(b,weight(1));final String out=s;b.setOnClickListener(v->{commit(out);if(!capsLocked&&caps){caps=false;rebuild();}});if(!english&&s.equals("و")){addSymbolVariantsLongPress(b,VAV_VARIANTS);}}Button qmark=key("؟",20,RED,CREAM);third.addView(qmark,weight(1));qmark.setOnClickListener(v->commit("؟"));root.addView(third);
        LinearLayout bottom=row(1.08f);Button emoji=keyWithIcon("اموجی","☺",14,NAVY,CREAM);Button sym=keyWithIcon("123\n!@...","⌘",13,NAVY,symbols?YELLOW:CREAM);Button globe=key(english?"🌐 EN":"🌐 FA",18,BLUE,CREAM);Button space=key("Space",19,NAVY,CREAM);Button comma=key(english?",":"،",23,RED,CREAM);Button question=key(".",23,RED,CREAM);Button pm=key("◆",18,RED,CREAM);Button left=key("←",23,BLUE,CREAM);Button right=key("→",23,BLUE,CREAM);Button up=key("↑",23,BLUE,CREAM);Button down=key("↓",23,BLUE,CREAM);bottom.addView(emoji,weight(.82f));bottom.addView(sym,weight(1.15f));bottom.addView(globe,weight(.9f));bottom.addView(space,weight(2.35f));bottom.addView(comma,weight(.72f));bottom.addView(question,weight(.72f));bottom.addView(pm,weight(.72f));bottom.addView(left,weight(.95f));bottom.addView(right,weight(.95f));bottom.addView(up,weight(.82f));bottom.addView(down,weight(.82f));emoji.setOnClickListener(v->showEmoji(v));sym.setOnClickListener(v->showSymbols(v));globe.setOnClickListener(v->{english=!english;symbols=false;rebuild();});space.setOnClickListener(v->commitSpaceAndLearn());comma.setOnClickListener(v->commit(((Button)v).getText().toString()));question.setOnClickListener(v->commit("."));pm.setOnClickListener(v->commit("◆"));addSymbolVariantsLongPress(pm,PM_VARIANTS);addArrowRepeat(left,KeyEvent.KEYCODE_DPAD_LEFT);addArrowRepeat(right,KeyEvent.KEYCODE_DPAD_RIGHT);addArrowRepeat(up,KeyEvent.KEYCODE_DPAD_UP);addArrowRepeat(down,KeyEvent.KEYCODE_DPAD_DOWN);root.addView(bottom);return root;
    }

    private void addLetterRow(LinearLayout parent,String[] letters,String[] marks){LinearLayout r=row(1f);for(int i=0;i<letters.length;i++){String s=caps?letters[i].toUpperCase():letters[i];Button b=key(s,22,NAVY,CREAM);r.addView(b,weight(1));final String out=s;b.setOnClickListener(v->{commit(out);if(!capsLocked&&caps){caps=false;rebuild();}});if(!english&&s.equals("ا")){addAlifLongPress(b);}else if(!english&&s.equals("ی")){addSymbolVariantsLongPress(b,YEH_VARIANTS);}else if(!english&&s.equals("و")){addSymbolVariantsLongPress(b,VAV_VARIANTS);}}parent.addView(r);}
    private Button dualKey(String main,String mark,float size,int fg,int markColor,int bg){DualButton b=new DualButton(this);b.setMainMark(main,mark,size,fg,markColor);b.setAllCaps(false);b.setTypeface(Typeface.create("sans",Typeface.NORMAL));b.setPadding(0,0,0,0);b.setMinHeight(0);b.setMinWidth(0);b.setTag(main);b.setBackground(makeBg(bg));installHighlight(b);return b;}
    private static class DualButton extends Button {
        private String main="", mark=""; private float mainSize=20; private int mainColor=Color.BLACK, markColor=Color.RED;
        DualButton(Context c){super(c);setWillNotDraw(false);setText("");}
        void setMainMark(String m,String k,float size,int mc,int kc){main=m;mark=k;mainSize=size;mainColor=mc;markColor=kc;invalidate();}
        @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextAlign(android.graphics.Paint.Align.CENTER);p.setTextSize(mainSize*getResources().getDisplayMetrics().scaledDensity);p.setColor(mainColor);float cy=getHeight()/2f-(p.ascent()+p.descent())/2f;c.drawText(main,getWidth()/2f,cy,p);p.setTextAlign(android.graphics.Paint.Align.LEFT);p.setTextSize(mainSize*.58f*getResources().getDisplayMetrics().scaledDensity);p.setColor(markColor);c.drawText(mark,dpStatic(getResources(),6),getHeight()-dpStatic(getResources(),5),p);}
        private static int dpStatic(android.content.res.Resources r,int v){return Math.round(v*r.getDisplayMetrics().density);}
    }
    private Button key(String text,float size,int fg,int bg){Button b=new Button(this);b.setText(text);b.setTextSize(size);b.setTextColor(fg);b.setGravity(Gravity.CENTER);b.setAllCaps(false);b.setTypeface(Typeface.create("sans",Typeface.NORMAL));b.setPadding(0,0,0,0);b.setMinHeight(0);b.setMinWidth(0);b.setIncludeFontPadding(true);b.setTag(bg);b.setBackground(makeBg(bg));installHighlight(b);return b;}
    private Button keyWithIcon(String text,String icon,float size,int fg,int bg){Button b=key(text,size,fg,bg);b.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);b.setContentDescription(text+" "+icon);return b;}
    private void installHighlight(Button b){b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){b.setBackground(makeBg(YELLOW));}else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){v.postDelayed(()->{Object t=b.getTag();b.setBackground(makeBg(t instanceof Integer?(Integer)t:CREAM));},80);}return false;});}
    private void addBackspaceRepeat(Button b){final boolean[] repeating={false};final Runnable[] repeat={null};repeat[0]=()->{repeating[0]=true;playBackspaceSound();backspace();handler.postDelayed(repeat[0],90);};b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){b.setBackground(makeBg(YELLOW));repeating[0]=false;playBackspaceSound();backspace();handler.postDelayed(repeat[0],420);return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(repeat[0]);b.setBackground(makeBg(PINK));return true;}return true;});}
    private void playBackspaceSound(){try{if(backspaceTone!=null)backspaceTone.startTone(ToneGenerator.TONE_PROP_BEEP,45);}catch(Exception ignored){}}
    private void addArrowRepeat(Button b,int keyCode){final boolean[] repeating={false};final Runnable[] repeat={null};repeat[0]=()->{repeating[0]=true;sendKey(keyCode);handler.postDelayed(repeat[0],90);};b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){b.setBackground(makeBg(YELLOW));repeating[0]=false;sendKey(keyCode);handler.postDelayed(repeat[0],380);return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(repeat[0]);b.setBackground(makeBg(CREAM));return true;}return true;});}
    private void addDualKeyBehavior(Button b,String main,String mark){
        final boolean[] repeating={false}; final Runnable[] repeat={null};
        repeat[0]=()->{
            repeating[0]=true;
            commit(TextUtils.isEmpty(mark) ? main : mark);
            handler.postDelayed(repeat[0],110);
        };
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                b.setBackground(makeBg(YELLOW));
                repeating[0]=false;
                handler.postDelayed(repeat[0],350);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
                handler.removeCallbacks(repeat[0]);
                if(!repeating[0]) { commit(main); if(caps && !capsLocked && main.length()==1 && Character.isLetter(main.charAt(0))){caps=false;rebuild();} }
                b.setBackground(makeBg(CREAM));
                return true;
            }
            return true;
        });
    }
    private void showSymbols(View anchor){symbols=true;showRepeatGridPopup(anchor,SYMBOLS,42,300);}

    private void addSymbolVariantsLongPress(Button b,String[] items){
        final boolean[] shown={false};
        final Runnable[] r={null};
        r[0]=()->{shown[0]=true;showSymbolVariants(b,items);};
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                shown[0]=false;
                handler.postDelayed(r[0],450);
                b.setBackground(makeBg(YELLOW));
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
                handler.removeCallbacks(r[0]);
                Object t=b.getTag();
                b.setBackground(makeBg(t instanceof Integer?(Integer)t:CREAM));
                if(!shown[0] && e.getAction()==MotionEvent.ACTION_UP) b.performClick();
                return true;
            }
            return true;
        });
    }
    private void showSymbolVariants(View anchor,String[] items){
        int[] loc=popupLocation(anchor);
        dismissPopup();
        ScrollView sv=scrollBox();
        LinearLayout box=gridContainer(sv);
        addAlifGrid(box,items,52);
        int h=Math.min(210,Math.max(120,((items.length+6)/7)*52+10));
        activePopup=new PopupWindow(sv,dp(360),dp(h),true);
        stylePopup(activePopup);
        showPopupAt(activePopup,loc[0],loc[1],h);
    }

    private void addAlifLongPress(Button b){final boolean[] shown={false};final Runnable[] r={null};r[0]=()->{shown[0]=true;showAlifVariants(b);};b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){shown[0]=false;handler.postDelayed(r[0],450);b.setBackground(makeBg(YELLOW));return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(r[0]);Object t=b.getTag();b.setBackground(makeBg(t instanceof Integer?(Integer)t:CREAM));if(!shown[0]){b.performClick();}return true;}return true;});}
    private void showAlifVariants(View anchor){
        int[] loc=popupLocation(anchor);
        dismissPopup();
        ScrollView sv=scrollBox();
        LinearLayout box=gridContainer(sv);
        addAlifGrid(box,ALIF_VARIANTS,52);
        activePopup=new PopupWindow(sv,dp(330),dp(150),true);
        stylePopup(activePopup);
        showPopupAt(activePopup,loc[0],loc[1],150);
    }
    private void addAlifGrid(LinearLayout box,String[] items,int cell){
        LinearLayout r=null; int count=0;
        for(String item:items){
            if(count%7==0){r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);box.addView(r,new LinearLayout.LayoutParams(-1,dp(cell)));}
            final String shown=decodeSymbol(item);
            Button b=key(shown,24,NAVY,CREAM);
            r.addView(b,weight(1));
            b.setOnClickListener(v->{
                commit(decodeSymbol(((Button)v).getText().toString()));
                dismissPopup();
            });
            count++;
        }
    }
    private LinearLayout row(float w){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.FILL);r.setPadding(0,0,0,0);r.setLayoutParams(new LinearLayout.LayoutParams(-1,0,w));return r;}
    private LinearLayout.LayoutParams weight(float w){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,w);p.setMargins(0,0,0,0);return p;}
    private GradientDrawable makeBg(int color){GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(8);gd.setStroke(1,Color.rgb(210,208,200));return gd;}
    private void scheduleSuggestions(){
        handler.removeCallbacks(suggestionUpdateRunnable);
        handler.postDelayed(suggestionUpdateRunnable,160);
    }
    private void updateSuggestions(){ scheduleSuggestions(); }
    private void updateSuggestionsNow(){
        if(suggestionButtons.isEmpty()) return;
        InputConnection ic=getCurrentInputConnection();
        String before="";
        if(ic!=null){ CharSequence cs=ic.getTextBeforeCursor(80,0); if(cs!=null) before=cs.toString(); }
        final String context=before;
        suggestionExecutor.execute(() -> {
            final List<String> list=predictor.suggest(context,6);
            handler.post(() -> {
                if(suggestionButtons.isEmpty()) return;
                for(int i=0;i<suggestionButtons.size();i++){
                    Button b=suggestionButtons.get(i);
                    if(i<list.size()){b.setText(list.get(i));b.setVisibility(View.VISIBLE);}
                    else {b.setText("");b.setVisibility(View.INVISIBLE);}
                }
            });
        });
    }
    private void performSendAction(){
        // This button is intentionally NOT an Enter/IME action.
        // It is meant to activate the visible blue Send button of the active app
        // (for example ChatGPT in a browser) through the AccessibilityService.
        boolean handled = MouseAccessibilityService.clickSendButtonFromKeyboard();
        if(!handled){
            android.widget.Toast.makeText(this, "برای ارسال واقعی، «موس سیستمی» را در تنظیمات Android فعال کنید.", android.widget.Toast.LENGTH_SHORT).show();
        }
        scheduleSuggestions();
    }

    private void performFileAction(){
        // This button is intentionally NOT a direct Android file-picker command.
        // It activates the active site's visible attachment/file button first,
        // so the site's own file chooser / device storage explorer opens.
        boolean handled = MouseAccessibilityService.clickFileButtonFromKeyboard();
        if(!handled){
            android.widget.Toast.makeText(this, "دکمه «انتخاب فایل» سایت پیدا نشد؛ «موس سیستمی» را در تنظیمات Android فعال کنید.", android.widget.Toast.LENGTH_SHORT).show();
        }
        scheduleSuggestions();
    }

    private void applySuggestion(String suggestion){
        InputConnection ic=getCurrentInputConnection(); if(ic==null) return;
        if(suggestion.equals("؟")||suggestion.equals("!")){ ic.commitText(suggestion+" ",1); predictor.observePunctuation(suggestion); scheduleSuggestions(); return; }
        CharSequence cs=ic.getTextBeforeCursor(80,0); String before=cs==null?"":cs.toString();
        String prefix=predictor.lastWord(before);
        if(!prefix.isEmpty() && !before.isEmpty() && !Character.isWhitespace(before.charAt(before.length()-1)) && predictor.normalize(suggestion).startsWith(predictor.normalize(prefix))){ ic.deleteSurroundingText(prefix.length(),0); ic.commitText(suggestion+" ",1); }
        else { ic.commitText((before.endsWith(" ")?"":" ")+suggestion+" ",1); }
        predictor.observeWord(suggestion); predictor.save(prefs); scheduleSuggestions();
    }
    private void commitSpaceAndLearn(){
        InputConnection ic=getCurrentInputConnection(); if(ic==null) return;
        CharSequence cs=ic.getTextBeforeCursor(160,0); String before=cs==null?"":cs.toString();
        predictor.learnFromContext(before); predictor.save(prefs); ic.commitText(" ",1); scheduleSuggestions();
    }

    private void rebuild(){setInputView(buildKeyboard());}
    private void commit(String s){InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.commitText(s,1);scheduleSuggestions();}}
    private void backspace(){InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.deleteSurroundingText(1,0);}
    private void sendKey(int code){InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}}
    private void ctrlKey(int code){InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.sendKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_DOWN,code,0,KeyEvent.META_CTRL_ON));ic.sendKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_UP,code,0,KeyEvent.META_CTRL_ON));}}
    private void copyAll(){InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.performContextMenuAction(android.R.id.selectAll);CharSequence selected=ic.getSelectedText(0);if(selected!=null&&!TextUtils.isEmpty(selected))addHistory(selected.toString());ic.performContextMenuAction(android.R.id.copy);}}
    private void paste(){InputConnection ic=getCurrentInputConnection();if(ic!=null){android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null&&cm.hasPrimaryClip()){android.content.ClipData d=cm.getPrimaryClip();if(d!=null&&d.getItemCount()>0){CharSequence x=d.getItemAt(0).coerceToText(this);if(x!=null&&!TextUtils.isEmpty(x))addHistory(x.toString());}}ic.performContextMenuAction(android.R.id.paste);}}
    private void cut(){InputConnection ic=getCurrentInputConnection();if(ic!=null){CharSequence selected=ic.getSelectedText(0);if(selected!=null&&!TextUtils.isEmpty(selected))addHistory(selected.toString());ic.performContextMenuAction(android.R.id.cut);}}
    private void toggleResize(){resizeLevel++;if(resizeLevel>3)resizeLevel=0;if(resizeLevel==0){int h=normalWindowHeight>0?normalWindowHeight:dp(360);getWindow().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,h);return;}int h;switch(resizeLevel){case 1:h=dp(280);break;case 2:h=dp(220);break;default:h=dp(160);break;}getWindow().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,h);}

    private void showMouse(View anchor){
        dismissPopup();
        MouseAccessibilityService.showCursorFromKeyboard();

        // The mouse panel intentionally follows the user's supplied reference image.
        // Do not substitute another mouse layout here.
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(makeBg(CREAM));
        panel.setPadding(0,0,0,0);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0,0,0,0);
        panel.addView(body, new FrameLayout.LayoutParams(-1,-1));

        // Top strip: transparency only. It is deliberately kept out of the main
        // control row so the two wheel buttons remain exactly where the reference shows them.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(5),0,dp(5),0);
        TextView transparencyLabel = new TextView(this);
        transparencyLabel.setText("شفافیت");
        transparencyLabel.setTextSize(12);
        transparencyLabel.setTextColor(NAVY);
        transparencyLabel.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(transparencyLabel, new LinearLayout.LayoutParams(dp(52),dp(30)));
        SeekBar transparency = new SeekBar(this);
        transparency.setMax(80);
        int savedTransparency = prefs.getInt("mousePopupTransparency",100);
        savedTransparency=Math.max(20,Math.min(100,savedTransparency));
        transparency.setProgress(savedTransparency-20);
        top.addView(transparency,new LinearLayout.LayoutParams(0,dp(30),1f));
        TextView transparencyValue = new TextView(this);
        transparencyValue.setText(savedTransparency+"%");
        transparencyValue.setTextSize(11);
        transparencyValue.setTextColor(NAVY);
        transparencyValue.setGravity(Gravity.CENTER);
        top.addView(transparencyValue,new LinearLayout.LayoutParams(dp(42),dp(30)));
        body.addView(top,new LinearLayout.LayoutParams(-1,dp(34)));

        // Main field + the two vertical controls at the right, exactly as in the reference.
        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setPadding(0,0,0,0);
        final TextView touchPad = new TextView(this);
        touchPad.setText("میدان لمسی\nحرکت نشانگر");
        touchPad.setTextSize(16);
        touchPad.setTextColor(Color.rgb(190,194,202));
        touchPad.setGravity(Gravity.CENTER);
        touchPad.setBackground(makeBg(Color.rgb(242,244,248)));
        middle.addView(touchPad,new LinearLayout.LayoutParams(0,dp(150),1f));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        Button close = key("Close",15,NAVY,Color.rgb(255,225,205));
        Button drag = key("Drag",16,NAVY,Color.rgb(255,246,185));
        side.addView(close,new LinearLayout.LayoutParams(dp(48),0,1f));
        side.addView(drag,new LinearLayout.LayoutParams(dp(48),0,1f));
        middle.addView(side,new LinearLayout.LayoutParams(dp(48),dp(150)));
        body.addView(middle,new LinearLayout.LayoutParams(-1,dp(150)));

        final float[] last={0,0};
        final boolean[] moving={false};
        touchPad.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                last[0]=e.getRawX(); last[1]=e.getRawY(); moving[0]=true; return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE && moving[0]){
                float dx=e.getRawX()-last[0], dy=e.getRawY()-last[1];
                if(Math.abs(dx)>=0.5f || Math.abs(dy)>=0.5f){
                    MouseAccessibilityService.moveRelativeFromKeyboard(dx*2.0f,dy*2.0f);
                    last[0]=e.getRawX(); last[1]=e.getRawY();
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){moving[0]=false;return true;}
            return true;
        });

        // Drag moves the mouse panel itself. It does not alter keyboard controls.
        final float[] dragLast={0,0};
        final boolean[] panelMoving={false};
        drag.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                dragLast[0]=e.getRawX(); dragLast[1]=e.getRawY(); panelMoving[0]=true; return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE && panelMoving[0]){
                float dx=e.getRawX()-dragLast[0],dy=e.getRawY()-dragLast[1];
                if(Math.abs(dx)>=1 || Math.abs(dy)>=1){
                    // PopupWindow supports position updates through update().
                    if(activePopup!=null){
                        mousePopupX += Math.round(dx);
                        mousePopupY += Math.round(dy);
                        int sw=getResources().getDisplayMetrics().widthPixels;
                        int sh=getResources().getDisplayMetrics().heightPixels;
                        int pw=panel.getWidth()>0?panel.getWidth():dp(430);
                        int ph=panel.getHeight()>0?panel.getHeight():dp(250);
                        mousePopupX=Math.max(0,Math.min(Math.max(0,sw-pw),mousePopupX));
                        mousePopupY=Math.max(0,Math.min(Math.max(0,sh-ph),mousePopupY));
                        activePopup.update(mousePopupX,mousePopupY,-1,-1);
                    }
                    dragLast[0]=e.getRawX(); dragLast[1]=e.getRawY();
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){panelMoving[0]=false;return true;}
            return true;
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0,0,0,0);

        Button leftClick=key("کلیک چپ",15,NAVY,Color.rgb(190,225,245));
        Button wheel1=key("↕",24,NAVY,Color.rgb(255,246,185));
        Button auto=key("حرکت\nخودکار",11,NAVY,Color.rgb(255,244,155));
        Button wheel2=key("↕",24,NAVY,Color.rgb(255,246,185));
        Button rightClick=key("کلیک راست",15,NAVY,Color.rgb(255,246,185));
        Button select=key("Select",14,NAVY,Color.rgb(255,246,185));

        controls.addView(leftClick,weight(2.25f));
        controls.addView(wheel1,weight(.62f));
        controls.addView(auto,weight(1.45f));
        controls.addView(wheel2,weight(.62f));
        controls.addView(rightClick,weight(2.15f));
        controls.addView(select,weight(1.25f));
        body.addView(controls,new LinearLayout.LayoutParams(-1,dp(58)));

        final boolean[] selectMode={false};
        final boolean[] leftHeld={false};
        leftClick.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                leftHeld[0]=true;
                MouseAccessibilityService.beginDragFromKeyboard();
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){
                MouseAccessibilityService.endDragFromKeyboard();
                if(e.getAction()==MotionEvent.ACTION_UP && !selectMode[0]) MouseAccessibilityService.clickFromKeyboard(false);
                leftHeld[0]=false;
                return true;
            }
            return true;
        });
        rightClick.setOnClickListener(v->MouseAccessibilityService.clickFromKeyboard(true));

        // The two narrow vertical glyphs are mouse-wheel controls, not magnifiers.
        wheel1.setOnClickListener(v->scrollMouse(-1));
        wheel2.setOnClickListener(v->scrollMouse(1));
        auto.setOnClickListener(v->{
            auto.setText(auto.getText().toString().contains("روشن") ? "حرکت\nخودکار" : "حرکت\nخودکار: روشن");
            MouseAccessibilityService.toggleAutoTargetFromKeyboard();
        });
        select.setOnClickListener(v->{
            selectMode[0]=!selectMode[0];
            select.setText(selectMode[0]?"Select ✓":"Select");
        });

        transparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
                int alpha=Math.max(20,Math.min(100,value+20));
                panel.setAlpha(alpha/100f);
                transparencyValue.setText(alpha+"%");
                if(fromUser) prefs.edit().putInt("mousePopupTransparency",alpha).apply();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        panel.setAlpha(savedTransparency/100f);

        close.setOnClickListener(v->{dismissPopup();MouseAccessibilityService.hideCursorFromKeyboard();});

        // One resize grip only: top-left, matching the supplied reference.
        TextView grip=new TextView(this);
        grip.setText("╱╱");
        grip.setTextSize(16);
        grip.setTextColor(Color.DKGRAY);
        grip.setGravity(Gravity.TOP|Gravity.LEFT);
        grip.setPadding(dp(2),0,0,0);
        panel.addView(grip,new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.TOP|Gravity.LEFT));

        final float[] resizeLast={0,0};
        final int[] base={0,0};
        final boolean[] resizing={false};
        grip.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                resizeLast[0]=e.getRawX(); resizeLast[1]=e.getRawY();
                int[] size=popupSize(panel); base[0]=size[0]; base[1]=size[1]; resizing[0]=true; return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE && resizing[0] && activePopup!=null){
                int nw=Math.max(dp(260),base[0]-Math.round(e.getRawX()-resizeLast[0]));
                int nh=Math.max(dp(220),base[1]-Math.round(e.getRawY()-resizeLast[1]));
                activePopup.update(nw,nh);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){resizing[0]=false;return true;}
            return true;
        });

        activePopup=new PopupWindow(panel,dp(430),dp(250),true);
        activePopup.setOutsideTouchable(true);
        stylePopup(activePopup);
        activePopup.setOnDismissListener(() -> MouseAccessibilityService.hideCursorFromKeyboard());
        int[] anchorLoc=popupLocation(anchor);
        int ph=dp(250);
        int screenH=getResources().getDisplayMetrics().heightPixels;
        mousePopupX=Math.max(0,anchorLoc[0]);
        mousePopupY=anchorLoc[1]-ph;
        if(mousePopupY<dp(4)) mousePopupY=dp(4);
        if(mousePopupY+ph>screenH-dp(4)) mousePopupY=Math.max(dp(4),screenH-ph-dp(4));
        activePopup.showAtLocation(currentRoot!=null?currentRoot:getWindow().getWindow().getDecorView(),Gravity.TOP|Gravity.LEFT,mousePopupX,mousePopupY);
    }

    private int[] popupSize(View v){
        int w=v.getWidth()>0?v.getWidth():dp(430);
        int h=v.getHeight()>0?v.getHeight():dp(250);
        return new int[]{w,h};
    }

    private void scrollMouse(int direction){
        MouseAccessibilityService.scrollFromKeyboard(direction);
    }

    private void addSystemMouseRepeat(Button b,float dy,float dx){
        final Runnable[] r={null};
        r[0]=()->{MouseAccessibilityService.moveRelativeFromKeyboard(dx*18f,dy*18f);handler.postDelayed(r[0],90);};
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                MouseAccessibilityService.moveRelativeFromKeyboard(dx*18f,dy*18f);
                handler.postDelayed(r[0],350);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){
                handler.removeCallbacks(r[0]);
                return true;
            }
            return true;
        });
    }

    private void showEmoji(View anchor){int[] loc=popupLocation(anchor);dismissPopup();ScrollView sv=scrollBox();LinearLayout box=gridContainer(sv);addGrid(box,EMOJIS,40);addGrid(box,FLAGS,40);activePopup=new PopupWindow(sv,dp(330),dp(420),true);stylePopup(activePopup);showPopupAt(activePopup,loc[0],loc[1],420);}
    private void showTools(View anchor){
        dismissPopup();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(6,6,6,6);box.setBackgroundColor(CREAM);
        TextView resizeTitle=new TextView(this);resizeTitle.setText("اندازه کیبورد");resizeTitle.setTextSize(15);resizeTitle.setTextColor(NAVY);resizeTitle.setGravity(Gravity.CENTER);box.addView(resizeTitle,new LinearLayout.LayoutParams(-1,dp(32)));
        SeekBar resizeRoller=new SeekBar(this);resizeRoller.setMax(260);int currentH=normalWindowHeight>0?normalWindowHeight:dp(360);int minH=dp(160);int maxH=dp(420);int progress=Math.max(0,Math.min(260,(int)(((float)(currentH-minH)/(maxH-minH))*260)));resizeRoller.setProgress(progress);box.addView(resizeRoller,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView resizeValue=new TextView(this);resizeValue.setText("ارتفاع: "+(currentH/dp(1)));resizeValue.setTextSize(13);resizeValue.setGravity(Gravity.CENTER);box.addView(resizeValue,new LinearLayout.LayoutParams(-1,dp(26)));
        resizeRoller.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean fromUser){int h=minH+(int)(((maxH-minH)*value)/260f);resizeValue.setText("ارتفاع: "+(h/dp(1)));if(fromUser){resizeLevel=0;normalWindowHeight=h;getWindow().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,h);}}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){int h=minH+(int)(((maxH-minH)*bar.getProgress())/260f);normalWindowHeight=h;prefs.edit().putInt("normalWindowHeight",h).apply();}});
        String[][] tools={{"⚙","اعراب و علائم عربی"},{"▦","ماشین حساب"},{"🎨","رنگ نمای کیبورد"},{"☺","اموجی و پرچم‌ها"},{"#","بیش از 100 علامت"},{"▤","100 History"}};
        for(String[] t:tools){
            Button b=keyWithIcon(t[1],t[0],14,NAVY,CREAM);box.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));
            if(t[1].startsWith("اعراب")) b.setOnClickListener(v->showArabicMarks(v));
            else if(t[1].equals("ماشین حساب")) b.setOnClickListener(this::showCalculator);
            else if(t[1].startsWith("رنگ")) b.setOnClickListener(this::showColorTablet);
            else if(t[1].startsWith("اموجی")) b.setOnClickListener(this::showEmoji);
            else if(t[1].startsWith("بیش")) b.setOnClickListener(v->showRepeatGridPopup(v,SYMBOLS,42,300));
            else b.setOnClickListener(this::showHistory);
        }
        ScrollView toolScroll=new ScrollView(this);toolScroll.setFillViewport(true);toolScroll.setVerticalScrollBarEnabled(true);toolScroll.setClipToPadding(true);toolScroll.addView(box,new ViewGroup.LayoutParams(-1,-2));
        activePopup=new PopupWindow(toolScroll,dp(320),dp(300),true);stylePopup(activePopup);showPopupAbove(anchor,activePopup,dp(300));
    }
    private ScrollView scrollBox(){ScrollView sv=new ScrollView(this);sv.setFillViewport(true);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(6,6,6,6);box.setBackgroundColor(CREAM);sv.addView(box,new ViewGroup.LayoutParams(-1,-1));return sv;}
    private LinearLayout gridContainer(ScrollView sv){return (LinearLayout)sv.getChildAt(0);}
    private String decodeSymbol(String value){
        if(value==null)return "";
        String s=value.trim();
        try{
            if(s.matches("(?i)U\\+[0-9A-F]{4,6}")) return new String(Character.toChars(Integer.parseInt(s.substring(2),16)));
            if(s.matches("(?i)0x[0-9A-F]{4,6}")) return new String(Character.toChars(Integer.parseInt(s.substring(2),16)));
            if(s.length()==6 && s.charAt(0)==92 && (s.charAt(1)=='u' || s.charAt(1)=='U') && s.substring(2).matches("[0-9A-Fa-f]{4}")) return String.valueOf((char)Integer.parseInt(s.substring(2),16));
            if(s.matches("(?i)&#x[0-9A-F]{2,6};")) return new String(Character.toChars(Integer.parseInt(s.substring(3,s.length()-1),16)));
            if(s.matches("&#[0-9]{2,7};")) return new String(Character.toChars(Integer.parseInt(s.substring(2,s.length()-1))));
        }catch(Exception ignored){}
        return value;
    }
    private void addGrid(LinearLayout box,String[] items,int cell){LinearLayout r=null;int count=0;for(String item:items){if(count%7==0){r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);box.addView(r,new LinearLayout.LayoutParams(-1,dp(cell)));}final String shown=decodeSymbol(item);Button b=key(shown,20,NAVY,CREAM);r.addView(b,weight(1));b.setOnClickListener(v->commit(decodeSymbol(((Button)v).getText().toString())));count++;}}
    private void showArabicMarks(View anchor){int[] loc=popupLocation(anchor);dismissPopup();ScrollView sv=scrollBox();LinearLayout box=gridContainer(sv);addGridCustom(box,ARABIC_MARKS,78,4,34);activePopup=new PopupWindow(sv,dp(330),dp(500),true);stylePopup(activePopup);showPopupAt(activePopup,loc[0],loc[1],500);}
    private void addGridCustom(LinearLayout box,String[] items,int cell,int columns,float textSize){LinearLayout r=null;int count=0;for(String item:items){if(count%columns==0){r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);box.addView(r,new LinearLayout.LayoutParams(-1,dp(cell)));}final String shown=decodeSymbol(item);Button b=key(shown,textSize,NAVY,CREAM);r.addView(b,weight(1));b.setOnClickListener(v->commit(decodeSymbol(((Button)v).getText().toString())));count++;}}
    private void showGridPopup(View anchor,String[] items,int cell,int height){int[] loc=popupLocation(anchor);dismissPopup();ScrollView sv=scrollBox();addGrid(gridContainer(sv),items,cell);activePopup=new PopupWindow(sv,dp(330),dp(height),true);stylePopup(activePopup);showPopupAt(activePopup,loc[0],loc[1],height);}
    private void showRepeatGridPopup(View anchor,String[] items,int cell,int height){int[] loc=popupLocation(anchor);dismissPopup();ScrollView sv=scrollBox();addRepeatGrid(gridContainer(sv),items,cell);activePopup=new PopupWindow(sv,dp(330),dp(height),true);stylePopup(activePopup);showPopupAt(activePopup,loc[0],loc[1],height);}
    private void addRepeatGrid(LinearLayout box,String[] items,int cell){LinearLayout r=null;int count=0;for(String item:items){if(count%7==0){r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);box.addView(r,new LinearLayout.LayoutParams(-1,dp(cell)));}final String shown=decodeSymbol(item);Button b=key(shown,20,NAVY,CREAM);r.addView(b,weight(1));addDualKeyBehavior(b,shown,shown);count++;}}
    private void stylePopup(PopupWindow pw){pw.setBackgroundDrawable(new ColorDrawable(CREAM));pw.setOutsideTouchable(true);pw.setFocusable(true);pw.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);pw.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);pw.setElevation(dp(8));pw.setTouchInterceptor((v,e)->false);}
    private int[] popupLocation(View anchor){int[] loc=new int[2];anchor.getLocationOnScreen(loc);return loc;}
    private void showPopupAbove(View anchor,PopupWindow pw,int height){int[] loc=popupLocation(anchor);showPopupAt(pw,loc[0],loc[1],height);}
    private void showPopupAt(PopupWindow pw,int x,int anchorY,int height){int h=dp(height);int screenH=getResources().getDisplayMetrics().heightPixels;int y=anchorY-h;if(y<dp(4))y=dp(4);if(y+h>screenH-dp(4))y=Math.max(dp(4),screenH-h-dp(4));if(currentRoot!=null)pw.showAtLocation(currentRoot,Gravity.TOP|Gravity.LEFT,Math.max(0,x),y);else pw.showAsDropDown(currentRoot,0,-h);}
    private void showPopupAtKeyboardTop(PopupWindow pw,int height){int h=dp(height);int[] rootLoc=new int[2];if(currentRoot!=null){currentRoot.getLocationOnScreen(rootLoc);int y=rootLoc[1]-h-dp(4);if(y<dp(4))y=dp(4);int screenW=getResources().getDisplayMetrics().widthPixels;int w=dp(320);int x=Math.max(0,(screenW-w)/2);pw.showAtLocation(currentRoot,Gravity.TOP|Gravity.LEFT,x,y);}else{pw.showAtLocation(getWindow().getWindow().getDecorView(),Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,dp(4));}}
    private void dismissPopup(){if(activePopup!=null&&activePopup.isShowing())activePopup.dismiss();activePopup=null;}
    private void showCalculator(View anchor){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(8,8,8,8);box.setBackgroundColor(CREAM);TextView display=new TextView(this);display.setText("0");display.setTextSize(24);display.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);box.addView(display,new LinearLayout.LayoutParams(-1,dp(55)));String[] ks={"7","8","9","÷","4","5","6","×","1","2","3","−","0",".","=","+","C"};for(int i=0;i<ks.length;i+=4){LinearLayout r=new LinearLayout(this);for(int j=i;j<Math.min(i+4,ks.length);j++){String k=ks[j];Button b=key(k,18,NAVY,CREAM);r.addView(b,weight(1));b.setOnClickListener(v->{String old=display.getText().toString();String x=((Button)v).getText().toString();if(x.equals("C"))display.setText("0");else if(x.equals("="))display.setText(calculate(old));else display.setText(old.equals("0")?x:old+x);});}box.addView(r,new LinearLayout.LayoutParams(-1,dp(48)));}int[] loc=popupLocation(anchor);dismissPopup();activePopup=new PopupWindow(box,dp(280),dp(360),true);stylePopup(activePopup);showPopupAt(activePopup,loc[0],loc[1],360);}
    private String calculate(String s){try{String e=s.replace("×","*").replace("÷","/").replace("−","-");double v=new SimpleExpression(e).parse();return v==(long)v?Long.toString((long)v):Double.toString(v);}catch(Exception e){return "Error";}}
    private void showColorTablet(View anchor){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(8,8,8,8);box.setBackgroundColor(CREAM);TextView title=new TextView(this);title.setText("انتخاب رنگ نمای کیبورد");title.setGravity(Gravity.CENTER);title.setTextSize(16);box.addView(title,new LinearLayout.LayoutParams(-1,dp(42)));int[] colors={0xFFFFFBF0,0xFFFFFFFF,0xFFFFF2CC,0xFFFFE4C4,0xFFFFD6D6,0xFFFFE0F0,0xFFE8D9FF,0xFFD9E8FF,0xFFD8F0FF,0xFFD8F5E5,0xFFE7F5D8,0xFFF5F5DC,0xFFE0E0E0,0xFFC8C8C8,0xFFB0BEC5,0xFF263238,0xFF102A43,0xFF1B4965,0xFF5C3D2E,0xFF6D597A,0xFF8D6E63,0xFF455A64,0xFF2E7D32,0xFF1565C0,0xFF6A1B9A,0xFFC62828,0xFFEF6C00,0xFFFFC107,0xFF00838F,0xFF00695C,0xFFAD1457,0xFF4E342E,0xFF37474F,0xFF1A237E,0xFF311B92,0xFF004D40,0xFF33691E,0xFF827717,0xFF3E2723,0xFF000000};for(int i=0;i<colors.length;i+=5){LinearLayout r=new LinearLayout(this);for(int j=i;j<Math.min(i+5,colors.length);j++){final int c=colors[j];Button sw=key("",1,NAVY,c);r.addView(sw,weight(1));sw.setOnClickListener(v->{keyboardColor=c;prefs.edit().putInt("keyboardColor",c).apply();if(currentRoot!=null)currentRoot.setBackgroundColor(c);});}box.addView(r,new LinearLayout.LayoutParams(-1,dp(48)));}dismissPopup();activePopup=new PopupWindow(box,dp(320),dp(395),true);stylePopup(activePopup);showPopupAtKeyboardTop(activePopup,395);}
    private void showHistory(View anchor){capturePrimaryClip();int[] loc=popupLocation(anchor);dismissPopup();ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setVerticalScrollBarEnabled(true);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(6,6,6,6);box.setBackgroundColor(CREAM);sv.addView(box,new ViewGroup.LayoutParams(-1,-1));if(clipboardHistory.isEmpty()){TextView empty=new TextView(this);empty.setText("تاریخچه کلیپ‌بورد خالی است");empty.setTextSize(16);empty.setGravity(Gravity.CENTER);box.addView(empty,new LinearLayout.LayoutParams(-1,dp(70)));}else{List<String> items=new ArrayList<>(clipboardHistory);Collections.reverse(items);if(items.size()>100)items=items.subList(0,100);for(String item:items){final String value=item;Button b=key(value,15,NAVY,CREAM);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);b.setPadding(dp(10),0,dp(10),0);box.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));b.setOnClickListener(v->{commit(value);dismissPopup();});}}activePopup=new PopupWindow(sv,dp(340),dp(430),true);stylePopup(activePopup);showPopupAt(activePopup,loc[0],loc[1],430);}
    private void addHistory(String s){if(TextUtils.isEmpty(s))return;history.add(s);while(history.size()>100)history.remove(0);prefs.edit().putString("history",TextUtils.join("\u0001",history)).apply();}
    private void loadHistory(){String all=prefs.getString("history","");if(!TextUtils.isEmpty(all))history.addAll(Arrays.asList(all.split("\u0001",-1)));while(history.size()>100)history.remove(0);}
    private void capturePrimaryClip(){if(clipboardManager==null||!clipboardManager.hasPrimaryClip())return;try{ClipData d=clipboardManager.getPrimaryClip();if(d==null||d.getItemCount()==0)return;CharSequence cs=d.getItemAt(0).coerceToText(this);if(cs==null)return;String text=cs.toString();if(TextUtils.isEmpty(text))return;if(!clipboardHistory.isEmpty()&&text.equals(clipboardHistory.get(clipboardHistory.size()-1)))return;clipboardHistory.add(text);while(clipboardHistory.size()>100)clipboardHistory.remove(0);prefs.edit().putString("clipboard_history",TextUtils.join("\u0001",clipboardHistory)).apply();}catch(Exception ignored){}}
    private void loadClipboardHistory(){String all=prefs.getString("clipboard_history","");if(!TextUtils.isEmpty(all))clipboardHistory.addAll(Arrays.asList(all.split("\u0001",-1)));while(clipboardHistory.size()>100)clipboardHistory.remove(0);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static class Predictor {
        private final Map<String,Map<String,Integer>> next=new HashMap<>();
        private final Map<String,Integer> common=new LinkedHashMap<>();
        private volatile List<Map.Entry<String,Integer>> commonSorted=Collections.emptyList();
        private volatile String lastSuggestionKey="";
        private volatile List<String> lastSuggestionValue=Collections.emptyList();
        Predictor(){
        }
        void seed(String a,String b,int n){next.computeIfAbsent(a,k->new HashMap<>()).put(b,n);}
        synchronized void seedCommon(String w,int n){if(w==null)return;w=w.trim();if(w.isEmpty())return;common.put(w,Math.max(n,common.getOrDefault(w,0)));commonSorted=Collections.emptyList();lastSuggestionKey="";}
        synchronized void observeWord(String w){ common.put(w,common.getOrDefault(w,0)+1); commonSorted=Collections.emptyList(); lastSuggestionKey=""; }
        void observePunctuation(String p){}
        void learnFromContext(String text){
            String clean=text.replaceAll("[،,؛;:!?؟\\\"()\\[\\]{}]"," ").trim(); if(clean.isEmpty()) return;
            String[] ws=clean.split("\\s+"); if(ws.length>=2){String a=ws[ws.length-2], b=ws[ws.length-1]; seed(a,b,next.getOrDefault(a,new HashMap<>()).getOrDefault(b,0)+1);} if(ws.length>=1) observeWord(ws[ws.length-1]);
        }
        synchronized List<String> suggest(String before,int max){
            String cacheKey=(before==null?"":before);
            if(cacheKey.equals(lastSuggestionKey) && lastSuggestionValue.size()<=max) return new ArrayList<>(lastSuggestionValue);
            ArrayList<String> out=new ArrayList<>();
            String raw=before==null?"":before;
            String normalized=normalize(raw);
            String last=lastWord(normalized);
            boolean partial=!normalized.isEmpty() && !Character.isWhitespace(normalized.charAt(normalized.length()-1)) && !last.isEmpty();
            if(partial){
                final String prefix=last;
                ArrayList<Map.Entry<String,Integer>> c=new ArrayList<>();
                for(Map.Entry<String,Integer> e:getCommonSorted()){c.add(e);}

                c.removeIf(e->{String w=normalize(e.getKey()); return w.length()<=prefix.length() || !w.startsWith(prefix);});
                c.sort((x,y)->{
                    String wx=normalize(x.getKey()), wy=normalize(y.getKey());
                    int sx=prefixScore(wx,prefix,x.getValue()), sy=prefixScore(wy,prefix,y.getValue());
                    int n=Integer.compare(sy,sx);
                    return n!=0?n:Integer.compare(wx.length(),wy.length());
                });
                for(Map.Entry<String,Integer> e:c){String w=e.getKey();if(!out.contains(w))out.add(w);if(out.size()>=max)break;}
                List<String> result=new ArrayList<>(out.subList(0,Math.min(max,out.size())));
                lastSuggestionKey=cacheKey; lastSuggestionValue=result;
                return new ArrayList<>(result);
            }
            Map<String,Integer> m=next.get(last);
            if(m!=null) addSorted(out,m);
            if(looksQuestion(normalized) && out.size()<max) out.add("؟");
            else if(looksExclamation(normalized) && out.size()<max) out.add("!");
            // After a completed word, prefer context learned from that word; only then use general words.
            if(out.size()<max){
                ArrayList<Map.Entry<String,Integer>> c=new ArrayList<>(getCommonSorted());
                for(Map.Entry<String,Integer> e:c){if(!out.contains(e.getKey())){out.add(e.getKey());if(out.size()>=max)break;}}
            }
            List<String> result=new ArrayList<>(out.subList(0,Math.min(max,out.size())));
            lastSuggestionKey=cacheKey; lastSuggestionValue=result;
            return new ArrayList<>(result);
        }
        private List<Map.Entry<String,Integer>> getCommonSorted(){
            if(!commonSorted.isEmpty() || common.isEmpty()) return commonSorted;
            ArrayList<Map.Entry<String,Integer>> c=new ArrayList<>(common.entrySet());
            c.sort((x,y)->Integer.compare(y.getValue(),x.getValue()));
            commonSorted=c;
            return commonSorted;
        }
        private int prefixScore(String word,String prefix,int freq){
            int score=freq*4;
            score += Math.max(0,120-(word.length()-prefix.length())*12);
            if(word.equals(prefix)) score-=10000;
            return score;
        }
        private String normalize(String s){
            if(s==null)return "";
            return s.replace("\u200c","").replace("\u200d","").replace("\u0640","").trim();
        }
        private void addSorted(List<String> out,Map<String,Integer> m){ArrayList<Map.Entry<String,Integer>> a=new ArrayList<>(m.entrySet());a.sort((x,y)->Integer.compare(y.getValue(),x.getValue()));for(Map.Entry<String,Integer> e:a)if(!out.contains(e.getKey()))out.add(e.getKey());}
        String lastWord(String s){String x=normalize(s);if(x.isEmpty())return "";int end=x.length();int i=end-1;while(i>=0&&!isWordChar(x.charAt(i)))i--;end=i+1;while(i>=0&&isWordChar(x.charAt(i)))i--;return x.substring(i+1,end);}
        private boolean isWordChar(char c){return Character.isLetter(c)||(c>=0x0600&&c<=0x06FF);}
        private boolean looksQuestion(String s){String x=s.trim(); return x.matches(".*(آیا|چرا|چطور|چگونه|کجا|کی|چه|مگر|میشود|می‌شود|هستید|هستی)\\s*$");}
        private boolean looksExclamation(String s){String x=s.trim(); return x.matches(".*(عالی|وای|عجب|چه خوب|تبریک|آفرین|خوشحال)\\s*$");}
        void save(SharedPreferences p){StringBuilder sb=new StringBuilder();for(Map.Entry<String,Map<String,Integer>> e:next.entrySet())for(Map.Entry<String,Integer> q:e.getValue().entrySet())sb.append(e.getKey()).append('~').append(q.getKey()).append('~').append(q.getValue()).append('\n');p.edit().putString("predictor",sb.toString()).apply();}
        void load(SharedPreferences p){String raw=p.getString("predictor","");if(raw.isEmpty())return;for(String line:raw.split("\\n")){String[] z=line.split("~",-1);if(z.length==3)try{seed(z[0],z[1],Integer.parseInt(z[2]));}catch(Exception ignored){}}}
    }

    private static class SimpleExpression{private final String s;private int p=0;SimpleExpression(String s){this.s=s.replace(" ","");}double parse(){double v=expr();if(p<s.length())throw new RuntimeException();return v;}double expr(){double v=term();while(p<s.length()){char c=s.charAt(p);if(c=='+'){p++;v+=term();}else if(c=='-'){p++;v-=term();}else break;}return v;}double term(){double v=factor();while(p<s.length()){char c=s.charAt(p);if(c=='*'){p++;v*=factor();}else if(c=='/'){p++;v/=factor();}else break;}return v;}double factor(){if(p<s.length()&&s.charAt(p)=='-'){p++;return -factor();}int st=p;while(p<s.length()&&(Character.isDigit(s.charAt(p))||s.charAt(p)=='.'))p++;if(st==p)throw new RuntimeException();return Double.parseDouble(s.substring(st,p));}}
}
