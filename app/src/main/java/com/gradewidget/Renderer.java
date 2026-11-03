package com.gradewidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Draws the whole widget into one bitmap: GPA ring on the left, course list on the right. */
final class Renderer {
    static final int BG = Color.parseColor("#E61F2430");
    static final int TRACK = Color.parseColor("#3A4152");
    static final int FG = Color.parseColor("#F2F4F8");
    static final int MUTED = Color.parseColor("#9AA3B5");
    static final int GREEN = Color.parseColor("#4CAF50");
    static final int AMBER = Color.parseColor("#FFB300");
    static final int RED = Color.parseColor("#EF5350");

    static int gpaColor(double gpa) {
        return gpa >= 3.0 ? GREEN : gpa >= 2.0 ? AMBER : RED;
    }

    static int letterColor(String letter) {
        if (letter.startsWith("A") || letter.startsWith("B")) return GREEN;
        if (letter.startsWith("C")) return AMBER;
        return RED;
    }

    /** GPA to three significant figures (2.22, 0.850, 4.00). */
    static String threeSf(double v) {
        if (v <= 0) return "0.00";
        if (v >= 10) return String.format(Locale.US, "%.1f", v);
        if (v >= 1) return String.format(Locale.US, "%.2f", v);
        return String.format(Locale.US, "%.3f", v);
    }

    static Bitmap draw(JSONObject data, String status, int w, int h, float density) {
        w = Math.max(w, 100);
        h = Math.max(h, 100);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(BG);
        float r = 22 * density;
        c.drawRoundRect(new RectF(0, 0, w, h), r, r, bg);

        float pad = 12 * density;
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        if (data == null) {
            t.setColor(FG);
            t.setTextSize(15 * density);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText("Grades", w / 2f, h / 2f - 6 * density, t);
            t.setColor(MUTED);
            t.setTextSize(12 * density);
            String msg = status == null ? "Tap to refresh" : status;
            c.drawText(TextUtils.ellipsize(msg, t, w - 2 * pad, TextUtils.TruncateAt.END).toString(),
                    w / 2f, h / 2f + 14 * density, t);
            return bmp;
        }

        double gpa = data.optDouble("gpa", 0);
        JSONArray courses = data.optJSONArray("courses");
        int n = courses == null ? 0 : courses.length();
        boolean showList = w > h * 1.35f && n > 0;

        // ---- GPA ring ----
        float ringBox = showList ? Math.min(h - 2 * pad, w * 0.42f) : Math.min(w, h) - 2 * pad;
        float stroke = ringBox * 0.11f;
        float cx = showList ? pad + ringBox / 2f : w / 2f;
        float cy = h / 2f;
        float rad = ringBox / 2f - stroke / 2f;
        RectF oval = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(TRACK);
        c.drawOval(oval, ring);

        float frac = (float) Math.max(0, Math.min(1, gpa / 4.0));
        ring.setColor(gpaColor(gpa));
        if (frac > 0) c.drawArc(oval, -90, 360 * frac, false, ring);

        t.setTextAlign(Paint.Align.CENTER);
        t.setColor(FG);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextSize(rad * 0.62f);
        c.drawText(threeSf(gpa), cx, cy + rad * 0.16f, t);
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        t.setColor(MUTED);
        t.setTextSize(rad * 0.24f);
        c.drawText("GPA", cx, cy + rad * 0.52f, t);

        if (!showList) return bmp;

        // ---- Course list ----
        float left = pad * 2 + ringBox;
        float right = w - pad;
        float top = pad;
        float bottom = h - pad;

        String term = data.optString("term", "Grades");
        String footer = footer(data, status);

        float headSize = Math.min(13 * density, (bottom - top) / (n + 2.4f) * 0.9f);
        float rowH = (bottom - top - headSize * 2.6f) / Math.max(n, 1);
        float rowSize = Math.min(14 * density, rowH * 0.62f);

        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(headSize);
        t.setColor(MUTED);
        c.drawText(term + " · " + data.optInt("credits", 0) + " credits", left, top + headSize, t);

        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = top + headSize * 1.6f;
        for (int i = 0; i < n; i++) {
            JSONObject co = courses.optJSONObject(i);
            if (co == null) continue;
            float base = y + rowH * 0.5f + rowSize * 0.36f;
            String letter = co.optString("letter", "");
            String pct = String.format(Locale.US, "%.1f%%", co.optDouble("percent", 0));

            // letter pill (right edge)
            t.setTextSize(rowSize * 0.85f);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float pillW = Math.max(t.measureText("B+"), t.measureText(letter)) + rowSize * 0.9f;
            float pillH = rowSize * 1.25f;
            RectF pr = new RectF(right - pillW, base - rowSize * 0.95f, right, base - rowSize * 0.95f + pillH);
            pill.setColor(letterColor(letter));
            pill.setAlpha(60);
            c.drawRoundRect(pr, pillH / 2, pillH / 2, pill);
            t.setColor(letterColor(letter));
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(letter, pr.centerX(), base, t);

            // percent
            t.setTextSize(rowSize);
            t.setColor(FG);
            t.setTextAlign(Paint.Align.RIGHT);
            float pctRight = pr.left - rowSize * 0.5f;
            c.drawText(pct, pctRight, base, t);
            float pctW = t.measureText("100.0%");

            // name
            t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            t.setTextAlign(Paint.Align.LEFT);
            float nameW = pctRight - pctW - rowSize * 0.4f - left;
            String name = TextUtils.ellipsize(co.optString("name", ""), t, Math.max(nameW, 10), TextUtils.TruncateAt.END).toString();
            c.drawText(name, left, base, t);

            y += rowH;
        }

        t.setTextSize(headSize * 0.9f);
        t.setColor(MUTED);
        t.setTextAlign(Paint.Align.LEFT);
        c.drawText(TextUtils.ellipsize(footer, t, right - left, TextUtils.TruncateAt.END).toString(), left, bottom, t);
        return bmp;
    }

    private static String footer(JSONObject data, String status) {
        String pulled = data.optString("pulled", "");
        String when = pulled;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(pulled);
            if (d != null) when = new SimpleDateFormat("MMM d", Locale.US).format(d);
        } catch (Exception ignored) { }
        String s = "Grades from " + when;
        return status == null ? s : s + " · " + status;
    }

    private Renderer() {}
}
