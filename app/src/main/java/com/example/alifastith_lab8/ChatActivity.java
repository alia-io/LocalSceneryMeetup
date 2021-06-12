package com.example.alifastith_lab8;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

public class ChatActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();

    private MessageRecyclerAdapter messageRecyclerAdapter;
    private RecyclerView recyclerView;
    private EditText newMessageText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        ImageView imageView = findViewById(R.id.profile_image);
        TextView targetNameView = findViewById(R.id.profile_name);
        newMessageText = findViewById(R.id.new_message_text);

        String selfUid = currentUser.getUid();
        String targetUid = getIntent().getStringExtra("target_uid");

        // Get other user's profile picture and display name
        database.getReference("Users/" + targetUid).addListenerForSingleValueEvent(new ValueEventListener() {
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

        recyclerView = findViewById(R.id.recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        layoutManager.scrollToPosition(0);
        recyclerView.setLayoutManager(layoutManager);
        messageRecyclerAdapter = new MessageRecyclerAdapter(recyclerView, targetUid);
        recyclerView.setAdapter(messageRecyclerAdapter);
    }

    public void send(View view) {
        String messageText = newMessageText.getText().toString();
        if (messageText.length() <= 0) {
            Toast.makeText(this, "Please write a message to send", Toast.LENGTH_SHORT).show();
            return;
        }
        messageRecyclerAdapter.onSendNewMessage(messageText);
        newMessageText.setText("");
    }

    @Override
    public void onBackPressed() { finish(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        messageRecyclerAdapter.removeListener();
    }
}