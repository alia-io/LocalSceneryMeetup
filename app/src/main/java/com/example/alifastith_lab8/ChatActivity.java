package com.example.alifastith_lab8;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

public class ChatActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        ImageView imageView = findViewById(R.id.profile_image);
        TextView targetNameView = findViewById(R.id.profile_name);

        String targetUid = getIntent().getStringExtra("target_uid");
        DatabaseReference targetRef = database.getReference("Users/" + targetUid);

        targetRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild("profilePicture")) {
                    Picasso.get().load(snapshot.child("profilePicture").getValue().toString())
                            .transform(new CircleTransform()).into(imageView);
                }
                targetNameView.setText(snapshot.child("displayName").getValue().toString());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    public void send(View view) {

    }
}