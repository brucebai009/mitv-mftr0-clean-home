package com.gathin.tvhome.entry;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.security.MessageDigest;

/** User-clicked entry only. No HOME interception, background activity or media work. */
public final class EntryActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView loading = new TextView(this);
        loading.setText(R.string.opening);
        loading.setGravity(Gravity.CENTER);
        setContentView(loading);
        openPreferred();
    }

    private EntryPolicy.State state(String pkg) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(pkg,
                    Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES
                            : PackageManager.GET_SIGNATURES);
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= 28) {
                signatures = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
            } else {
                signatures = info.signatures;
            }
            if (signatures == null) return EntryPolicy.State.UNTRUSTED;
            String[] hashes = new String[signatures.length];
            for (int i = 0; i < signatures.length; i++) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray());
                StringBuilder hex = new StringBuilder(64);
                for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                hashes[i] = hex.toString();
            }
            return EntryPolicy.trustedCertificates(hashes) ? EntryPolicy.State.TRUSTED : EntryPolicy.State.UNTRUSTED;
        } catch (PackageManager.NameNotFoundException e) {
            return EntryPolicy.State.ABSENT;
        } catch (Exception e) {
            return EntryPolicy.State.UNTRUSTED;
        }
    }

    private void openPreferred() {
        String target = EntryPolicy.automaticTarget(state(EntryPolicy.COMPAT), state(EntryPolicy.ORIGINAL));
        if (target == null || !launch(target)) showUnavailable();
    }

    private boolean launch(String pkg) {
        if (state(pkg) != EntryPolicy.State.TRUSTED) return false;
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName(pkg, "com.gathin.tvhome.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
            finish();
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void showUnavailable() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        TextView message = new TextView(this);
        message.setText(R.string.unavailable);
        message.setTextSize(22);
        content.addView(message);
        Button retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setOnClickListener(v -> openPreferred());
        content.addView(retry);
        if (state(EntryPolicy.ORIGINAL) == EntryPolicy.State.TRUSTED) {
            Button original = new Button(this);
            original.setText(R.string.open_original);
            original.setOnClickListener(v -> { if (!launch(EntryPolicy.ORIGINAL)) showUnavailable(); });
            content.addView(original);
        }
        Button close = new Button(this);
        close.setText(R.string.close);
        close.setOnClickListener(v -> finish());
        content.addView(close);
        setContentView(content);
        retry.requestFocus();
    }
}
