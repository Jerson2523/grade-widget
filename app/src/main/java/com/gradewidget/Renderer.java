package com.gradewidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.text.TextPaint;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Draws the whole widget into one bitmap: GPA ring, course list, footer with an Update button. */
final class Renderer {
    static final int GREEN = Color.parseColor("#4CAF50");
    static final int AMBER = Color.parseColor("#FFB300");
    static final int RED = Color.parseColor("#EF5350");

    /** Size of the Update button in dp; the widget layout's tap area matches it. */
    static final float BTN_W_DP = 64, BTN_H_DP = 24, PAD_DP = 12;

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

    static Bitmap draw(JSONObject data, String status, int w, int h, float density, Style s) {
        w = Math.max(w, 100);
        h = Math.max(h, 100);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        int fg = s.fg, muted = s.muted();

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(s.bgWithAlpha());
        float r = s.radiusDp * density;
        c.drawRoundRect(new RectF(0, 0, w, h), r, r, bg);

        float pad = PAD_DP * density;
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(s.face(false));

        drawUpdateButton(c, w, h, density, s);

        if (data == null) {
            t.setColor(fg);
            t.setTextSize(15 * density);
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText("Grades", w / 2f, h / 2f - 6 * density, t);
            t.setColor(muted);
            t.setTextSize(12 * density);
            String msg = status == null ? "Tap to refresh" : status;
            c.drawText(TextUtils.ellipsize(msg, t, w - 2 * pad, TextUtils.TruncateAt.END).toString(),
                    w / 2f, h / 2f + 14 * density, t);
            return bmp;
        }

        double gpa = data.optDouble("gpa", 0);
        JSONArray courses = data.optJSONArray("courses");
        int n = courses == null ? 0 : courses.length();
        boolean showList = s.showList && w > h * 1.35f && n > 0;

        // ---- GPA ring ----
        float ringBox = showList ? Math.min(h - 2 * pad, w * 0.42f) : Math.min(w, h - BTN_H_DP * density) - 2 * pad;
        float stroke = ringBox * 0.11f;
        float cx = showList ? pad + ringBox / 2f : w / 2f;
        float cy = showList ? h / 2f : pad + ringBox / 2f;
        float rad = ringBox / 2f - stroke / 2f;
        RectF oval = new RectF(cx - rad, cy - rad, cx + rad, cy + rad);

        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(s.trackColor);
        c.drawOval(oval, ring);

        float frac = (float) Math.max(0, Math.min(1, gpa / 4.0));
        ring.setColor(s.ringByGpa ? gpaColor(gpa) : s.ringColor);
        if (frac > 0) c.drawArc(oval, -90, 360 * frac, false, ring);

        t.setTextAlign(Paint.Align.CENTER);
        t.setColor(fg);
        t.setTypeface(s.face(true));
        t.setTextSize(rad * 0.62f);
        c.drawText(threeSf(gpa), cx, cy + rad * 0.16f, t);
        t.setTypeface(s.face(false));
        t.setColor(muted);
        t.setTextSize(rad * 0.24f);
        c.drawText("GPA", cx, cy + rad * 0.52f, t);

        if (!showList) return bmp;

        // ---- Course list ----
        float left = pad * 2 + ringBox;
        float right = w - pad;
        float top = pad;
        float footerH = s.showFooter ? BTN_H_DP * density + 4 * density : BTN_H_DP * density;
        float bottom = h - pad - footerH;

        float headSize = Math.min(13 * density, (h - 2 * pad) / (n + 3.4f) * 0.85f);
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(headSize);
        t.setColor(muted);
        c.drawText(data.optString("term", "Grades") + " · " + data.optInt("credits", 0) + " credits", left, top + headSize, t);

        float listTop = top + headSize * 1.5f;
        float rowH = (bottom - listTop) / Math.max(n, 1);
        float rowSize = Math.min(14 * density, rowH * 0.62f);

        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        float y = listTop;
        for (int i = 0; i < n; i++) {
            JSONObject co = courses.optJSONObject(i);
            if (co == null) continue;
            float base = y + rowH * 0.5f + rowSize * 0.36f;
            String letter = co.optString("letter", "");
            String pct = String.format(Locale.US, "%.1f%%", co.optDouble("percent", 0));

            t.setTextSize(rowSize * 0.85f);
            t.setTypeface(s.face(true));
            float pillW = Math.max(t.measureText("B+"), t.measureText(letter)) + rowSize * 0.9f;
            float pillH = rowSize * 1.25f;
            RectF pr = new RectF(right - pillW, base - rowSize * 0.95f, right, base - rowSize * 0.95f + pillH);
            if (s.showBadges) {
                pill.setColor(letterColor(letter));
                pill.setAlpha(60);
                c.drawRoundRect(pr, pillH / 2, pillH / 2, pill);
            }
            t.setColor(letterColor(letter));
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(letter, pr.centerX(), base, t);

            t.setTextSize(rowSize);
            t.setColor(fg);
            t.setTextAlign(Paint.Align.RIGHT);
            float pctRight = pr.left - rowSize * 0.5f;
            c.drawText(pct, pctRight, base, t);
            float pctW = t.measureText("100.0%");

            t.setTypeface(s.face(false));
            t.setTextAlign(Paint.Align.LEFT);
            float nameW = pctRight - pctW - rowSize * 0.4f - left;
            c.drawText(TextUtils.ellipsize(co.optString("name", ""), t, Math.max(nameW, 10), TextUtils.TruncateAt.END).toString(), left, base, t);
            y += rowH;
        }

        if (s.showFooter) {
            t.setTextSize(Math.min(headSize, 11 * density));
            t.setColor(muted);
            t.setTextAlign(Paint.Align.LEFT);
            float btnLeft = w - pad - BTN_W_DP * density;
            float fy = h - pad - (BTN_H_DP * density) / 2f + t.getTextSize() * 0.35f;
            c.drawText(TextUtils.ellipsize(footer(data, status), t, btnLeft - left - 6 * density, TextUtils.TruncateAt.END).toString(), left, fy, t);
        }
        return bmp;
    }

    private static void drawUpdateButton(Canvas c, int w, int h, float density, Style s) {
        float pad = PAD_DP * density;
        RectF b = new RectF(w - pad - BTN_W_DP * density, h - pad - BTN_H_DP * density, w - pad, h - pad);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(40, Color.red(s.fg), Color.green(s.fg), Color.blue(s.fg)));
        c.drawRoundRect(b, b.height() / 2, b.height() / 2, p);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(s.face(true));
        t.setTextSize(11 * density);
        t.setColor(s.fg);
        t.setTextAlign(Paint.Align.CENTER);
        c.drawText("Update", b.centerX(), b.centerY() + t.getTextSize() * 0.35f, t);
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
