package com.example.android.recyclingbanks;

import android.util.Log;

import com.google.android.gms.maps.model.Marker;

/**
 * Created by Sam on 17/09/2016.
 */

class MarkerCallback implements com.squareup.picasso.Callback {
    private Marker marker = null;
    final String TAG = "MarkerCallback";

    MarkerCallback(Marker marker) {
        this.marker=marker;
    }

    @Override
    public void onError() {
        Log.e(getClass().getSimpleName(), "Error loading thumbnail!");
    }

    @Override
    public void onSuccess() {
        Log.i(TAG, "image got, should rebuild window");
        if (marker != null && marker.isInfoWindowShown()) {
            Log.i(TAG, "conditions met, redrawing window");
            marker.setTag(new Boolean("True"));
            marker.showInfoWindow();
        }
    }

}
