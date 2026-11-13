package com.labok.goat.keyprobe;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class KeyProbeService extends AccessibilityService {
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { Recorder.screen(context, intent.getAction()); }
    };
    @Override public void onServiceConnected() {
        Recorder.note(this, "service_connected", "flags=" + getServiceInfo().flags + " capabilities=" + getServiceInfo().getCapabilities());
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        Recorder.key(this, "accessibility_key", event);
        return false;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { unregisterReceiver(screen); super.onDestroy(); }
}
