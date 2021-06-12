package com.example.alifastith_lab8;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MessageRecyclerAdapter extends RecyclerView.Adapter<MessageRecyclerAdapter.MessageViewHolder> {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference allUsersRef = database.getReference("/Users");
    private DatabaseReference currentChatRef;
    private ChildEventListener currentChatListener;

    private SimpleDateFormat localDateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");

    private String selfUid;
    private String targetUid;
    private List<MessageModel> messageList;
    private String lastDate = null;
    private RecyclerView recyclerView;

    public MessageRecyclerAdapter(final RecyclerView recyclerView, final String targetUid) {

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        selfUid = currentUser.getUid();
        this.targetUid = targetUid;
        messageList = new ArrayList<>();
        this.recyclerView = recyclerView;

        Log.d("User", "selfUid = " + selfUid);
        Log.d("User", "targetUid = " + targetUid);

        // Get the chat UUID & set currentChatListener database reference
        allUsersRef.child(selfUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User selfU = snapshot.getValue(User.class);
                Log.d("User", "chats.size() = " + selfU.chats.size());
                Log.d("User", "selfU.displayName = " + selfU.displayName);
                if (snapshot.hasChild("chats") && selfU.chats.containsKey(targetUid)) { // Chat already exists
                    String chatUUID = selfU.chats.get(targetUid);
                    currentChatRef = database.getReference("Chats/" + chatUUID);
                    setChatListener();
                } else { // Chat doesn't exist - make a new chat UUID
                    allUsersRef.runTransaction(new Transaction.Handler() {
                        @NonNull @Override
                        public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                            Log.d("User", "currentData = " + currentData);
                            User selfUser = currentData.child(selfUid).getValue(User.class);
                            User targetUser = currentData.child(targetUid).getValue(User.class);
                            if (selfUser == null || targetUser == null)
                                return Transaction.success(currentData);
                            Log.d("User", "selfUser = " + selfUser);
                            Log.d("User", "targetUser = " + targetUser);
                            if (currentData.hasChild("chats") && selfUser.chats.containsKey(targetUid)) { // Make sure the other user didn't add chat first
                                String chatUUID = selfUser.chats.get(targetUid);
                                currentChatRef = database.getReference("Chats/" + chatUUID);
                            } else {
                                final String newChatUUID = UUID.randomUUID().toString();
                                selfUser.chats.put(targetUid, newChatUUID);
                                targetUser.chats.put(selfUid, newChatUUID);
                                currentData.child(selfUid).setValue(selfUser); // Push new chat to current user chats list
                                currentData.child(targetUid).setValue(targetUser); // Push new chat to target user chats list
                                currentChatRef = database.getReference("Chats/" + newChatUUID);
                            }
                            setChatListener();
                            return Transaction.success(currentData);
                        }
                        @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) { }
                    });


                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void setChatListener() {
        currentChatListener = currentChatRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                MessageModel messageModel = new MessageModel(snapshot.getKey(), snapshot.child("sender").getValue().toString(),
                        snapshot.child("receiver").getValue().toString(), snapshot.child("message").getValue().toString(),
                        localDateFormat.format(new Date(Long.parseLong(snapshot.child("timestamp").getValue().toString()))));
                messageList.add(messageModel);
                notifyItemInserted(messageList.size() - 1);
                recyclerView.scrollToPosition(messageList.size() - 1);
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) { }
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    @NonNull @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_card_view, parent, false);
        final MessageViewHolder viewHolder = new MessageViewHolder(view);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {

        MessageModel messageModel = messageList.get(position);
        String sender = messageModel.sender;
        String receiver = messageModel.receiver;

        // Show date if new
        if (lastDate == null || !lastDate.equals(messageModel.date)) {
            holder.dateView.setText(messageModel.date);
            holder.dateView.setVisibility(View.VISIBLE);
            lastDate = messageModel.date;
        } else holder.dateView.setVisibility(View.GONE);

        holder.messageView.setText(messageModel.message);
        holder.timeView.setText(messageModel.time);

        if (sender.equals(selfUid) && receiver.equals(targetUid)) { // Sent message
            holder.messageLayout.setBackgroundResource(R.drawable.sent_message_background);
            holder.constraintSet.clear(holder.messageLayoutId, ConstraintSet.LEFT);
            holder.constraintSet.connect(holder.messageLayoutId, ConstraintSet.RIGHT, holder.parentLayoutId, ConstraintSet.RIGHT);
        } else if (sender.equals(targetUid) && receiver.equals(selfUid)) { // Received message
            holder.messageLayout.setBackgroundResource(R.drawable.received_message_background);
            holder.constraintSet.clear(holder.messageLayoutId, ConstraintSet.RIGHT);
            holder.constraintSet.connect(holder.messageLayoutId, ConstraintSet.LEFT, holder.parentLayoutId, ConstraintSet.LEFT);
        }
    }

    public void removeListener() {
        if (currentChatRef != null && currentChatListener != null)
            currentChatRef.removeEventListener(currentChatListener);
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public void onSendNewMessage(String messageText) {
        Message newMessage = new Message(selfUid, targetUid, messageText);




        currentChatRef.push().setValue(newMessage);

    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {

        public ConstraintSet constraintSet;
        public ConstraintLayout parentLayout;
        public LinearLayout messageLayout;
        public int parentLayoutId;
        public int messageLayoutId;
        public TextView messageView;
        public TextView dateView;
        public TextView timeView;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            parentLayoutId = R.id.message_layout;
            messageLayoutId = R.id.message_body;
            parentLayout = itemView.findViewById(parentLayoutId);
            messageLayout = itemView.findViewById(messageLayoutId);
            messageView = itemView.findViewById(R.id.message_text);
            dateView = itemView.findViewById(R.id.message_date);
            timeView = itemView.findViewById(R.id.message_time);
            constraintSet = new ConstraintSet();
            constraintSet.clone(parentLayout);
        }
    }
}
