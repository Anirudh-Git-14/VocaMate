package com.anirudh.vocamate;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import android.content.Intent;
import android.widget.Button;
import com.google.firebase.firestore.FieldValue;
import java.util.Date;
import java.util.Locale;

public class historydetailactivity extends AppCompatActivity {

    TextView txtHistoryDetail;

    FirebaseAuth firebaseAuth;
    FirebaseUser currentUser;
    FirebaseFirestore db;
    Button btnDeleteFromHistory;
    String filterLanguage;

    String sessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.historydetailactivity);

        txtHistoryDetail = findViewById(R.id.txtHistoryDetail);
        btnDeleteFromHistory = findViewById(R.id.btnDeleteFromHistory);

        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();

        sessionId = getIntent().getStringExtra("sessionId");
        filterLanguage = getIntent().getStringExtra("filterLanguage");

        if (currentUser == null || currentUser.getEmail() == null) {
            txtHistoryDetail.setText("Please login to view this report.");
            return;
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            txtHistoryDetail.setText("Session not found.");
            return;
        }
        btnDeleteFromHistory.setOnClickListener(v -> {
            deleteFromAppHistory(currentUser.getEmail(), sessionId);
        });

        loadHistoryDetail(currentUser.getEmail(), sessionId);
    }

    private void loadHistoryDetail(String userEmail, String sessionId) {
        txtHistoryDetail.setText("Loading report...");

        db.collection("users")
                .document(userEmail)
                .collection("conversation_reports")
                .document(sessionId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        txtHistoryDetail.setText("Report not found.");
                        return;
                    }

                    String language = document.getString("language");
                    String mode = document.getString("mode");
                    String status = document.getString("reportStatus");
                    String transcript = document.getString("transcript");
                    String fullConversation = document.getString("fullConversation");
                    String aiReport = document.getString("aiReport");

                    Timestamp timestamp = document.getTimestamp("createdAt");
                    String dateText = "Time not available";

                    if (timestamp != null) {
                        Date date = timestamp.toDate();
                        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                        dateText = format.format(date);
                    }

                    if (transcript == null || transcript.trim().isEmpty()) {
                        transcript = "No speech recorded";
                    }

                    if (fullConversation == null || fullConversation.trim().isEmpty()) {
                        fullConversation = "Full conversation not available";
                    }

                    if (aiReport == null || aiReport.trim().isEmpty()) {
                        aiReport = "AI report not available";
                    }

                    String detailText =
                            "Language:\n" + language + "\n\n" +
                                    "Mode:\n" + mode + "\n\n" +
                                    "Status:\n" + status + "\n\n" +
                                    "Date:\n" + dateText + "\n\n" +
                                    "What user said:\n" + transcript + "\n\n" +
                                    "Full Conversation:\n" + fullConversation + "\n\n" +
                                    "AI Mistake Report:\n" + aiReport;

                    txtHistoryDetail.setText(detailText);
                })
                .addOnFailureListener(e -> {
                    txtHistoryDetail.setText("Could not load report. Please try again.");
                });
    }
    private void deleteFromAppHistory(String userEmail, String sessionId) {
        db.collection("users")
                .document(userEmail)
                .collection("conversation_reports")
                .document(sessionId)
                .update(
                        "userDeleted", true,
                        "deletedAt", FieldValue.serverTimestamp()
                )
                .addOnSuccessListener(unused -> {
                    Intent intent = new Intent(historydetailactivity.this, historyactivity.class);

                    if (filterLanguage != null && !filterLanguage.trim().isEmpty()) {
                        intent.putExtra("filterLanguage", filterLanguage);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    txtHistoryDetail.setText("Could not delete from app history. Try again.");
                });
    }
}