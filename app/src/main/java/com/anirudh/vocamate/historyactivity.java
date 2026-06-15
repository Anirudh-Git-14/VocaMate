package com.anirudh.vocamate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class historyactivity extends AppCompatActivity {

    TextView txtHistoryTitle, txtHistoryMessage;
    LinearLayout historyContainer;

    FirebaseAuth firebaseAuth;
    FirebaseUser currentUser;
    FirebaseFirestore db;

    String filterLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.historyactivity);

        txtHistoryTitle = findViewById(R.id.txtHistoryTitle);
        txtHistoryMessage = findViewById(R.id.txtHistoryMessage);
        historyContainer = findViewById(R.id.historyContainer);

        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();

        filterLanguage = getIntent().getStringExtra("filterLanguage");

        if (filterLanguage == null || filterLanguage.trim().isEmpty()) {
            txtHistoryTitle.setText("All Practice History");
        } else {
            txtHistoryTitle.setText(filterLanguage + " History");
        }

        if (currentUser == null || currentUser.getEmail() == null) {
            txtHistoryMessage.setText("Please login to view your practice history.");
            return;
        }

        loadPracticeHistory(currentUser.getEmail());
    }

    private void loadPracticeHistory(String userEmail) {
        txtHistoryMessage.setText("Loading history...");

        db.collection("users")
                .document(userEmail)
                .collection("conversation_reports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        txtHistoryMessage.setText("No practice history found yet.");
                        return;
                    }

                    int count = 0;

                    for (int i = 0; i < queryDocumentSnapshots.size(); i++) {
                        String documentId = queryDocumentSnapshots.getDocuments().get(i).getId();
                        Boolean userDeleted = queryDocumentSnapshots.getDocuments().get(i).getBoolean("userDeleted");

                        if (userDeleted != null && userDeleted) {
                            continue;
                        }
                        String language = queryDocumentSnapshots.getDocuments().get(i).getString("language");

                        if (filterLanguage != null &&
                                !filterLanguage.trim().isEmpty() &&
                                !filterLanguage.equals(language)) {
                            continue;
                        }

                        count++;

                        String mode = queryDocumentSnapshots.getDocuments().get(i).getString("mode");
                        String status = queryDocumentSnapshots.getDocuments().get(i).getString("reportStatus");
                        String transcript = queryDocumentSnapshots.getDocuments().get(i).getString("transcript");

                        Timestamp timestamp = queryDocumentSnapshots.getDocuments().get(i).getTimestamp("createdAt");
                        String dateText = "Time not available";

                        if (timestamp != null) {
                            Date date = timestamp.toDate();
                            SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                            dateText = format.format(date);
                        }

                        if (transcript == null || transcript.trim().isEmpty()) {
                            transcript = "No speech recorded";
                        }

                        if (transcript.length() > 90) {
                            transcript = transcript.substring(0, 90) + "...";
                        }

                        String buttonText =
                                language + " • " + mode + "\n" +
                                        "Status: " + status + "\n" +
                                        "Date: " + dateText + "\n" +
                                        "Tap to view full report";

                        Button sessionButton = new Button(historyactivity.this);
                        sessionButton.setText(buttonText);
                        sessionButton.setAllCaps(false);
                        sessionButton.setTextColor(ContextCompat.getColor(historyactivity.this, R.color.bg_dark));
                        sessionButton.setBackgroundResource(R.drawable.button_primary);

                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        params.setMargins(0, 0, 0, 16);
                        sessionButton.setLayoutParams(params);

                        sessionButton.setOnClickListener(v -> {
                            Intent intent = new Intent(historyactivity.this, historydetailactivity.class);
                            intent.putExtra("sessionId", documentId);
                            if (filterLanguage != null && !filterLanguage.trim().isEmpty()) {
                                intent.putExtra("filterLanguage", filterLanguage);
                            }
                            startActivity(intent);
                        });

                        historyContainer.addView(sessionButton);
                    }

                    if (count == 0) {
                        if (filterLanguage == null || filterLanguage.trim().isEmpty()) {
                            txtHistoryMessage.setText("No practice history found yet.");
                        } else {
                            txtHistoryMessage.setText("No " + filterLanguage + " history found yet.");
                        }
                    } else {
                        txtHistoryMessage.setText("Total sessions: " + count);
                    }
                })
                .addOnFailureListener(e -> {
                    txtHistoryMessage.setText("Could not load history. Please try again.");
                });
    }
}