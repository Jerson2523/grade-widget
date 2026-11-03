package com.gradewidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Saved settings, the last good grades, and fetching + decrypting new ones. */
final class Store {
    private static final String PREFS = "grades";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String url(Context c) {
        return prefs(c).getString("url", BuildConfig.DATA_URL);
    }

    static final String DEFAULT_CLAUDE_URL = "https://claude.ai/project/01a19ecb-71ee-736c-875f-13c1bef81c46";

    static String claudeUrl(Context c) {
        return prefs(c).getString("claudeUrl", DEFAULT_CLAUDE_URL);
    }

    static void saveClaudeUrl(Context c, String u) {
        prefs(c).edit().putString("claudeUrl", u.trim().isEmpty() ? DEFAULT_CLAUDE_URL : u.trim()).apply();
    }

    static String passphrase(Context c) {
        return prefs(c).getString("pass", "");
    }

    static void saveSettings(Context c, String url, String pass) {
        prefs(c).edit().putString("url", url.trim()).putString("pass", pass.trim())
                .remove("keySalt").remove("key").apply();
    }

    /** Last grades that decrypted fine, or null. */
    static JSONObject cached(Context c) {
        String s = prefs(c).getString("data", null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (Exception e) { return null; }
    }

    static long fetchedAt(Context c) {
        return prefs(c).getLong("fetchedAt", 0);
    }

    /** Download, decrypt and cache. Returns the grades; throws with a readable message on failure. */
    static JSONObject refresh(Context c) throws Exception {
        String pass = passphrase(c);
        if (pass.isEmpty()) throw new Exception("Open the Grades app and enter your passphrase");
        String body = download(url(c) + "?t=" + System.currentTimeMillis());
        JSONObject env = new JSONObject(body);
        JSONObject data = new JSONObject(decrypt(c, env, pass));
        prefs(c).edit().putString("data", data.toString())
                .putLong("fetchedAt", System.currentTimeMillis()).apply();
        return data;
    }

    private static String download(String u) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setUseCaches(false);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new Exception("Download failed (HTTP " + code + ")");
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            con.disconnect();
        }
    }

    private static String decrypt(Context c, JSONObject env, String pass) throws Exception {
        Base64.Decoder b64 = Base64.getDecoder();
        String saltB64 = env.getString("salt");
        byte[] iv = b64.decode(env.getString("iv"));
        byte[] ct = b64.decode(env.getString("ct"));
        int iter = env.optInt("iter", 200000);

        // Key derivation is slow on purpose, so keep the derived key for this salt.
        SharedPreferences p = prefs(c);
        byte[] key;
        if (saltB64.equals(p.getString("keySalt", null)) && p.getString("key", null) != null) {
            key = b64.decode(p.getString("key", ""));
        } else {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            key = f.generateSecret(new PBEKeySpec(pass.toCharArray(), b64.decode(saltB64), iter, 256)).getEncoded();
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] plain;
        try {
            plain = cipher.doFinal(ct);
        } catch (Exception e) {
            p.edit().remove("key").remove("keySalt").apply();
            throw new Exception("Wrong passphrase");
        }
        p.edit().putString("keySalt", saltB64).putString("key", Base64.getEncoder().encodeToString(key)).apply();
        return new String(plain, StandardCharsets.UTF_8);
    }

    private Store() {}
}
