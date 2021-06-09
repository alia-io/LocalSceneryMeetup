package com.example.alifastith_lab8;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryDataEventListener;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

// https://console.firebase.google.com/
public class HomeActivity extends AppCompatActivity implements OnMapReadyCallback, ItemClickListener {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference allUsersRef = database.getReference("/Users");
    private DatabaseReference allPostsRef = database.getReference("/Posts");
    private DatabaseReference geoFireRef = database.getReference("/GeoFire");

    private List<String> keyList = null;
    private HashMap<String, PostModel> keyToPost = null;
    private SimpleDateFormat localDateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    private static final int REQUEST_FOR_CAMERA = 0011;
    private static final int REQUEST_FOR_LOCATION = 0012;
    private long UPDATE_INTERVAL = 10000;       // 10 seconds
    private long FASTEST_INTERVAL = 2000;       // 2 seconds
    private float SMALLEST_DISPLACEMENT = 20;   // 20 meters
    private double QUERY_RADIUS = 10;           // 10 km

    private FusedLocationProviderClient mFusedLocationClient;   // Google maps reference
    private LocationRequest mLocationRequest;   // Get the location
    private LocationCallback locationCallback;  // Get notified when location changes
    private GoogleMap mMap;

    private GeoFire geoFire = new GeoFire(geoFireRef);
    private GeoQuery geoQuery = null;

    private Uri imageUri = null;
    private RecyclerView recyclerView;
    private MyRecyclerAdapter myRecyclerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        keyList = new ArrayList<>();
        keyToPost = new HashMap<>();

        recyclerView = findViewById(R.id.recycler_view);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        layoutManager.scrollToPosition(0);
        recyclerView.setLayoutManager(layoutManager);
        myRecyclerAdapter = new MyRecyclerAdapter(this, recyclerView, keyList, keyToPost);
        recyclerView.setAdapter(myRecyclerAdapter);

        //https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(SMALLEST_DISPLACEMENT);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;
                Location lastLocation = locationResult.getLastLocation();
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude())).zoom(12).build();
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                newLocation(lastLocation);
            }
        };

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //https://developer.android.com/training/permissions/requesting#java
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "We need permission to access your location.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION },
                    REQUEST_FOR_LOCATION);
            return;
        }

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        myRecyclerAdapter.removeListener();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actionbar_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override // Handle item selection
    public boolean onOptionsItemSelected(MenuItem item) {
        final int signOut = R.id.signout;
        final int newUser = R.id.new_user;
        final int editProfile = R.id.edit_profile;
        switch (item.getItemId()) {
            case signOut:
                mAuth.signOut();
                finish();
                return true;
            case newUser:
                createTestEntry();
                return true;
            case editProfile:
                startActivity(new Intent(this, EditProfile.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void createTestEntry() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        String pushKey = allUsersRef.push().getKey();
        allUsersRef.child(pushKey).setValue(new User("Test Display Name",
                "Test Email", "Test Phone"));
    }

    public void uploadNewPhoto(View view) {
        checkPermissions();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(getBaseContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "We need permission to access your camera and photo.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_FOR_CAMERA);
        } else takePhoto();
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
            Intent intent = new Intent(this, PhotoPreview.class);
            intent.putExtra("uri", imageUri.toString());
            startActivity(intent);
        }
    }

    @Override //https://developer.android.com/training/permissions/requesting#java
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode) {
            case REQUEST_FOR_LOCATION:
                // If request is canceled, the results arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // This next check is to eliminate a compilation error. We know that we have the permission here.
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, Looper.getMainLooper());
                } else {
                    Toast.makeText(this, "The app will not perform correctly without your permission to access the device location.", Toast.LENGTH_SHORT).show();
                }
                return;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            /* TODO: Consider calling ActivityCompat#requestPermissions here to request the missing permissions,
                     and then overriding public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                     to handle the case where the user grants the permission.
                     See documentation for ActivityCompat#requestPermissions for more details. */
            return false;
        }
        return true;
    }

    public void newLocation(Location lastLocation) {
        if (geoQuery != null) {
            geoQuery.setCenter(new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()));
        } else { // Create query based on new location update
            geoQuery = geoFire.queryAtLocation(new GeoLocation(lastLocation.getLatitude(), lastLocation.getLongitude()), QUERY_RADIUS);

            geoQuery.addGeoQueryDataEventListener(new GeoQueryDataEventListener() {
                @Override
                public void onDataEntered(DataSnapshot dataSnapshot, GeoLocation location) {
                    Log.d("EventListener", "onDataEntered invoked");
                    final String postKey = dataSnapshot.getKey();
                    if (keyToPost.containsKey(postKey)) return;
                    database.getReference("Posts/" + postKey).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Log.d("EventListener", "onDataChange called");
                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(Double.parseDouble(snapshot.child("latitude").getValue().toString()),
                                    Double.parseDouble(snapshot.child("longitude").getValue().toString())))
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_grey)));
                            String update = null;
                            if (snapshot.hasChild("lastEditTimestamp"))
                                update = localDateFormat.format(new Date(Long.parseLong(snapshot.child("lastEditTimestamp").getValue().toString())));
                            PostModel postModel = new PostModel(snapshot.getKey(),
                                    snapshot.child("uid").getValue().toString(),
                                    snapshot.child("url").getValue().toString(),
                                    snapshot.child("description").getValue().toString(),
                                    localDateFormat.format(new Date(Long.parseLong(snapshot.child("timestamp").getValue().toString()))),
                                    update, marker);
                            keyToPost.put(postKey, postModel);
                            keyList.add(postKey);
                            marker.setTag(postKey);
                            marker.setTitle(postModel.description);
                            myRecyclerAdapter.notifyItemInserted(keyList.size() - 1);
                            recyclerView.scrollToPosition(keyList.size() - 1);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) { }
                    });
                }

                @Override
                public void onDataExited(DataSnapshot dataSnapshot) {
                    Log.d("EventListener", "onDataExited invoked");
                    removePost(dataSnapshot.getKey());
                }

                @Override
                public void onDataChanged(DataSnapshot dataSnapshot, GeoLocation location) {
                    Log.d("EventListener", "onDataChanged invoked");
                    final String postKey = dataSnapshot.getKey();
                    if (keyToPost.containsKey(postKey)) {
                        PostModel postModel = keyToPost.get(postKey);
                        postModel.marker.setPosition(new LatLng(location.latitude, location.longitude));
                        if (myRecyclerAdapter.unsetIfCurrentMarker(postModel.marker)) {
                            onPostImageClick(postModel.marker);
                        }
                    }
                }

                @Override public void onDataMoved(DataSnapshot dataSnapshot, GeoLocation location) { Log.d("EventListener", "onDataMoved invoked"); }
                @Override public void onGeoQueryReady() { Log.d("EventListener", "onGeoQueryReady invoked"); }
                @Override public void onGeoQueryError(DatabaseError error) { Log.d("EventListener", "onGeoQueryError invoked"); }
            });
        }
    }

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

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        /* TODO: Can customize the styling of the base map using a JSON object defined in a raw resource file.
        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.silver_map_style));
            if (!success) Log.e("UTARZ", "Style parsing failed.");
        } catch (Resources.NotFoundException e) {
            Log.e("UTARZ", "Can't find style. Error: ", e);
        } */

        if (!checkLocationPermission()) return;

        mMap.setOnMarkerClickListener(marker -> {
            onPostImageClick(marker);
            Toast.makeText(HomeActivity.this, "Marker clicked", Toast.LENGTH_SHORT).show();
            return true;
        });

        // Remove the marker selection
        mMap.setOnMyLocationButtonClickListener(() -> {
            myRecyclerAdapter.unsetCurrentMarker();
            mFusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(location.getLatitude(), location.getLongitude())).zoom(12).build();
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }).addOnFailureListener(e ->
                    Toast.makeText(HomeActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show());
            return true;
        });

        mMap.setMyLocationEnabled(true);
    }

    @Override
    public void onPostImageClick(Marker currentMarker) {
        String key = currentMarker.getTag().toString();
        CameraPosition cameraPosition = new CameraPosition.Builder().target(currentMarker.getPosition()).zoom(12).build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        currentMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_red));
        recyclerView.scrollToPosition(keyList.indexOf(key));
        currentMarker.showInfoWindow();
        myRecyclerAdapter.setCurrentMarker(currentMarker);
    }

    public void removePost(String key) {
        PostModel postModel = keyToPost.get(key);
        Marker marker = postModel.marker;
        if (!checkLocationPermission()) return;
        if (myRecyclerAdapter.unsetIfCurrentMarker(marker)) {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(location.getLatitude(), location.getLongitude())).zoom(12).build();
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }).addOnFailureListener(e ->
                    Toast.makeText(HomeActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show());
        }
        for (int i = 0; i < keyList.size(); i++) {
            if (keyList.get(i).equals(key)) {
                keyList.remove(i);
                keyToPost.remove(key);
                myRecyclerAdapter.notifyItemRemoved(i);
                recyclerView.scrollToPosition(i);
                break;
            }
        }
        marker.remove();
    }

    @Override
    public void onPostEditClick(String postKey, String imageId) {
        Intent intent = new Intent(this, PhotoPreview.class);
        intent.putExtra("key", postKey);
        StorageReference imagePathRef = FirebaseStorage.getInstance().getReference("Images/" + imageId);
        imagePathRef.getDownloadUrl().addOnSuccessListener(uri -> {
            intent.putExtra("uri", uri.toString());
            startActivity(intent);
            Toast.makeText(this, "Editing", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onPostDeleteClick(String postKey, String imageId) {
        geoFireRef.child(postKey).setValue(null)
                .addOnSuccessListener(aVoid -> allPostsRef.child(postKey).setValue(null)
                        .addOnSuccessListener(aVoid12 -> {
                            FirebaseStorage storage = FirebaseStorage.getInstance();
                            StorageReference imagePathRef = storage.getReference("Images/" + imageId);
                            imagePathRef.delete()
                                    .addOnSuccessListener(aVoid1 ->
                                            Toast.makeText(HomeActivity.this, "Deleted", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(HomeActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show());
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(HomeActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(HomeActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
