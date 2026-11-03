package com.gradewidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;

/** Widget look, saved in preferences and edited in the app. */
final class Style {
    int bg = Color.parseColor("#1F2430");
    int bgAlpha = 90;            // percent
    int radiusDp = 22;
    int fg = Color.parseColor("#F2F4F8");
    boolean ringByGpa = true;
    int ringColor = Color.parseColor("#4F8EF7");
    int trackColor = Color.parseColor("#3A4152");
    String font = "sans";        // sans, serif, mono, rounded
    boolean showList = true;
    boolean showBadges = true;
    boolean showFooter = true;

    static Style load(Context c) {
        SharedPreferences p = Store.prefs(c);
        Style s = new Style();
        s.bg = p.getInt("st_bg", s.bg);
        s.bgAlpha = p.getInt("st_bgAlpha", s.bgAlpha);
        s.radiusDp = p.getInt("st_radius", s.radiusDp);
        s.fg = p.getInt("st_fg", s.fg);
        s.ringByGpa = p.getBoolean("st_ringByGpa", s.ringByGpa);
        s.ringColor = p.getInt("st_ring", s.ringColor);
        s.trackColor = p.getInt("st_track", s.trackColor);
        s.font = p.getString("st_font", s.font);
        s.showList = p.getBoolean("st_list", s.showList);
        s.showBadges = p.getBoolean("st_badges", s.showBadges);
        s.showFooter = p.getBoolean("st_footer", s.showFooter);
        return s;
    }

    void save(Context c) {
        Store.prefs(c).edit()
                .putInt("st_bg", bg).putInt("st_bgAlpha", bgAlpha).putInt("st_radius", radiusDp)
                .putInt("st_fg", fg).putBoolean("st_ringByGpa", ringByGpa).putInt("st_ring", ringColor)
                .putInt("st_track", trackColor).putString("st_font", font)
                .putBoolean("st_list", showList).putBoolean("st_badges", showBadges)
                .putBoolean("st_footer", showFooter).apply();
    }

    static void reset(Context c) {
        new Style().save(c);
    }

    int bgWithAlpha() {
        return Color.argb(Math.round(bgAlpha * 2.55f), Color.red(bg), Color.green(bg), Color.blue(bg));
    }

    int muted() {
        return Color.argb(165, Color.red(fg), Color.green(fg), Color.blue(fg));
    }

    Typeface face(boolean bold) {
        String fam;
        switch (font) {
            case "serif": fam = "serif"; break;
            case "mono": fam = "monospace"; break;
            case "rounded": fam = bold ? "sans-serif-condensed" : "sans-serif-condensed-light"; break;
            default: fam = bold ? "sans-serif-medium" : "sans-serif";
        }
        boolean styleBold = bold && (font.equals("serif") || font.equals("mono"));
        return Typeface.create(fam, styleBold ? Typeface.BOLD : Typeface.NORMAL);
    }
}
