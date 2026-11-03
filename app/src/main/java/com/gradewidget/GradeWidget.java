package com.gradewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** Home-screen widget: shows the cached grades right away, then fetches fresh ones in the background. */
public class GradeWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.gradewidget.REFRESH";

    /** Redraw every widget from the saved grades (after a look change), without downloading. */
    static void redraw(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(app, GradeWidget.class));
        render(app, mgr, ids, Store.cached(app), null);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        refreshAsync(context, goAsync());
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, Bundle newOptions) {
        render(context, mgr, new int[]{id}, Store.cached(context), null);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            refreshAsync(context, goAsync());
            return;
        }
        super.onReceive(context, intent);
    }

    /** Ask every placed widget to refresh (used by the app after saving settings). */
    static void requestRefresh(Context context) {
        Intent i = new Intent(context, GradeWidget.class).setAction(ACTION_REFRESH);
        context.sendBroadcast(i);
    }

    private static void refreshAsync(Context context, PendingResult pending) {
        final Context app = context.getApplicationContext();
        final AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        final int[] ids = mgr.getAppWidgetIds(new ComponentName(app, GradeWidget.class));
        render(app, mgr, ids, Store.cached(app), "Updating…");
        final PendingResult pr = pending;
        new Thread(() -> {
            String status = null;
            JSONObject data;
            try {
                data = Store.refresh(app);
            } catch (Exception e) {
                data = Store.cached(app);
                status = e.getMessage() == null ? "Couldn't update" : e.getMessage();
                if (data != null) status = "offline";
            }
            render(app, mgr, ids, data, status);
            if (pr != null) pr.finish();
        }).start();
    }

    static void render(Context context, AppWidgetManager mgr, int[] ids, JSONObject data, String status) {
        float density = context.getResources().getDisplayMetrics().density;
        Style style = Style.load(context);
        for (int id : ids) {
            Bundle o = mgr.getAppWidgetOptions(id);
            int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);
            if (wDp <= 0) wDp = 250;
            if (hDp <= 0) hDp = 110;
            int w = Math.min(Math.round(wDp * density), 1400);
            int h = Math.min(Math.round(hDp * density), 1000);

            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget);
            rv.setImageViewBitmap(R.id.canvas, Renderer.draw(data, status, w, h, density, style));

            Intent tap = new Intent(context, GradeWidget.class).setAction(ACTION_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(context, 0, tap,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.root, pi);

            Intent ask = new Intent(context, AskClaudeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent askPi = PendingIntent.getActivity(context, 1, ask,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.update_btn, askPi);
            mgr.updateAppWidget(id, rv);
        }
    }
}
