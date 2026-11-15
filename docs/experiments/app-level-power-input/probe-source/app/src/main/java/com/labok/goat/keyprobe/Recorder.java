package com.labok.goat.keyprobe;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

final class Recorder {
    private static long firstScreenEvent;
    private static int screenEvents;
    private static String lastScreenAction = "";
    static synchronized void note(Context context, String source, String detail) {
        try {
            JSONObject row = new JSONObject();
            row.put("elapsed_ms", SystemClock.elapsedRealtime());
            row.put("utc_ms", System.currentTimeMillis());
            row.put("source", source);
            row.put("detail", detail);
            try (FileWriter writer = new FileWriter(file(context), true)) {
                writer.write(row.toString() + "\n");
            }
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static void key(Context context, String source, KeyEvent event) {
        int code = event.getKeyCode();
        // Restrict diagnostics to the requested key and explicit positive controls.
        if (code != KeyEvent.KEYCODE_POWER && code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return;
        String action = event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN" : event.getAction() == KeyEvent.ACTION_UP ? "UP" : "MULTIPLE";
        note(context, source, KeyEvent.keyCodeToString(code) + " " + action + " repeat=" + event.getRepeatCount());
    }
    static synchronized void screen(Context context, String action) {
        note(context, "screen_broadcast", action);
        long now = SystemClock.elapsedRealtime();
        if (screenEvents == 0 || now - firstScreenEvent > 1500 || action.equals(lastScreenAction)) {
            firstScreenEvent = now;
            screenEvents = 0;
        }
        lastScreenAction = action;
        if (++screenEvents == 3) {
            note(context, "screen_triple_candidate", "Three screen transitions; physical key identity is unknown");
            screenEvents = 0;
        }
    }
    static synchronized void clear(Context context) {
        try (FileOutputStream writer = new FileOutputStream(file(context))) { writer.write(new byte[0]); }
        catch (Exception e) { throw new IllegalStateException(e); }
        firstScreenEvent = 0;
        screenEvents = 0;
        lastScreenAction = "";
    }
    static synchronized String read(Context context) {
        try { return new String(Files.readAllBytes(file(context).toPath()), StandardCharsets.UTF_8); }
        catch (Exception e) { return "No events yet. Enable the probe accessibility service, then press Volume Up and Power."; }
    }
    private static File file(Context context) { return new File(context.getFilesDir(), "events.jsonl"); }
}
