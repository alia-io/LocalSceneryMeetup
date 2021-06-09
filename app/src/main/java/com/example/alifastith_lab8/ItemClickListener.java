package com.example.alifastith_lab8;

import com.google.android.gms.maps.model.Marker;

// Interface to communicate from RecyclerView Adapter
public interface ItemClickListener {
    void onPostImageClick(Marker currentMarker);
    void onPostEditClick(String postKey, String imageId);
    void onPostDeleteClick(String postKey, String imageId);
}
