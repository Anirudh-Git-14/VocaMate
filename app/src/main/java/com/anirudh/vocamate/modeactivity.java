package com.anirudh.vocamate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class modeactivity extends AppCompatActivity {

    String selectedLanguage;

    TextView txtSelectedLanguage;
    Button btnLanguageHistory;

    Button btnFriend, btnHR, btnOfficer, btnParent, btnPartner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.modeactivity);

        selectedLanguage = getIntent().getStringExtra("language");

        txtSelectedLanguage = findViewById(R.id.txtSelectedLanguage);


        txtSelectedLanguage.setText("Language: " + selectedLanguage);


        btnFriend = findViewById(R.id.btnFriend);
        btnHR = findViewById(R.id.btnHR);
        btnOfficer = findViewById(R.id.btnOfficer);
        btnParent = findViewById(R.id.btnParent);
        btnPartner = findViewById(R.id.btnPartner);
        btnLanguageHistory = findViewById(R.id.btnLanguageHistory);
        btnLanguageHistory.setText("View " + selectedLanguage + " History");

        btnLanguageHistory.setOnClickListener(v -> {
            Intent intent = new Intent(modeactivity.this, historyactivity.class);
            intent.putExtra("filterLanguage", selectedLanguage);
            startActivity(intent);
        });


        btnFriend.setOnClickListener(v -> openConversationScreen("Friend"));
        btnHR.setOnClickListener(v -> openConversationScreen("HR Interview"));
        btnOfficer.setOnClickListener(v -> openConversationScreen("Officer / Manager"));
        btnParent.setOnClickListener(v -> openConversationScreen("Parent"));
        btnPartner.setOnClickListener(v -> openConversationScreen("Partner"));
    }
    private void openConversationScreen(String mode) {
        Intent intent = new Intent(modeactivity.this, conversationactivity.class);
        intent.putExtra("language", selectedLanguage);
        intent.putExtra("mode", mode);
        startActivity(intent);
    }

}