package com.anirudh.vocamate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.content.Intent;
import android.widget.Button;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public class languageactivity extends AppCompatActivity {

    Button btnEnglish, btnHindi, btnTelugu;
    Button btnTamil, btnKannada, btnFrench, btnSpanish, btnGerman, btnMalayalam, btnJapanese;
    Button btnAllHistory;
    ImageButton btnSettings;
    FirebaseAuth firebaseAuth;


    FirebaseUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.languageactivity);
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            Intent intent = new Intent(languageactivity.this, loginactivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return;
        }
        btnSettings = findViewById(R.id.btnSettings);

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(languageactivity.this, settingsactivity.class);
            startActivity(intent);
        });

        btnEnglish = findViewById(R.id.btnEnglish);
        btnHindi = findViewById(R.id.btnHindi);
        btnTelugu = findViewById(R.id.btnTelugu);
        btnTamil = findViewById(R.id.btnTamil);
        btnKannada = findViewById(R.id.btnKannada);
        btnFrench = findViewById(R.id.btnFrench);
        btnSpanish = findViewById(R.id.btnSpanish);
        btnGerman = findViewById(R.id.btnGerman);
        btnMalayalam = findViewById(R.id.btnMalayalam);
        btnJapanese = findViewById(R.id.btnJapanese);

        btnEnglish.setOnClickListener(v -> openModeScreen("English"));
        btnHindi.setOnClickListener(v -> openModeScreen("Hindi"));
        btnTelugu.setOnClickListener(v -> openModeScreen("Telugu"));
        btnTamil.setOnClickListener(v -> openModeScreen("Tamil"));
        btnKannada.setOnClickListener(v -> openModeScreen("Kannada"));
        btnFrench.setOnClickListener(v -> openModeScreen("French"));
        btnSpanish.setOnClickListener(v -> openModeScreen("Spanish"));
        btnGerman.setOnClickListener(v -> openModeScreen("German"));
        btnMalayalam.setOnClickListener(v -> openModeScreen("Malayalam"));
        btnJapanese.setOnClickListener(v -> openModeScreen("Japanese"));
        btnAllHistory = findViewById(R.id.btnAllHistory);

        btnAllHistory.setOnClickListener(v -> {
            Intent intent = new Intent(languageactivity.this, historyactivity.class);
            startActivity(intent);
        });
    }

    private void openModeScreen(String language) {
        Intent intent = new Intent(languageactivity.this, modeactivity.class);
        intent.putExtra("language", language);
        startActivity(intent);
    }
}