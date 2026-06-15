package com.anirudh.vocamate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class reportactivity extends AppCompatActivity {

    String selectedLanguage, selectedMode, transcript, sessionId, userEmail;

    TextView txtReportInfo, txtUserSaid, txtAiReport;
    Button btnNewConversation;

    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reportactivity);

        db = FirebaseFirestore.getInstance();

        selectedLanguage = getIntent().getStringExtra("language");
        selectedMode = getIntent().getStringExtra("mode");
        transcript = getIntent().getStringExtra("transcript");
        sessionId = getIntent().getStringExtra("sessionId");
        userEmail = getIntent().getStringExtra("userEmail");

        txtReportInfo = findViewById(R.id.txtReportInfo);
        txtUserSaid = findViewById(R.id.txtUserSaid);
        txtAiReport = findViewById(R.id.txtAiReport);
        btnNewConversation = findViewById(R.id.btnNewConversation);

        txtReportInfo.setText("Language: " + selectedLanguage + "\nMode: " + selectedMode);

        btnNewConversation.setOnClickListener(v -> {
            Intent intent = new Intent(reportactivity.this, languageactivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        if (transcript == null || transcript.trim().isEmpty()) {
            txtUserSaid.setText("What user said:\nNo speech recorded yet");
            txtAiReport.setText("No report available because no speech was recorded.");
            saveNoSpeechStatusToFirestore();
        } else {
            txtUserSaid.setText("What user said:\n" + transcript);
            generateMistakeReport();
        }
    }

    private void generateMistakeReport() {
        txtAiReport.setText("Generating mistake report...");

        GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-2.5-flash");

        GenerativeModelFutures model = GenerativeModelFutures.from(ai);

        String prompt =
                "You are VocaMate, a friendly speaking practice coach.\n\n" +

                        "The user practiced this language: " + selectedLanguage + "\n" +
                        "The user practiced this mode: " + selectedMode + "\n\n" +

                        "User speech transcript:\n" +
                        transcript + "\n\n" +

                        "Important rules:\n" +
                        "1. Write all explanations in simple English only.\n" +
                        "2. Do not explain mistakes in " + selectedLanguage + ".\n" +
                        "3. The corrected sentence should be in the practiced language.\n" +
                        "4. If the practiced language is not English, also give pronunciation using simple English letters.\n" +
                        "5. Keep the report short, clear, and beginner friendly.\n" +
                        "6. Do not mention Gemini, Firebase, API, backend, or model.\n\n" +

                        "Generate the report exactly in this format:\n\n" +

                        "Original sentence:\n" +
                        "[Write what the user said]\n\n" +

                        "Better sentence:\n" +
                        "[Write the improved sentence in " + selectedLanguage + "]\n\n" +

                        "Pronunciation:\n" +
                        "[If language is not English, write simple English-letter pronunciation. If English, write Not needed.]\n\n" +

                        "Major mistakes:\n" +
                        "[Explain big mistakes in simple English]\n\n" +

                        "Minor mistakes:\n" +
                        "[Explain small mistakes in simple English]\n\n" +

                        "Grammar score:\n" +
                        "[Give score out of 10]\n\n" +

                        "Fluency score:\n" +
                        "[Give score out of 10]\n\n" +

                        "Simple advice:\n" +
                        "[Give simple improvement advice in English]";

        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String report = result.getText();

                if (report == null || report.trim().isEmpty()) {
                    txtAiReport.setText("Report came empty. Try again.");
                    saveReportFailureToFirestore();
                } else {
                    txtAiReport.setText(report);
                    saveAiReportToFirestore(report);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                txtAiReport.setText("Report could not be generated right now. Please try again after some time.");
                saveReportFailureToFirestore();
            }
        }, reportactivity.this::runOnUiThread);
    }

    private void saveAiReportToFirestore(String report) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (userEmail == null || userEmail.trim().isEmpty()) {
            return;
        }

        db.collection("users")
                .document(userEmail)
                .collection("conversation_reports")
                .document(sessionId)
                .update(
                        "aiReport", report,
                        "reportStatus", "success",
                        "reportGeneratedAt", FieldValue.serverTimestamp()
                );
    }

    private void saveReportFailureToFirestore() {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (userEmail == null || userEmail.trim().isEmpty()) {
            return;
        }

        db.collection("users")
                .document(userEmail)
                .collection("conversation_reports")
                .document(sessionId)
                .update(
                        "reportStatus", "failed",
                        "reportFailedAt", FieldValue.serverTimestamp()
                );
    }

    private void saveNoSpeechStatusToFirestore() {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (userEmail == null || userEmail.trim().isEmpty()) {
            return;
        }

        db.collection("users")
                .document(userEmail)
                .collection("conversation_reports")
                .document(sessionId)
                .update(
                        "reportStatus", "no_speech",
                        "reportUpdatedAt", FieldValue.serverTimestamp()
                );
    }
}