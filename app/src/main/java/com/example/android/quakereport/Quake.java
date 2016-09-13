package com.example.android.quakereport;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;

/**
 * {@link Quake} represents a Quake listing.
 * It contains a location, magnitude, and date.
 */

public class Quake implements Cloneable {

    /**
     * ID, from 1 to however many there are
     */
    private Integer mBankID;

    /**
     * street name and number
     */
    private String mSiteName;

    /**
     * type of bank e.g. paper, packaging, bottle
     */
    private String mBankType;
    /*
    * location object containing lat lon
     */
    private LatLng mLocationLatLng;
    /*
    * location description, where the bank is on the street
     */
    private String mLocationDetails;
    /*
    * create a hashmap, or dict, from bankType to Boolean array, to flag banks @ same location
     */
    private final HashMap<String, Boolean> hashMap = new HashMap<String, Boolean>();

    /**
     * Create a new Quake object.
     *
     * @param bank_ID is the unique number of the recycling bin
     * @param site_name is the street address
     * @param bank_type is the type of the bin {packaging, paper or glass}
     * @param location is the lat lng of the bin
     * @param location_details is directions to the bin
     */
    public Quake(Integer bank_ID, String site_name, String bank_type, LatLng location, String location_details) {
        mBankID = bank_ID;
        mSiteName = site_name;
        mBankType = bank_type;
        mLocationLatLng = location;
        mLocationDetails = location_details;

        hashMap.put("Packaging", false);
        hashMap.put("Paper Bank", false);
        hashMap.put("Bottle Bank", false);
        hashMap.put("Textile Bank", false);
        hashMap.put("Others", false);
    }

    /**
     * Get the bank ID.
     */
    public Integer getBankID() {
        return mBankID;
    }
    /**
     * Get the location text string.
     */
    public String getSiteName() {
        return mSiteName;
    }

    /**
     * Get the bank type
     */
    public String getBankType() {
        return mBankType;
    }
    /*
    * Get the lat-lon location object
     */
    public LatLng getLocation() { return mLocationLatLng; }
    /**
     * Get the location details
     */
    public String getLocationDetails() {return mLocationDetails; }
    /**
     * enables us to create a clean copy
     */
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
    /**
     * Change the bankType, used for compressing banks @ same location for globalList
     */
    public void changeBankType(String newBankType) {
        mBankType = newBankType;
        return;
    }

    /**
     * Flag banks at this location
     * @param bankBucket may be a container for several bankTypes, e.g. cans, compost, books
     *                   or bottle bank, inc. all bottle types
     */
    public void flagBank(String bankBucket) {
        hashMap.put(bankBucket, true);
    }
}


