package com.example.alifastith_lab8;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class PostRecyclerAdapter extends RecyclerView.Adapter<PostRecyclerAdapter.PostViewHolder> {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference allPostsRef = database.getReference("Posts");
    private ChildEventListener postsRefListener;

    private List<String> keyList;
    private HashMap<String, PostModel> keyToPost;
    private SimpleDateFormat localDateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private Marker currentMarker = null;
    private PostItemClickListener postItemClickListener;
    private RecyclerView recyclerView;

    public PostRecyclerAdapter(PostItemClickListener postItemClickListener, RecyclerView recyclerView, List<String> keyList, HashMap<String, PostModel> keyToPost) {
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        this.postItemClickListener = postItemClickListener;
        this.recyclerView = recyclerView;
        this.keyList = keyList;
        this.keyToPost = keyToPost;

        postsRefListener = allPostsRef.addChildEventListener(new ChildEventListener() {
            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Log.d("EventListener", "onChildChanged invoked");
                final String postKey = snapshot.getKey();
                if (keyToPost.containsKey(postKey)) {
                    PostModel postModel = keyToPost.get(postKey);
                    int position = keyList.indexOf(postKey);
                    postModel.url = snapshot.child("url").getValue().toString();
                    postModel.description = snapshot.child("description").getValue().toString();
                    if (snapshot.hasChild("lastEditTimestamp"))
                        postModel.update = localDateFormat.format(new Date(Long.parseLong(snapshot.child("lastEditTimestamp").getValue().toString())));
                    postModel.marker.setTitle(postModel.description);
                    if (postModel.marker.equals(currentMarker))
                        currentMarker.showInfoWindow();
                    notifyItemChanged(position);
                    recyclerView.scrollToPosition(position);
                }
            }
            @Override public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { Log.d("EventListener", "onChildAdded invoked"); }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) { Log.d("EventListener", "onChildRemoved invoked"); }
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { Log.d("EventListener", "onChildMoved invoked"); }
            @Override public void onCancelled(@NonNull DatabaseError error) { Log.d("EventListener", "onCancelled invoked"); }
        });
    }

    public void setCurrentMarker(Marker marker) {
        unsetCurrentMarker();
        currentMarker = marker;
    }

    public void unsetCurrentMarker() {
        if (currentMarker != null) {
            currentMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_grey));
            currentMarker.hideInfoWindow();
            currentMarker = null;
        }
    }

    public boolean unsetIfCurrentMarker(Marker marker) {
        if (marker.equals(currentMarker)) {
            unsetCurrentMarker();
            return true;
        }
        return false;
    }

    @NonNull @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.post_card_view, parent, false);
        final PostViewHolder viewHolder = new PostViewHolder(view);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {

        final PostModel postModel = keyToPost.get(keyList.get(position));
        String uid = postModel.uid;
        String key = postModel.postKey;

        // Reset the holder's last edit date view
        holder.updateView.setText("");
        holder.updateView.setVisibility(View.GONE);

        // Set the non-changeable field
        String dateText = "Date Created: " + postModel.date;
        holder.dateView.setText(dateText);

        // Set profile image click handler
        if (!currentUser.getUid().equals(uid)) {
            holder.profileImage.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), ChatActivity.class);
                intent.putExtra("target_uid", uid);
                v.getContext().startActivity(intent);
            });
        }

        if (currentUser.getUid().equals(uid)) {
            holder.menuImage.setVisibility(View.VISIBLE);
            holder.menuImage.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
                MenuInflater inflater = popupMenu.getMenuInflater();
                inflater.inflate(R.menu.card_view_menu, popupMenu.getMenu());
                popupMenu.show();
                popupMenu.setOnMenuItemClickListener(item -> {
                    final int editActionId = R.id.edit_post;
                    final int deleteActionId = R.id.delete_post;
                    switch (item.getItemId()) {
                        case editActionId: // Edit action
                            if (postItemClickListener != null)
                                postItemClickListener.onPostEditClick(key, postModel.url);
                            return true;
                        case deleteActionId: // Delete action
                            if (postItemClickListener != null)
                                postItemClickListener.onPostDeleteClick(key, postModel.url);
                            return true;
                        default:
                            return false;
                    }
                });
            });
        } else holder.menuImage.setVisibility(View.GONE);

        if (holder.uRef != null && holder.uRefListener != null)
            holder.uRef.removeEventListener(holder.uRefListener);
        if (holder.imageRef != null && holder.imageRefListener != null)
            holder.imageRef.removeEventListener(holder.imageRefListener);
        if (holder.descRef != null && holder.descRefListener != null)
            holder.descRef.removeEventListener(holder.descRefListener);
        if (holder.likesRef != null && holder.likesRefListener != null)
            holder.likesRef.removeEventListener(holder.likesRefListener);
        if (holder.likesCountRef != null && holder.likesCountRefListener != null)
            holder.likesCountRef.removeEventListener(holder.likesCountRefListener);

        holder.uRef = database.getReference("Users").child(uid);
        holder.uRefListener = holder.uRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String nameText = "Name: " + snapshot.child("displayName").getValue().toString();
                String emailText = "Email: " + snapshot.child("emailAddress").getValue().toString();
                String phoneText = "Phone Number: " + snapshot.child("phoneNumber").getValue().toString();
                holder.nameView.setText(nameText);
                holder.emailView.setText(emailText);
                holder.phoneView.setText(phoneText);
                if (snapshot.hasChild("profilePicture")) {
                    Picasso.get().load(snapshot.child("profilePicture").getValue().toString())
                            .transform(new CircleTransform()).into(holder.profileImage);
                } else { // TODO: default image not working
                    Picasso.get().load(R.drawable.default_profile)
                            .transform(new CircleTransform()).into(holder.profileImage);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });

        holder.imageRef = database.getReference("Posts/" + key + "/url");
        holder.imageRefListener = holder.imageRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                StorageReference imagePathRef = FirebaseStorage.getInstance().getReference("Images/" + postModel.url);
                imagePathRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    Picasso.get().load(uri).into(holder.imageView);
                    if (postModel.update != null) {
                        String updateText = "Last Edited: " + postModel.update;
                        holder.updateView.setVisibility(View.VISIBLE);
                        holder.updateView.setText(updateText);
                    }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });

        holder.descRef = database.getReference("Posts/" +  key + "/description");
        holder.descRefListener = holder.descRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("PhotoPreview", "description changed!");
                holder.descriptionView.setText(postModel.description);
                if (postModel.update != null) {
                    String updateText = "Last Edited: " + postModel.update;
                    holder.updateView.setVisibility(View.VISIBLE);
                    holder.updateView.setText(updateText);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });

        holder.likesCountRef = database.getReference("Posts/" + key + "/likesCount");
        holder.likesCountRefListener = holder.likesCountRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.getValue() != null) {
                    String likesText = snapshot.getValue().toString() + " Likes";
                    holder.likesCount.setText(likesText);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });

        holder.likesRef = database.getReference("Posts/" + key + "/likes/" + currentUser.getUid());
        holder.likesRefListener = holder.likesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue().toString().equals("true"))
                    holder.likeButton.setImageDrawable(ContextCompat.getDrawable(holder.likeButton.getContext(), R.drawable.like_active));
                else
                    holder.likeButton.setImageDrawable(ContextCompat.getDrawable(holder.likeButton.getContext(), R.drawable.like_disabled));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });

        holder.likeButton.setOnClickListener(v -> database.getReference("Posts/" + key).runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Post post = currentData.getValue(Post.class);
                if (post == null)
                    return Transaction.success(currentData);
                if (post.likes.containsKey(currentUser.getUid())) { // Un-star the post and remove self from stars
                    post.likesCount = post.likesCount - 1;
                    post.likes.remove(currentUser.getUid());
                } else { // Star the post and add self to stars
                    post.likesCount = post.likesCount + 1;
                    post.likes.put(currentUser.getUid(), true);
                }
                // Set value and report transaction success
                currentData.setValue(post);
                return Transaction.success(currentData);
            }
            @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) { }
        }));

        holder.imageView.setOnClickListener(v -> {
            if (postItemClickListener != null)
                postItemClickListener.onPostImageClick(postModel.marker);
        });
    }

    public void removeListener() {
        if (allPostsRef != null && postsRefListener != null)
            allPostsRef.removeEventListener(postsRefListener);
    }

    @Override
    public int getItemCount() {
        return keyList.size();
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {

        public ImageView profileImage;
        public ImageView menuImage;
        public TextView nameView;
        public TextView emailView;
        public TextView phoneView;
        public TextView dateView;
        public TextView updateView;
        public TextView descriptionView;
        public ImageView imageView;
        public ImageView likeButton;
        public TextView likesCount;
        DatabaseReference uRef;
        ValueEventListener uRefListener;
        DatabaseReference imageRef;
        ValueEventListener imageRefListener;
        DatabaseReference descRef;
        ValueEventListener descRefListener;
        DatabaseReference likesCountRef;
        ValueEventListener likesCountRefListener;
        DatabaseReference likesRef;
        ValueEventListener likesRefListener;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            menuImage = itemView.findViewById(R.id.card_menu_image);
            nameView = itemView.findViewById(R.id.name_view);
            emailView = itemView.findViewById(R.id.email_view);
            phoneView = itemView.findViewById(R.id.phone_view);
            dateView = itemView.findViewById(R.id.date_view);
            updateView = itemView.findViewById(R.id.update_view);
            descriptionView = itemView.findViewById(R.id.description);
            imageView = itemView.findViewById(R.id.post_img);
            likeButton = itemView.findViewById(R.id.like_btn);
            likesCount = itemView.findViewById(R.id.like_count);
        }
    }
}
