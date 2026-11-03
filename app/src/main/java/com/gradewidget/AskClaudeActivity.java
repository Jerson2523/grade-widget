package com.gradewidget;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Widget's Update button: copies "update my grades" and opens the Claude project chat. */
public class AskClaudeActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("message", "update my grades"));
        } catch (Exception ignored) { }
        Toast.makeText(this, "Copied \"update my grades\" — paste it to Claude", Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Store.claudeUrl(this)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open Claude", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
