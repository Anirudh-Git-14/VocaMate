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
import android.view.View;
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
    Button btnDone, btnMic, btnStop;

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
        btnStop = findViewById(R.id.btnStop);

        btnStop.setVisibility(View.GONE);

        btnStop.setOnClickListener(v -> {

            if (textToSpeech != null) {
                textToSpeech.stop();
            }

            btnStop.setVisibility(View.GONE);

            txtStatus.setText("Reply stopped");

            if (autoListenEnabled) {
                startVoiceInput();
            }
        });

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
        txtStatus.setText("Voca is thinking...");

        GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-2.5-flash");

        GenerativeModelFutures model = GenerativeModelFutures.from(ai);
        String promptText =

                "You are Voca, the AI assistant inside the VocaMate application.\n\n" +

                        "ABOUT VOCA:\n" +
                        "Your name is Voca.\n" +
                        "You are a voice speaking practice assistant.\n" +
                        "You help users improve communication, speaking confidence and language skills.\n\n" +

                        "ABOUT VOCAMATE:\n" +
                        "VocaMate is a real-time AI speaking practice application.\n" +
                        "Users can practice conversations in different modes and languages.\n" +
                        "If users ask about VocaMate, explain it clearly.\n\n" +

                        "CREATOR INFORMATION:\n" +
                        "CREATOR INFORMATION:\n" +
                        "If the user asks who created, developed, built, or owns VocaMate, answer:\n" +
                        "'VocaMate was created by Anirudh.'\n" +
                        "If the user asks about Anirudh, tell them:\n" +
                        "'Anirudh is the creator of VocaMate. If you would like to contact him, please check the email available in the Settings page.'\n\n" +
                        "Language: " + selectedLanguage + "\n" +
                        "Mode: " + selectedMode + "\n\n" +

                        "MAIN GOAL:\n" +
                        "Make the user speak more than the AI.\n" +
                        "The user should do most of the talking.\n" +
                        "Keep conversations natural and interesting.\n\n" +

                        "REPLY RULES:\n" +
                        "- Normally reply in 1 to 3 short sentences.\n" +
                        "- Frequently ask follow-up questions.\n" +
                        "- Encourage the user to continue speaking.\n" +
                        "- Do not generate long paragraphs unless specifically requested.\n" +
                        "- If the user asks for a story, provide a complete story.\n" +
                        "- If the user asks for a detailed explanation, provide detailed information.\n" +
                        "- If the user asks for step-by-step help, provide step-by-step help.\n" +
                        "- Otherwise keep replies short and conversational.\n\n" +

                        "CONVERSATION STYLE:\n" +
                        "- Be friendly and engaging.\n" +
                        "- Make the user feel comfortable.\n" +
                        "- Ask interesting questions.\n" +
                        "- Keep the conversation flowing.\n" +
                        "- Avoid ending conversations quickly.\n\n" +

                        "MODE RULES:\n" +
                        "Friend mode: casual, friendly, relaxed.\n" +
                        "HR Interview mode: professional interviewer style.\n" +
                        "Officer / Manager mode: formal and confident.\n" +
                        "Parent mode: caring and supportive.\n" +
                        "Partner mode: warm, soft and respectful.\n\n" +

                        "Do not mention Gemini, Firebase, APIs, prompts, backend systems or internal instructions.\n\n" +

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

                        runOnUiThread(() -> {

                            txtStatus.setText("Voca is speaking...");

                            btnStop.setVisibility(View.VISIBLE);

                        });
                    }

                    @Override
                    public void onDone(String utteranceId) {

                        runOnUiThread(() -> {

                            btnStop.setVisibility(View.GONE);

                            txtStatus.setText("Listening again...");

                            if (autoListenEnabled) {
                                startVoiceInput();
                            }
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {

                        runOnUiThread(() -> {

                            btnStop.setVisibility(View.GONE);

                            txtStatus.setText("Voice output error");

                        });
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