package com.example.akn_callai;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;

    // UI Components
    EditText etMessage;
    Button btnSave;
    RecyclerView recyclerView;
    CallLogAdapter adapter;
    List<CallLogAdapter.CallLogItem> callList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerViewCalls);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        callList = new ArrayList<>();
        adapter = new CallLogAdapter(callList);
        recyclerView.setAdapter(adapter);

        // 2. Setup Header Navigation (P and R)
        View btnPreferences = findViewById(R.id.btnPreferences);
        View btnReport = findViewById(R.id.btnReport);

        // Click P -> Go to Preferences (Type Message)
        btnPreferences.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, PreferencesActivity.class));
        });

        // Click R -> Go to Report (See AI Logs)
        btnReport.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ReportActivity.class));
        });

        // 3. Permissions & Data
        if (checkPermission()) {
            loadRealCallLogs();
        } else {
            requestPermission();
        }
    }

    // Refresh the list every time the user opens the app (The "Boss" feature)
    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermission()) {
            loadRealCallLogs();
        }
    }

    private void loadRealCallLogs() {
        callList.clear();

        SharedPreferences prefs = getSharedPreferences("AI_PREFS", MODE_PRIVATE);
        // Load our list of specific events
        Set<String> aiEvents = prefs.getStringSet("ai_handled_events", new HashSet<>());

        try {
            Cursor cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null, null, null,
                    CallLog.Calls.DATE + " DESC");

            if (cursor != null) {
                int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);

                while (cursor.moveToNext()) {
                    String number = cursor.getString(numberIndex);
                    String name = cursor.getString(nameIndex);
                    long callDate = cursor.getLong(dateIndex); // Time call started

                    if (name == null || name.isEmpty()) name = "Unknown Caller";

                    // --- NEW LOGIC: Check strict timestamp match ---
                    boolean isHandledByAi = checkIsAiEvent(aiEvents, number, callDate);

                    callList.add(new CallLogAdapter.CallLogItem(
                            name, number, getSmartDate(callDate), isHandledByAi
                    ));
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Permission Error", Toast.LENGTH_SHORT).show();
        }
        adapter.notifyDataSetChanged();
    }

    // Helper to match Number AND Time (Window of 60 seconds)
    private boolean checkIsAiEvent(Set<String> aiEvents, String number, long callDate) {
        for (String event : aiEvents) {
            // Event format: "NUMBER_TIMESTAMP"
            String[] parts = event.split("_");
            if (parts.length == 2) {
                String savedNum = parts[0];
                long savedTime = Long.parseLong(parts[1]);

                // Check Number Match
                if (savedNum.equals(number)) {
                    // Check Time Match (Difference less than 60 seconds)
                    // The AI runs AFTER the call rings, so savedTime > callDate usually.
                    long diff = Math.abs(savedTime - callDate);
                    if (diff < 60000) {
                        return true; // Match found!
                    }
                }
            }
        }
        return false;
    }

    private String getSmartDate(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar callTime = Calendar.getInstance();
        callTime.setTimeInMillis(timestamp);

        if (now.get(Calendar.DAY_OF_YEAR) == callTime.get(Calendar.DAY_OF_YEAR)
                && now.get(Calendar.YEAR) == callTime.get(Calendar.YEAR)) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            return "Today, " + timeFormat.format(callTime.getTime());
        } else if (now.get(Calendar.DAY_OF_YEAR) - callTime.get(Calendar.DAY_OF_YEAR) == 1) {
            return "Yesterday";
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
            return dateFormat.format(callTime.getTime());
        }
    }

    private void savePreferences() {
        String msg = etMessage.getText().toString();
        if(!msg.isEmpty()){
            getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE)
                    .edit().putString("custom_msg", msg).apply();
            Toast.makeText(this, "AI Reply Updated!", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPreferences() {
        String msg = getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE)
                .getString("custom_msg", "");
        etMessage.setText(msg);
    }

    private boolean checkPermission() {
        int resultCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG);
        int resultReadPhone = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
        int resultAnswer = ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS);
        int resultSendSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS); // <--- NEW!

        return resultCallLog == PackageManager.PERMISSION_GRANTED &&
                resultReadPhone == PackageManager.PERMISSION_GRANTED &&
                resultAnswer == PackageManager.PERMISSION_GRANTED &&
                resultSendSms == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.SEND_SMS
        }, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadRealCallLogs();
            }
        }
    }
}