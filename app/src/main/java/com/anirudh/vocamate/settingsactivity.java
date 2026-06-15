package com.anirudh.vocamate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class settingsactivity extends AppCompatActivity {

    TextView txtAccountInfo;
    Button btnLogout;

    FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settingsactivity);

        firebaseAuth = FirebaseAuth.getInstance();

        txtAccountInfo = findViewById(R.id.txtAccountInfo);
        btnLogout = findViewById(R.id.btnLogout);

        FirebaseUser user = firebaseAuth.getCurrentUser();

        if (user != null) {
            txtAccountInfo.setText("Account:\n" + user.getEmail());
        } else {
            txtAccountInfo.setText("Account:\nNot logged in");
        }

        btnLogout.setOnClickListener(v -> {
            firebaseAuth.signOut();

            GoogleSignInOptions googleSignInOptions =
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build();

            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions);

            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent intent = new Intent(settingsactivity.this, loginactivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
    }
}