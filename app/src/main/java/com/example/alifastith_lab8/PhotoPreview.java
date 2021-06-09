package com.example.alifastith_lab8;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.util.UUID;

public class PhotoPreview extends AppCompatActivity {

    private static final int REQUEST_FOR_CAMERA = 0011;
    private static final int REQUEST_FOR_LOCATION = 123;
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference allPostsRef = database.getReference("Posts");
    private DatabaseReference postRef;
    private FirebaseStorage storage = FirebaseStorage.getInstance();
    private StorageReference imagePathRef;
    private FusedLocationProviderClient mFusedLocationClient;
    private String postKey;
    private Uri uri;
    private Post post;
    private ImageView imageView;
    private EditText editText;
    private String imageUri;    // Original image uri
    private String description; // Original description

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_preview);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        editText = findViewById(R.id.description);
        imageView = findViewById(R.id.preview_image);
        postKey = getIntent().getStringExtra("key");
        imageUri = getIntent().getStringExtra("uri");
        uri = Uri.parse(imageUri);
        Picasso.get().load(uri).into(imageView);

        Log.d("PhotoPreview", "onCreate");

        if (postKey != null) {
            ((Button) findViewById(R.id.publish_button)).setText("UPDATE");
            findViewById(R.id.new_image_button).setVisibility(View.VISIBLE);
            postRef = allPostsRef.child(postKey);
            postRef.runTransaction(new Transaction.Handler() {
                @NonNull @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    // I don't know why but this runs multiple times. Ignore it if the post data was already retrieved or if data is empty.
                    if (post != null) return Transaction.success(currentData);
                    post = currentData.getValue(Post.class);
                    if (post == null) { return Transaction.success(currentData); }
                    imagePathRef = storage.getReference("Images/" + post.url);
                    description = post.description;
                    editText.post(() -> editText.setText(description));
                    return Transaction.success(currentData);
                }
                @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) { }
            });
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
                    REQUEST_FOR_LOCATION);
        }
    }

    public void newImage(View view) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From your camera");
        uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        Intent chooser = Intent.createChooser(intent, "Select a Camera App.");
        if (intent.resolveActivity(getPackageManager()) != null)
            startActivityForResult(chooser, REQUEST_FOR_CAMERA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOR_CAMERA && resultCode == RESULT_OK) {
            if (uri == null) {
                Toast.makeText(this, "Error taking photo.", Toast.LENGTH_SHORT).show();
                return;
            }
            Picasso.get().load(uri).into(imageView);
        }
    }

    public void publish(View view) {
        if (post == null) {
            Log.d("PhotoPreview", "publish: post is null");
        } else {
            Log.d("PhotoPreview", "publish: post is not null");
        }
        if (postKey == null || !editText.getText().toString().equals(description) || (uri != null && !uri.toString().equals(imageUri))) {
            if (uri != null) uploadImage();
            else Toast.makeText(this, "You have not selected an image.", Toast.LENGTH_SHORT).show();
        } else Toast.makeText(this, "No changes were made.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void uploadImage() {

        final DatabaseReference geoFireRef = database.getReference("/GeoFire");
        final GeoFire geoFire = new GeoFire(geoFireRef);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            /* TODO: Consider calling ActivityCompat#requestPermissions here to request the missing permissions,
                     and then overriding public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                     to handle the case where the user grants the permission.
                     See documentation for ActivityCompat#requestPermissions for more details. */
            return;
        }

        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (postKey == null) { // Adding new post
                            final String fileNameInStorage = UUID.randomUUID().toString();
                            final StorageReference imageRef = storage.getReference("Images/" + fileNameInStorage + ".jpg");
                            final String latitude = String.valueOf(location.getLatitude());
                            final String longitude = String.valueOf(location.getLongitude());
                            final StorageMetadata metadata = new StorageMetadata.Builder()
                                    .setContentType("image/jpg")
                                    .setCustomMetadata("uid", currentUser.getUid())
                                    .setCustomMetadata("description", editText.getText().toString())
                                    .setCustomMetadata("latitude", latitude)
                                    .setCustomMetadata("longitude", longitude)
                                    .build();
                            Log.d("PhotoPreview", "uri = " + uri.toString());
                            imageRef.putFile(uri, metadata)
                                    .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                        @Override
                                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                            // TODO: This part can be eliminated with a firebase cloud function
                                            final DatabaseReference newPostRef = allPostsRef.push();
                                            newPostRef.setValue(new Post(currentUser.getUid(), fileNameInStorage + ".jpg", editText.getText().toString(), latitude, longitude))
                                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                        @Override
                                                        public void onSuccess(Void aVoid) {
                                                            geoFire.setLocation(newPostRef.getKey(), new GeoLocation(Double.parseDouble(latitude), Double.parseDouble(longitude)));
                                                            Toast.makeText(PhotoPreview.this, "Success", Toast.LENGTH_SHORT).show();
                                                        }
                                                    })
                                                    .addOnFailureListener(new OnFailureListener() {
                                                        @Override
                                                        public void onFailure(@NonNull Exception e) {
                                                            Toast.makeText(PhotoPreview.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                                        }
                                                    });
                                            Toast.makeText(PhotoPreview.this, "Upload completed. Your image will appear shortly.", Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Toast.makeText(PhotoPreview.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        } else if (post != null) { // Editing existing post
                            if (uri == null || uri.toString().equals(imageUri)) { // Don't change the image
                                final StorageReference imageRef = storage.getReference("Images/" + post.url);
                                final StorageMetadata metadata = new StorageMetadata.Builder()
                                        .setCustomMetadata("description", editText.getText().toString())
                                        .build();
                                imageRef.updateMetadata(metadata)
                                        .addOnSuccessListener(new OnSuccessListener<StorageMetadata>() {
                                            @Override
                                            public void onSuccess(StorageMetadata storageMetadata) {
                                                post.description = editText.getText().toString();
                                                post.lastEditTimestamp = ServerValue.TIMESTAMP;
                                                postRef.runTransaction(new Transaction.Handler() {
                                                    @NonNull
                                                    @Override
                                                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                                        currentData.setValue(post);
                                                        return Transaction.success(currentData);
                                                    }
                                                    @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) { }
                                                });
                                                Toast.makeText(PhotoPreview.this, "Success", Toast.LENGTH_SHORT).show();
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(PhotoPreview.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            } else { // Change image data
                                final StorageReference oldImageRef = storage.getReference("Images/" + post.url);
                                final String fileNameInStorage = UUID.randomUUID().toString();
                                final StorageReference imageRef = storage.getReference("Images/" + fileNameInStorage + ".jpg");
                                final String latitude = String.valueOf(location.getLatitude());
                                final String longitude = String.valueOf(location.getLongitude());
                                final StorageMetadata metadata = new StorageMetadata.Builder()
                                        .setContentType("image/jpg")
                                        .setCustomMetadata("uid", currentUser.getUid())
                                        .setCustomMetadata("description", editText.getText().toString())
                                        .setCustomMetadata("latitude", latitude)
                                        .setCustomMetadata("longitude", longitude)
                                        .build();
                                imageRef.putFile(uri, metadata)
                                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                            @Override
                                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                                geoFire.setLocation(postKey, new GeoLocation(Double.parseDouble(latitude), Double.parseDouble(longitude)));
                                                post.latitude = latitude;
                                                post.longitude = longitude;
                                                post.url = fileNameInStorage + ".jpg";
                                                post.description = editText.getText().toString();
                                                post.lastEditTimestamp = ServerValue.TIMESTAMP;
                                                oldImageRef.delete()
                                                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                            @Override
                                                            public void onSuccess(Void aVoid) {
                                                                postRef.runTransaction(new Transaction.Handler() {
                                                                    @NonNull
                                                                    @Override
                                                                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                                                                        currentData.setValue(post);
                                                                        return Transaction.success(currentData);
                                                                    }
                                                                    @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) { }
                                                                });
                                                                Toast.makeText(PhotoPreview.this, "Success", Toast.LENGTH_SHORT).show();
                                                            }
                                                        })
                                                        .addOnFailureListener(new OnFailureListener() {
                                                            @Override
                                                            public void onFailure(@NonNull Exception e) {
                                                                Toast.makeText(PhotoPreview.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                                            }
                                                        });
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Toast.makeText(PhotoPreview.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        } else {
                            Toast.makeText(PhotoPreview.this, "Could not find Post data.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(PhotoPreview.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_FOR_LOCATION
                && ((grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
                || (grantResults.length > 1 && grantResults[1] != PackageManager.PERMISSION_GRANTED))) {
            Toast.makeText(this, "We need to access your location", Toast.LENGTH_SHORT).show();
        }
    }
}
