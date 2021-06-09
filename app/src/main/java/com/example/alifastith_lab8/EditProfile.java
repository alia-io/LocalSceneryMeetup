package com.example.alifastith_lab8;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.util.UUID;

public class EditProfile extends AppCompatActivity implements PopupMenu.OnMenuItemClickListener {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference usersRef;
    private EditText displayName, phoneNumber;
    private ImageView profileImage;
    private Uri imageUri = null;
    private static final int REQUEST_FOR_CAMERA = 0011;
    private static final int OPEN_FILE = 0012;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        displayName = findViewById(R.id.display_name_text);
        phoneNumber = findViewById(R.id.phone_number_text);
        profileImage = findViewById(R.id.profile_image);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        final FirebaseDatabase database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("Users/" + currentUser.getUid());

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    phoneNumber.setText(snapshot.child("phoneNumber").getValue().toString());
                    displayName.setText(snapshot.child("displayName").getValue().toString());
                    if (snapshot.child("profilePicture").exists())
                        Picasso.get().load(snapshot.child("profilePicture").getValue().toString()).transform(new CircleTransform()).into(profileImage);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    public void uploadProfilePhoto(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.popup, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        final int takePhoto = R.id.take_photo;
        final int upload = R.id.upload;

        switch (item.getItemId()) {
            case takePhoto:
                checkPermissions();
                return true;
            case upload:
                Intent intent = new Intent().setType("*/*") // when uncommented the argument here shall be "start/star"
                        .setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select a file"), OPEN_FILE);
                return true;
            default:
                return false;
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "We need permission to access your camera and photo.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_FOR_CAMERA);
        } else
            takePhoto();
    }

    private void takePhoto() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");
        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        Intent chooser = Intent.createChooser(intent, "Select a Camera App.");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(chooser, REQUEST_FOR_CAMERA);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FOR_CAMERA && resultCode == RESULT_OK) {
            if (imageUri == null) {
                Toast.makeText(this, "Error taking photo.", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadImage();
            return;
        }

        if (requestCode == OPEN_FILE && resultCode == RESULT_OK) {
            imageUri = data.getData();
            uploadImage();
        }
    }

    private void uploadImage() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        final String fileNameInStorage = UUID.randomUUID().toString();
        String path = "Images/" + fileNameInStorage + ".jpg";
        final StorageReference imageRef = storage.getReference(path);

        imageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> imageRef.getDownloadUrl()
                        .addOnSuccessListener(uri ->
                                usersRef.child("profilePicture").setValue(uri.toString()).addOnSuccessListener(aVoid ->
                                        Picasso.get().load(uri.toString()).transform(new CircleTransform()).into(profileImage)))
                        .addOnFailureListener(e ->
                                Toast.makeText(EditProfile.this, e.getMessage(), Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(EditProfile.this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && requestCode == REQUEST_FOR_CAMERA) {
            if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            }
        } else
            Toast.makeText(this, "We need to access your camera and photos to upload.", Toast.LENGTH_SHORT).show();
    }

    public void save(View view) {
        if (displayName.getText().toString().equals("") || phoneNumber.getText().toString().equals("")) {
            Toast.makeText(this, "Please enter your display name and phont number", Toast.LENGTH_SHORT).show();
            return;
        }
        usersRef.child("displayName").setValue(displayName.getText().toString());
        usersRef.child("phoneNumber").setValue(phoneNumber.getText().toString());
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
