package com.gradewidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/** Setup (passphrase, links), a live preview, and every look option for the widget. */
public class MainActivity extends Activity {
    private EditText url, pass, claude;
    private TextView status;
    private ImageView preview;
    private Style style;
    private float d;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private static final String[] BG_SWATCHES = {"#1F2430", "#000000", "#FFFFFF", "#0D47A1", "#4A148C", "#1B5E20", "#B71C1C", "#F5E6CC", "#37474F", "#FF6F00"};
    private static final String[] FG_SWATCHES = {"#F2F4F8", "#000000", "#FFFFFF", "#FFE082", "#B3E5FC", "#263238"};
    private static final String[] RING_SWATCHES = {"#4F8EF7", "#4CAF50", "#FFB300", "#EF5350", "#AB47BC", "#26C6DA", "#FF7043", "#FFFFFF"};
    private static final String[] TRACK_SWATCHES = {"#3A4152", "#00000000", "#555555", "#CCCCCC", "#1A1A1A", "#FFFFFF"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        style = Style.load(this);
        int p = dp(20);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(p, p * 2, p, p * 2);

        TextView title = new TextView(this);
        title.setText("Grades widget");
        title.setTextSize(24);
        col.addView(title);

        TextView help = new TextView(this);
        help.setText("Tap the widget to load the latest grades. Tap its Update button to ask Claude for a fresh pull from Blackboard: it copies \"update my grades\" and opens Claude, so just paste and send.");
        help.setPadding(0, p / 2, 0, p);
        col.addView(help);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        col.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        status = new TextView(this);
        status.setPadding(0, p / 2, 0, p / 2);
        col.addView(status);

        // ---- Setup ----
        col.addView(heading("Setup"));
        col.addView(label("Passphrase"));
        pass = new EditText(this);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pass.setText(Store.passphrase(this));
        col.addView(pass);

        Button save = new Button(this);
        save.setText("Save & refresh");
        save.setOnClickListener(v -> saveAndRefresh());
        col.addView(save);

        Button add = new Button(this);
        add.setText("Add widget to home screen");
        add.setOnClickListener(v -> pinWidget());
        col.addView(add);

        // ---- Look ----
        col.addView(heading("Look"));

        col.addView(label("Background color"));
        col.addView(colorRow(BG_SWATCHES, () -> style.bg, c -> style.bg = c));
        col.addView(slider("Background opacity", 0, 100, () -> style.bgAlpha, v -> style.bgAlpha = v, "%d%%"));
        col.addView(slider("Corner roundness", 0, 40, () -> style.radiusDp, v -> style.radiusDp = v, "%d dp"));

        col.addView(label("Text color"));
        col.addView(colorRow(FG_SWATCHES, () -> style.fg, c -> style.fg = c));

        col.addView(toggle("Color the ring by GPA (green 3.0+, amber 2.0+, red below)", style.ringByGpa, on -> style.ringByGpa = on));
        col.addView(label("Ring color (when not colored by GPA)"));
        col.addView(colorRow(RING_SWATCHES, () -> style.ringColor, c -> style.ringColor = c));
        col.addView(label("Ring background"));
        col.addView(colorRow(TRACK_SWATCHES, () -> style.trackColor, c -> style.trackColor = c));

        col.addView(label("Font"));
        col.addView(fontPicker());

        col.addView(toggle("Show course list", style.showList, on -> style.showList = on));
        col.addView(toggle("Show letter-grade badges", style.showBadges, on -> style.showBadges = on));
        col.addView(toggle("Show \"Grades from\" date", style.showFooter, on -> style.showFooter = on));

        Button reset = new Button(this);
        reset.setText("Reset look to default");
        reset.setOnClickListener(v -> { Style.reset(this); recreate(); GradeWidget.redraw(this); });
        col.addView(reset);

        // ---- Advanced ----
        col.addView(heading("Advanced"));
        col.addView(label("Data link"));
        url = new EditText(this);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setTextSize(12);
        url.setText(Store.url(this));
        col.addView(url);
        col.addView(label("Claude chat link (Update button)"));
        claude = new EditText(this);
        claude.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        claude.setTextSize(12);
        claude.setText(Store.claudeUrl(this));
        col.addView(claude);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);

        showPreview(Store.cached(this) == null ? "Not set up yet" : null);
    }

    private int dp(float v) { return Math.round(v * d); }

    private TextView heading(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setPadding(0, dp(24), 0, dp(4));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    /** Called after every look change: save, redraw the preview and the widgets. */
    private void lookChanged() {
        style.save(this);
        showPreview(null);
        GradeWidget.redraw(this);
    }

    private View colorRow(String[] swatches, IntSupplier get, Consumer<Integer> set) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText hex = new EditText(this);

        for (String sw : swatches) {
            int color = Color.parseColor(sw);
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color);
            g.setStroke(dp(1), Color.GRAY);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMargins(0, 0, dp(10), 0);
            v.setOnClickListener(x -> { set.accept(color); hex.setText(toHex(color)); lookChanged(); });
            row.addView(v, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(row);
        wrap.addView(hs);

        hex.setHint("#RRGGBB");
        hex.setTextSize(13);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setText(toHex(get.getAsInt()));
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                String s = e.toString().trim();
                if (!s.startsWith("#")) s = "#" + s;
                if (s.length() != 7 && s.length() != 9) return;
                try {
                    int c = Color.parseColor(s);
                    if (c != get.getAsInt()) { set.accept(c); lookChanged(); }
                } catch (IllegalArgumentException ignored) { }
            }
        });
        wrap.addView(hex, new LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private static String toHex(int c) {
        if (Color.alpha(c) == 255) return String.format(Locale.US, "#%06X", c & 0xFFFFFF);
        return String.format(Locale.US, "#%08X", c);
    }

    private View slider(String name, int min, int max, IntSupplier get, Consumer<Integer> set, String fmt) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        TextView l = label(name + ": " + String.format(Locale.US, fmt, get.getAsInt()));
        wrap.addView(l);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(get.getAsInt() - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                set.accept(v + min);
                l.setText(name + ": " + String.format(Locale.US, fmt, v + min));
                style.save(MainActivity.this);
                showPreview(null);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { GradeWidget.redraw(MainActivity.this); }
        });
        wrap.addView(sb);
        return wrap;
    }

    private View toggle(String name, boolean on, Consumer<Boolean> set) {
        Switch sw = new Switch(this);
        sw.setText(name);
        sw.setChecked(on);
        sw.setPadding(0, dp(10), 0, dp(10));
        sw.setOnCheckedChangeListener((CompoundButton b, boolean v) -> { set.accept(v); lookChanged(); });
        return sw;
    }

    private View fontPicker() {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        String[][] opts = {{"sans", "Clean"}, {"serif", "Serif"}, {"mono", "Mono"}, {"rounded", "Narrow"}};
        for (String[] o : opts) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(o[1]);
            rb.setChecked(o[0].equals(style.font));
            rb.setOnClickListener(v -> { style.font = o[0]; lookChanged(); });
            g.addView(rb);
        }
        return g;
    }

    private void showPreview(String msg) {
        int w = getResources().getDisplayMetrics().widthPixels - dp(40);
        Bitmap bmp = Renderer.draw(Store.cached(this), null, w, dp(150), d, style);
        preview.setImageBitmap(bmp);
        if (msg != null) status.setText(msg);
    }

    private void saveAndRefresh() {
        Store.saveSettings(this, url.getText().toString(), pass.getText().toString());
        Store.saveClaudeUrl(this, claude.getText().toString());
        status.setText("Checking…");
        new Thread(() -> {
            String msg;
            try {
                Store.refresh(this);
                msg = "Saved. Your widget will show these grades.";
            } catch (Exception e) {
                msg = e.getMessage() == null ? "Something went wrong" : e.getMessage();
            }
            final String fm = msg;
            ui.post(() -> {
                showPreview(fm);
                GradeWidget.requestRefresh(this);
            });
        }).start();
    }

    private void pinWidget() {
        AppWidgetManager mgr = getSystemService(AppWidgetManager.class);
        if (mgr != null && mgr.isRequestPinAppWidgetSupported()) {
            mgr.requestPinAppWidget(new ComponentName(this, GradeWidget.class), null, null);
        } else {
            status.setText("Long-press your home screen → Widgets → Grades.");
        }
    }
}
