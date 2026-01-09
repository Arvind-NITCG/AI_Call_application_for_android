package com.example.akn_callai;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ReportActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    CallLogAdapter adapter;
    List<CallLogAdapter.CallLogItem> reportList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        recyclerView = findViewById(R.id.recyclerViewReport);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        reportList = new ArrayList<>();
        adapter = new CallLogAdapter(reportList);
        recyclerView.setAdapter(adapter);

        loadAiOnlyLogs();
    }

    private void loadAiOnlyLogs() {
        reportList.clear();

        // 1. Get the list of AI Events (Number_Timestamp)
        SharedPreferences prefs = getSharedPreferences("AI_PREFS", MODE_PRIVATE);
        Set<String> aiEvents = prefs.getStringSet("ai_handled_events", new HashSet<>());

        if (aiEvents.isEmpty()) return;

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
                    long callDate = cursor.getLong(dateIndex);

                    // 2. THE FIX: Strict Check!
                    // Only add if the Time AND Number match our records
                    if (checkIsAiEvent(aiEvents, number, callDate)) {

                        String name = cursor.getString(nameIndex);
                        if (name == null || name.isEmpty()) name = "Unknown Caller";

                        reportList.add(new CallLogAdapter.CallLogItem(
                                name, number, getSmartDate(callDate), true
                        ));
                    }
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Permission Error", Toast.LENGTH_SHORT).show();
        }

        adapter.notifyDataSetChanged();
    }

    // The Logic Engine: Matches Number + Time (within 60 seconds)
    private boolean checkIsAiEvent(Set<String> aiEvents, String number, long callDate) {
        for (String event : aiEvents) {
            String[] parts = event.split("_");
            if (parts.length == 2) {
                String savedNum = parts[0];
                long savedTime = Long.parseLong(parts[1]);

                if (savedNum.equals(number)) {
                    long diff = Math.abs(savedTime - callDate);
                    if (diff < 60000) { // 60 seconds tolerance
                        return true;
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

        if (now.get(Calendar.DAY_OF_YEAR) == callTime.get(Calendar.DAY_OF_YEAR)) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            return "Today, " + timeFormat.format(callTime.getTime());
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
            return dateFormat.format(callTime.getTime());
        }
    }
}