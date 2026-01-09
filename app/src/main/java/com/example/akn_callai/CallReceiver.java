package com.example.akn_callai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

public class CallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // We ONLY listen for the IDLE state (When call ends)
        if (intent != null && TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                Log.d("AKN_AI", "Call Ended. Checking System Database...");

                // Go Async to keep app alive while checking database
                final PendingResult pendingResult = goAsync();

                // Wait 1.5 seconds for Android to write the log to storage
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    verifyAndReact(context);
                    pendingResult.finish();
                }, 1500);
            }
        }
    }

    private void verifyAndReact(Context context) {
        try {
            // QUERY: Get the very last call from the system log
            Cursor cursor = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null, null, null,
                    CallLog.Calls.DATE + " DESC LIMIT 1");

            if (cursor != null && cursor.moveToFirst()) {
                int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);

                int type = cursor.getInt(typeIndex);
                String number = cursor.getString(numberIndex);
                cursor.close();

                // VERDICT: Only reply if it is officially a MISSED or REJECTED call
                if (type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE) {
                    Log.d("AKN_AI", "Verdict: MISSED CALL from " + number + ". Sending SMS.");
                    sendSms(context, number);
                } else {
                    Log.d("AKN_AI", "Verdict: ANSWERED call. No Action.");
                }
            }
        } catch (SecurityException e) {
            Log.e("AKN_AI", "Permission Error: " + e.getMessage());
        }
    }

    private void sendSms(Context context, String number) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE);
            String msg = prefs.getString("custom_msg", "I am currently unavailable.");

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, "[AI]:Hi I am FormalHault, the agentic AI manager of Arvind. He left you this note: " + msg, null, null);

            // Save to logs so the Badge appears
            saveToAiLog(context, number);

        } catch (Exception e) {
            Log.e("AKN_AI", "SMS Failed: " + e.getMessage());
        }
    }

    private void saveToAiLog(Context context, String number) {
        SharedPreferences prefs = context.getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE);
        Set<String> aiEvents = prefs.getStringSet("ai_handled_events", new HashSet<>());
        Set<String> newSet = new HashSet<>(aiEvents);

        // Save unique event: NUMBER_TIMESTAMP
        newSet.add(number + "_" + System.currentTimeMillis());

        prefs.edit().putStringSet("ai_handled_events", newSet).apply();
    }
}