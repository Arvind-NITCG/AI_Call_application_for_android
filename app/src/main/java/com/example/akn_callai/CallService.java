package com.example.akn_callai;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public class CallService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("incoming_number")) {
            String targetNumber = intent.getStringExtra("incoming_number");
            Log.d("AKN_AI", "Processing Missed Call: " + targetNumber);

            // Send SMS & Save
            //sendSms(targetNumber);
            saveToAiLog(targetNumber);
        }
        return START_NOT_STICKY;
    }

    private void saveToAiLog(String number) {
        SharedPreferences prefs = getSharedPreferences("AI_PREFS", MODE_PRIVATE);
        // We use "ai_handled_events" now, not just numbers
        Set<String> aiEvents = prefs.getStringSet("ai_handled_events", new HashSet<>());

        Set<String> newSet = new HashSet<>(aiEvents);

        // Save format: "NUMBER_TIMESTAMP" (e.g., "+919876543210_1704789000000")
        String uniqueKey = number + "_" + System.currentTimeMillis();

        newSet.add(uniqueKey);

        prefs.edit().putStringSet("ai_handled_events", newSet).apply();
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}