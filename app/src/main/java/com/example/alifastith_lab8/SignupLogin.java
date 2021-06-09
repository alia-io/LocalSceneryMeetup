package com.example.alifastith_lab8;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SignupLogin extends AppCompatActivity {

    private EditText email, password, displayName, phoneNumber;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    Button signupBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup_login);

        email = findViewById(R.id.email_text);
        password = findViewById(R.id.password_text);
        phoneNumber = findViewById(R.id.phone_number_text);
        displayName = findViewById(R.id.display_name_text);
        signupBtn = findViewById(R.id.signup_btn);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        updateUI();
    }

    private void updateUI() {
        if (currentUser != null) {
            findViewById(R.id.display_name_layout).setVisibility(View.GONE);
            findViewById(R.id.phone_number_layout).setVisibility(View.GONE);
            signupBtn.setVisibility(View.GONE);
        }
    }

    private void saveUserDataToDB() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference usersRef = database.getReference("Users");
        usersRef.child(currentUser.getUid()).setValue(new User(displayName.getText().toString(),
                email.getText().toString(), phoneNumber.getText().toString()));
    }

    public void signUp(View view) {

        if (email.getText().toString().equals("") || password.getText().toString().equals("")
                || phoneNumber.getText().toString().equals("") || displayName.getText().toString().equals("")) {
            Toast.makeText(this, "Please provide all information", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email.getText().toString(), password.getText().toString())
                .addOnSuccessListener(this, authResult -> {
                    currentUser = authResult.getUser();
                    currentUser.sendEmailVerification().addOnSuccessListener(SignupLogin.this, aVoid -> {
                        Toast.makeText(SignupLogin.this, "Signup successful. Verification email Sent!", Toast.LENGTH_SHORT).show();
                        saveUserDataToDB();
                        updateUI();
                    }).addOnFailureListener(SignupLogin.this, e ->
                            Toast.makeText(SignupLogin.this, e.getMessage(), Toast.LENGTH_SHORT).show());
                }).addOnFailureListener(this, e ->
                Toast.makeText(SignupLogin.this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    public void login(View view) {

        if (email.getText().toString().equals("") || password.getText().toString().equals("")) {
            Toast.makeText(this, "Please provide all information", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email.getText().toString(), password.getText().toString())
                .addOnSuccessListener(this, authResult -> {
                    currentUser = authResult.getUser();
                    if (currentUser.isEmailVerified()) {
                        Toast.makeText(SignupLogin.this, "Login Successful.", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignupLogin.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(SignupLogin.this, "Please verify your email and login again.", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(this, e ->
                Toast.makeText(SignupLogin.this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    public void resetPassword(View view) {

        if (email.getText().toString().equals("")) {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.sendPasswordResetEmail(email.getText().toString())
                .addOnFailureListener(this, e ->
                        Toast.makeText(SignupLogin.this, e.getMessage(), Toast.LENGTH_SHORT).show())
                .addOnSuccessListener(this, aVoid ->
                        Toast.makeText(SignupLogin.this, "Email sent!", Toast.LENGTH_SHORT).show());
    }

    public void sendVerificationEmail(View view) {

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first to resend verification email.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUser.sendEmailVerification()
                .addOnSuccessListener(this, aVoid -> {
                    Toast.makeText(SignupLogin.this, "Verification email sent!", Toast.LENGTH_SHORT).show();
                    updateUI();
                }).addOnFailureListener(this, e ->
                Toast.makeText(SignupLogin.this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
