package com.example.android.recyclingbanks;

/**
 * Created by Sam on 19/09/2016.
 * {@ReturnValues} represents lat lon and street name
 */

public class ReturnValues {
    private Double mLatitude;
    private Double mLongitude;
    private String mStreetAddress;

    public ReturnValues(Double latitude, Double longitude, String streetAddress) {
        mLatitude = latitude;
        mLongitude = longitude;
        mStreetAddress = streetAddress;
    }
    public Double getLatitude() { return mLatitude; }
    public Double getLongitude() { return mLongitude; }
    public String getStreetAddress() { return mStreetAddress; }
}
