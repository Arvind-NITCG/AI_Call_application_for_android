package com.example.akn_callai;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PreferencesActivity extends AppCompatActivity {

    EditText etMessage;
    Button btnSave;
    TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        etMessage = findViewById(R.id.etConfigMessage);
        btnSave = findViewById(R.id.btnConfigSave);
        tvStatus = findViewById(R.id.tvConfigStatus);

        // Load existing message
        loadCurrentMessage();

        btnSave.setOnClickListener(v -> saveMessage());
    }

    private void loadCurrentMessage() {
        SharedPreferences prefs = getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE);
        String currentMsg = prefs.getString("custom_msg", "");
        etMessage.setText(currentMsg);
    }

    private void saveMessage() {
        String newMsg = etMessage.getText().toString();

        if (!newMsg.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("AI_PREFS", Context.MODE_PRIVATE);
            prefs.edit().putString("custom_msg", newMsg).apply();

            // Cool "Terminal" effect for saving
            tvStatus.setText("Updating Protocol...");
            tvStatus.setTextColor(0xFFFFFF00); // Yellow

            new Handler().postDelayed(() -> {
                tvStatus.setText("PROTOCOL UPDATED SUCCESSFULLY.");
                tvStatus.setTextColor(0xFF4CAF50); // Green
                Toast.makeText(this, "AI Logic Updated", Toast.LENGTH_SHORT).show();
                finish(); // Go back to main screen
            }, 800);
        } else {
            tvStatus.setText("ERROR: Payload cannot be empty.");
            tvStatus.setTextColor(0xFFFF5252); // Red
        }
    }
}