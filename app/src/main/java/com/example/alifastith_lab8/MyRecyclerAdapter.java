package com.example.alifastith_lab8;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
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

public class MyRecyclerAdapter extends RecyclerView.Adapter<MyRecyclerAdapter.MyViewHolder> {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference allPostsRef = database.getReference("Posts");
    private ChildEventListener postsRefListener; // TODO: move to HomeActivity class? Also might need it here to update profile images...

    private List<String> keyList;
    private HashMap<String, PostModel> keyToPost;
    private SimpleDateFormat localDateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private Marker currentMarker = null;
    private ItemClickListener itemClickListener;
    private RecyclerView recyclerView;

    // TODO: probably move most of this functionality to HomeActivity class
    /*public MyRecyclerAdapter(RecyclerView recyclerView, ItemClickListener itemClickListener, List<String> keyList, HashMap<String, PostModel> keyToPost) {

        postsList = new ArrayList<>();
        this.recyclerView = recyclerView;
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        postsRefListener = allPostsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String update = null;
                if (snapshot.hasChild("lastEditTimestamp"))
                    update = localDateFormat.format(new Date(Long.parseLong(snapshot.child("lastEditTimestamp").getValue().toString())));
                PostModel postModel = new PostModel(snapshot.getKey(),
                        snapshot.child("uid").getValue().toString(),
                        snapshot.child("description").getValue().toString(),
                        snapshot.child("url").getValue().toString(),
                        localDateFormat.format(new Date(Long.parseLong(snapshot.child("timestamp").getValue().toString()))),
                        update);
                postsList.add(postModel);
                notifyItemInserted(postsList.size() - 1);
                recyclerView.scrollToPosition(postsList.size() - 1);
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                for (int i = 0; i < postsList.size(); i++) {
                    if (postsList.get(i).postKey.equals(snapshot.getKey())) {
                        String update = null;
                        if (snapshot.hasChild("lastEditTimestamp"))
                            update = localDateFormat.format(new Date(Long.parseLong(snapshot.child("lastEditTimestamp").getValue().toString())));
                        PostModel userModel = new PostModel(snapshot.getKey(),
                                snapshot.child("uid").getValue().toString(),
                                snapshot.child("description").getValue().toString(),
                                snapshot.child("url").getValue().toString(),
                                localDateFormat.format(new Date(Long.parseLong(snapshot.child("timestamp").getValue().toString()))),
                                update);
                        postsList.remove(i);
                        postsList.add(i, userModel);
                        notifyItemChanged(i);
                        recyclerView.scrollToPosition(i);
                        break;
                    }
                }
            }

            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                for (int i = 0; i < postsList.size(); i++) {
                    if (postsList.get(i).postKey.equals(snapshot.getKey())) {
                        postsList.remove(i);
                        notifyItemRemoved(i);
                        break;
                    }
                }
            }

            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }*/

    public MyRecyclerAdapter(ItemClickListener itemClickListener, RecyclerView recyclerView, List<String> keyList, HashMap<String, PostModel> keyToPost) {
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        this.itemClickListener = itemClickListener;
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
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_view, parent, false);
        final MyViewHolder viewHolder = new MyViewHolder(view);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {

        final PostModel postModel = keyToPost.get(keyList.get(position));
        String uid = postModel.uid;
        String key = postModel.postKey;

        // Reset the holder's last edit date view
        holder.updateView.setText("");
        holder.updateView.setVisibility(View.GONE);

        // Set the non-changeable field
        String dateText = "Date Created: " + postModel.date;
        holder.dateView.setText(dateText);

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
                            if (itemClickListener != null)
                                itemClickListener.onPostEditClick(key, postModel.url);
                            return true;
                        case deleteActionId: // Delete action
                            if (itemClickListener != null)
                                itemClickListener.onPostDeleteClick(key, postModel.url);
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
                /* TODO:
                    1. create an ImageView in the item layout
                    2. Picasso and load their profile image
                    3. Create click handler */
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
            if (itemClickListener != null)
                itemClickListener.onPostImageClick(postModel.marker);
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

    public static class MyViewHolder extends RecyclerView.ViewHolder {

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

        public MyViewHolder(View view) {
            super(view);
            menuImage = view.findViewById(R.id.card_menu_image);
            nameView = view.findViewById(R.id.name_view);
            emailView = view.findViewById(R.id.email_view);
            phoneView = view.findViewById(R.id.phone_view);
            dateView = view.findViewById(R.id.date_view);
            updateView = view.findViewById(R.id.update_view);
            descriptionView = view.findViewById(R.id.description);
            imageView = view.findViewById(R.id.post_img);
            likeButton = view.findViewById(R.id.like_btn);
            likesCount = view.findViewById(R.id.like_count);
        }
    }
}
