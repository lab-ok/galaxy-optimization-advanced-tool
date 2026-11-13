package com.labok.goat.keyprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private TextView events;
    private final Runnable refresh = new Runnable() {
        public void run() { events.setText(Recorder.read(MainActivity.this)); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 90, 32, 32);
        TextView heading = new TextView(this);
        heading.setText("GOAT · App-level key input probe\n\nNo Shizuku, shell reader, or privileged permission.\nOnly POWER / VOLUME control keys and screen-state events are recorded.\nThis diagnostic does not change rotation.");
        heading.setTextSize(18);
        layout.addView(heading);
        Button settings = new Button(this);
        settings.setText("Accessibility settings");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        layout.addView(settings);
        Button clear = new Button(this);
        clear.setText("Clear recorded events");
        clear.setOnClickListener(v -> Recorder.clear(this));
        layout.addView(clear);
        events = new TextView(this);
        events.setTextSize(13);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(events);
        layout.addView(scroll);
        setContentView(layout);
        handleIntent(getIntent());
    }
    @Override public void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIntent(intent); }
    private void handleIntent(Intent intent) { if (intent.getBooleanExtra("clear", false)) Recorder.clear(this); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        Recorder.key(this, "activity_key", event);
        return super.dispatchKeyEvent(event);
    }
    @Override public void onResume() { super.onResume(); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
