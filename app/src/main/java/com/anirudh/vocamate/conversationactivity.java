package com.anirudh.vocamate;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class conversationactivity extends AppCompatActivity {

    String selectedLanguage, selectedMode;

    TextView txtConversationInfo, txtStatus;
    Button btnDone, btnMic;

    private static final int MIC_PERMISSION_CODE = 100;

    SpeechRecognizer speechRecognizer;
    Intent speechIntent;

    String userSpokenText = "";
    StringBuilder userTranscript = new StringBuilder();
    StringBuilder aiConversationMemory = new StringBuilder();

    TextToSpeech textToSpeech;
    boolean isTtsReady = false;
    boolean autoListenEnabled = true;

    Handler aiDelayHandler = new Handler(Looper.getMainLooper());
    Runnable aiReplyRunnable;

    FirebaseFirestore db;
    FirebaseAuth firebaseAuth;
    FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversationactivity);

        db = FirebaseFirestore.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        selectedLanguage = getIntent().getStringExtra("language");
        selectedMode = getIntent().getStringExtra("mode");

        txtConversationInfo = findViewById(R.id.txtConversationInfo);
        txtStatus = findViewById(R.id.txtStatus);
        btnDone = findViewById(R.id.btnDone);
        btnMic = findViewById(R.id.btnMic);

        txtConversationInfo.setText(
                "Language: " + selectedLanguage +
                        "\nMode: " + selectedMode
        );

        setupSpeechRecognizer();
        setupTextToSpeech();

        btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(conversationactivity.this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                ActivityCompat.requestPermissions(
                        conversationactivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MIC_PERMISSION_CODE
                );
            }
        });

        btnDone.setOnClickListener(v -> {
            autoListenEnabled = false;

            if (aiReplyRunnable != null) {
                aiDelayHandler.removeCallbacks(aiReplyRunnable);
            }

            if (speechRecognizer != null) {
                speechRecognizer.cancel();
            }

            if (textToSpeech != null) {
                textToSpeech.stop();
            }

            if (currentUser == null || currentUser.getEmail() == null) {
                Toast.makeText(conversationactivity.this, "Login required to save report", Toast.LENGTH_LONG).show();

                Intent intent = new Intent(conversationactivity.this, reportactivity.class);
                intent.putExtra("language", selectedLanguage);
                intent.putExtra("mode", selectedMode);
                intent.putExtra("transcript", userTranscript.toString());
                startActivity(intent);
                return;
            }

            String userEmail = currentUser.getEmail();

            Map<String, Object> userData = new HashMap<>();
            userData.put("email", userEmail);
            userData.put("userId", currentUser.getUid());
            userData.put("lastActiveAt", FieldValue.serverTimestamp());

            db.collection("users")
                    .document(userEmail)
                    .set(userData, SetOptions.merge());

            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("userId", currentUser.getUid());
            sessionData.put("userEmail", userEmail);
            sessionData.put("language", selectedLanguage);
            sessionData.put("mode", selectedMode);
            sessionData.put("transcript", userTranscript.toString());
            sessionData.put("fullConversation", aiConversationMemory.toString());
            sessionData.put("createdAt", FieldValue.serverTimestamp());
            sessionData.put("reportStatus", "pending");

            db.collection("users")
                    .document(userEmail)
                    .collection("conversation_reports")
                    .add(sessionData)
                    .addOnSuccessListener(documentReference -> {
                        Intent intent = new Intent(conversationactivity.this, reportactivity.class);
                        intent.putExtra("language", selectedLanguage);
                        intent.putExtra("mode", selectedMode);
                        intent.putExtra("transcript", userTranscript.toString());
                        intent.putExtra("sessionId", documentReference.getId());
                        intent.putExtra("userEmail", userEmail);
                        startActivity(intent);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(conversationactivity.this,
                                "Firestore save failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
        });
    }

    private void scheduleAiReply(String userText) {
        if (aiReplyRunnable != null) {
            aiDelayHandler.removeCallbacks(aiReplyRunnable);
        }

        aiReplyRunnable = () -> sendUserSpeechToAi(userText);

        txtStatus.setText("You said:\n" + userText + "\n\nAssistant is thinking...");
        aiDelayHandler.postDelayed(aiReplyRunnable, 500);
    }

    private void sendUserSpeechToAi(String userText) {
        txtStatus.setText("Assistant is thinking...");

        GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-2.5-flash");

        GenerativeModelFutures model = GenerativeModelFutures.from(ai);

        String promptText =
                "You are a real-time AI voice speaking practice assistant.\n" +
                        "Language: " + selectedLanguage + "\n" +
                        "Mode: " + selectedMode + "\n\n" +

                        "Continue the same conversation using the conversation history.\n" +
                        "Understand what the user wants from the latest message.\n" +
                        "If the user asks a simple question, reply shortly.\n" +
                        "If the user asks for a story, explanation, or detailed content, give a complete useful reply.\n" +
                        "Do not always make replies short. Do not always make replies long.\n\n" +

                        "Tone rules:\n" +
                        "Friend mode: casual, friendly, relaxed.\n" +
                        "HR Interview mode: professional, serious, slightly strict.\n" +
                        "Officer / Manager mode: formal, respectful, confident.\n" +
                        "Parent mode: caring, patient, supportive.\n" +
                        "Partner mode: soft, cute, warm, loving, but respectful.\n\n" +

                        "Do not mention Gemini, Firebase, API, or backend.\n\n" +

                        "Conversation history:\n" +
                        aiConversationMemory.toString() +
                        "\nLatest user message:\n" +
                        userText;

        Content prompt = new Content.Builder()
                .addText(promptText)
                .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(prompt);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String aiReply = result.getText();

                if (aiReply == null || aiReply.trim().isEmpty()) {
                    txtStatus.setText("Assistant gave no reply. Try again.");
                    return;
                }

                txtStatus.setText("Assistant:\n" + aiReply);

                aiConversationMemory.append("User: ").append(userText).append("\n");
                aiConversationMemory.append("Assistant: ").append(aiReply).append("\n\n");

                if (isTtsReady) {
                    applyAssistantVoiceStyle();
                    textToSpeech.speak(aiReply, TextToSpeech.QUEUE_FLUSH, null, "ai_reply");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                txtStatus.setText("Assistant is busy right now. Please try again after some time.");
            }
        }, conversationactivity.this::runOnUiThread);
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(getTtsLocale());

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    txtStatus.setText("Voice not supported for this language on this phone");
                    isTtsReady = false;
                } else {
                    isTtsReady = true;
                    txtStatus.setText("Voice output ready");
                }

                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(() -> txtStatus.setText("Assistant is speaking..."));
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(() -> {
                            txtStatus.setText("Listening again...");
                            if (autoListenEnabled) {
                                startVoiceInput();
                            }
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(() -> txtStatus.setText("Voice output error"));
                    }
                });

            } else {
                txtStatus.setText("Text to Speech setup failed");
                isTtsReady = false;
            }
        });
    }

    private Locale getTtsLocale() {
        if ("Hindi".equals(selectedLanguage)) {
            return new Locale("hi", "IN");
        } else if ("Telugu".equals(selectedLanguage)) {
            return new Locale("te", "IN");
        } else if ("Tamil".equals(selectedLanguage)) {
            return new Locale("ta", "IN");
        } else if ("Kannada".equals(selectedLanguage)) {
            return new Locale("kn", "IN");
        } else if ("French".equals(selectedLanguage)) {
            return Locale.FRANCE;
        } else if ("Spanish".equals(selectedLanguage)) {
            return new Locale("es", "ES");
        } else if ("German".equals(selectedLanguage)) {
            return Locale.GERMANY;
        } else if ("Malayalam".equals(selectedLanguage)) {
            return new Locale("ml", "IN");
        } else if ("Japanese".equals(selectedLanguage)) {
            return Locale.JAPAN;
        } else {
            return Locale.US;
        }
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                txtStatus.setText("Listening...");
            }

            @Override
            public void onBeginningOfSpeech() {
                txtStatus.setText("You are speaking...");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                txtStatus.setText("Processing speech...");
            }

            @Override
            public void onError(int error) {
                txtStatus.setText("Could not understand. Try again.");
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {
                    userSpokenText = matches.get(0);
                    userTranscript.append(userSpokenText).append("\n");
                    scheduleAiReply(userSpokenText);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
    }

    private void startVoiceInput() {
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getSpeechLanguageCode());
        speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now");

        speechRecognizer.startListening(speechIntent);
    }

    private String getSpeechLanguageCode() {
        if ("Hindi".equals(selectedLanguage)) {
            return "hi-IN";
        } else if ("Telugu".equals(selectedLanguage)) {
            return "te-IN";
        } else if ("Tamil".equals(selectedLanguage)) {
            return "ta-IN";
        } else if ("Kannada".equals(selectedLanguage)) {
            return "kn-IN";
        } else if ("French".equals(selectedLanguage)) {
            return "fr-FR";
        } else if ("Spanish".equals(selectedLanguage)) {
            return "es-ES";
        } else if ("German".equals(selectedLanguage)) {
            return "de-DE";
        } else if ("Malayalam".equals(selectedLanguage)) {
            return "ml-IN";
        } else if ("Japanese".equals(selectedLanguage)) {
            return "ja-JP";
        } else {
            return "en-US";
        }
    }

    private void applyAssistantVoiceStyle() {
        if (textToSpeech == null) {
            return;
        }

        textToSpeech.setPitch(1.0f);
        textToSpeech.setSpeechRate(1.0f);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MIC_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission allowed", Toast.LENGTH_SHORT).show();
                startVoiceInput();
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        aiDelayHandler.removeCallbacksAndMessages(null);

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}