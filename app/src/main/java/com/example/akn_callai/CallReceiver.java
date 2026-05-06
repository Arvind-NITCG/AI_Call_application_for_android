package com.example.akn_callai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class CallReceiver extends BroadcastReceiver {

    private static boolean isCallActive = false;
    private static TextToSpeech tts;
    private static String fetchedName = null;
    private static boolean isNameReady = false;
    private static final long URGENT_WINDOW_MS = 3 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {

            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                SharedPreferences prefs = context.getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE);
                if (!prefs.getBoolean("is_voice_enabled", true)) return;

                isCallActive = true;
                isNameReady = false; // Reset flag
                String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                if (incomingNumber != null) {
                    final PendingResult pendingResult = goAsync();
                    new Thread(() -> {
                        fetchedName = getContactName(context, incomingNumber);
                        isNameReady = true;
                    }).start();
                    boolean isUrgent = checkUrgency(context, incomingNumber); // Fast check

                    tts = new TextToSpeech(context, status -> {
                        if (status == TextToSpeech.SUCCESS) {
                            new Thread(() -> {
                                int attempts = 0;
                                while (!isNameReady && attempts < 15) {
                                    try { Thread.sleep(100); } catch (Exception e) {}
                                    attempts++;
                                }

                                String finalName = isNameReady ? fetchedName : "Unknown Caller";
                                speakAnnouncementLogic(context, finalName, isUrgent, pendingResult);
                            }).start();
                        } else {
                            pendingResult.finish();
                        }
                    });
                }
            }
            else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                isCallActive = false;
                stopVoiceEngine(); // Kill TTS immediately to save RAM

                // Only proceed if we haven't processed this recently
                // (Use Handler to wait for Call Log DB update)
                final PendingResult pendingResult = goAsync();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    verifyAndReact(context);
                    pendingResult.finish();
                }, 1500);
            }
            else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                isCallActive = false;
                stopVoiceEngine();
            }
        }
    }
    private void speakAnnouncementLogic(Context context, String name, boolean isUrgent, PendingResult pendingResult) {
        if (!isCallActive) {
            stopVoiceEngine();
            pendingResult.finish();
            return;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int oldVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) { if (!isCallActive) stopVoiceEngine(); }

            @Override
            public void onDone(String utteranceId) {
                // Restore & Cleanup
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, oldVolume, 0);
                stopVoiceEngine();
                pendingResult.finish();
            }

            @Override
            public void onError(String utteranceId) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, oldVolume, 0);
                stopVoiceEngine();
                pendingResult.finish();
            }
        });

        // Config Voice
        tts.setLanguage(Locale.US);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        tts.setAudioAttributes(audioAttributes);

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);

        String textToSpeak;
        if (isUrgent) {
            tts.setPitch(1.1f);
            tts.setSpeechRate(1.2f);
            textToSpeak = "Sir!! attention!! attention!! " + name + " is calling multiple times! It is Urgent!";
        } else {
            tts.setPitch(1.1f);
            tts.setSpeechRate(0.85f);
            textToSpeak = "Sir, Incoming call from " + name;
        }

        if (isCallActive) {
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, params, "ID");
        }
    }
    private void stopVoiceEngine() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
            }
        } catch (Exception e) {}
    }

    private String getContactName(Context context, String phoneNumber) {
        String name = "Unknown Caller";
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) name = cursor.getString(0);
                cursor.close();
            }
        } catch (Exception e) {}
        return name;
    }
    private boolean checkUrgency(Context context, String number) {
        SharedPreferences prefs = context.getSharedPreferences("CALL_HISTORY_PREFS", Context.MODE_PRIVATE);
        long lastCallTime = prefs.getLong("last_call_" + number, 0);
        long currentTime = System.currentTimeMillis();
        prefs.edit().putLong("last_call_" + number, currentTime).apply();
        return (lastCallTime != 0 && (currentTime - lastCallTime) < URGENT_WINDOW_MS);
    }
    private boolean isContact(Context context, String number) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null);
            if (cursor != null) {
                boolean exists = cursor.getCount() > 0;
                cursor.close();
                return exists;
            }
        } catch (Exception e) {}
        return false;
    }
    private void verifyAndReact(Context context) {
        try (Cursor cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC")) {

            if (cursor != null && cursor.moveToFirst()) {
                int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int type = cursor.getInt(typeIndex);
                String number = cursor.getString(numberIndex);

                if (type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE) {
                    // Check if friend
                    if (isContact(context, number)) {
                        sendSms(context, number);
                    }
                }
            }
        } catch (Exception e) {}
    }
    private void sendSms(Context context, String number) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE);
            String userMsg = prefs.getString("custom_msg", "I am currently unavailable.");
            String fullMessage = "Hi there, I am Vega the personal assistant of Arvind..He left you this note..Look onto it :" + userMsg;

            SmsManager smsManager = SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(fullMessage);
            smsManager.sendMultipartTextMessage(number, null, parts, null, null);
            saveToAiLog(context, number);
        } catch (Exception e) {}
    }
    private void saveToAiLog(Context context, String number) {
        SharedPreferences prefs = context.getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE);
        Set<String> aiEvents = prefs.getStringSet("ai_handled_events", new HashSet<>());
        Set<String> newSet = new HashSet<>(aiEvents);
        newSet.add(number + "_" + System.currentTimeMillis());
        prefs.edit().putStringSet("ai_handled_events", newSet).apply();
    }
}