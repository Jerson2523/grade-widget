package com.gradewidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/** One screen: passphrase + data link, a live preview of the widget, and a button to add it. */
public class MainActivity extends Activity {
    private EditText url, pass;
    private TextView status;
    private ImageView preview;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float d = getResources().getDisplayMetrics().density;
        int p = Math.round(20 * d);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(p, p * 2, p, p);

        TextView title = new TextView(this);
        title.setText("Grades widget");
        title.setTextSize(24);
        col.addView(title);

        TextView help = new TextView(this);
        help.setText("Enter the passphrase Claude gave you, tap Save, then add the widget to your home screen. Tap the widget any time to refresh it.");
        help.setPadding(0, p / 2, 0, p);
        col.addView(help);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        col.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(150 * d)));

        status = new TextView(this);
        status.setPadding(0, p / 2, 0, p / 2);
        col.addView(status);

        col.addView(label("Passphrase"));
        pass = new EditText(this);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pass.setText(Store.passphrase(this));
        col.addView(pass);

        col.addView(label("Data link"));
        url = new EditText(this);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setTextSize(12);
        url.setText(Store.url(this));
        col.addView(url);

        Button save = new Button(this);
        save.setText("Save & refresh");
        save.setOnClickListener(v -> saveAndRefresh());
        col.addView(save);

        Button add = new Button(this);
        add.setText("Add widget to home screen");
        add.setOnClickListener(v -> pinWidget());
        col.addView(add);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);

        showPreview(Store.cached(this), Store.cached(this) == null ? "Not set up yet" : null);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, Math.round(12 * getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private void showPreview(JSONObject data, String msg) {
        float d = getResources().getDisplayMetrics().density;
        int w = getResources().getDisplayMetrics().widthPixels - Math.round(40 * d);
        Bitmap bmp = Renderer.draw(data, null, w, Math.round(150 * d), d);
        preview.setImageBitmap(bmp);
        status.setText(msg == null ? "" : msg);
    }

    private void saveAndRefresh() {
        Store.saveSettings(this, url.getText().toString(), pass.getText().toString());
        status.setText("Checking…");
        new Thread(() -> {
            JSONObject data = null;
            String msg;
            try {
                data = Store.refresh(this);
                msg = "Saved. Your widget will show these grades.";
            } catch (Exception e) {
                msg = e.getMessage() == null ? "Something went wrong" : e.getMessage();
            }
            final JSONObject fd = data;
            final String fm = msg;
            ui.post(() -> {
                showPreview(fd != null ? fd : Store.cached(this), fm);
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
