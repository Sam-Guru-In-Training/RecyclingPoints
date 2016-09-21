package com.example.android.recyclingbanks;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Sam on 13/08/2016.
 */

public class EarthquakeLoader extends AsyncTaskLoader<Boolean> {
    //InputStream mIS;
    //private String mUrl;
    private int distanceFromHome;
    private Double homeLatitude;
    private Double homeLongitude;
    private final String LOG_TAG = EarthquakeLoader.class.getName();

    //public EarthquakeLoader(Context context, String url, int maxRadius) {
    public EarthquakeLoader(Context context, int maxRadius, Double mUserLatitude, Double mUserLongitude) {
        super(context);
        Log.i(LOG_TAG, "!!!! creating a new EarthquakeLoader, loading context, assigning URL");
        //mUrl = url;
        //mIS = is;
        distanceFromHome = maxRadius;
        homeLatitude = mUserLatitude;
        homeLongitude = mUserLongitude;
        //mUrl = "http://data.edinburghopendata.info/api/action/datastore_search?resource_id=4cfb5177-d3db-4efc-ac6f-351af75f9f92&limit=500";g
    }

    @Override
    protected void onStartLoading() {
        Log.i(LOG_TAG, "!!!! onStartLoading is forceLoad()");
        forceLoad();
    }

    /*
    * on a background thread, LoaderManager merely MANAGERS background processing,
    * it's a wrapper
     */
    @Override
    public Boolean loadInBackground() {
        Log.i(LOG_TAG, "!!!! loadInBackground calling QueryUtils to get data");
//        if (mUrl == null) {
//            return null;
//        }
        // to extract online earthquakes
        //Boolean noMarkers = QueryUtils.extractBanksList(mUrl, distanceFromHome);
        // to extract OFFline earthquake
        InputStream is;
        try {
            is = getContext().getAssets().open("recycleBanksOffline");
        } catch (IOException e) {
            e.printStackTrace();
            Log.wtf("EarthquakeLoader", "unable to get json!!!!");
            return true;
        }
        Boolean noMarkers = true;
        noMarkers = QueryUtils.extractBanksList(is, distanceFromHome, homeLatitude, homeLongitude);
        Log.i(LOG_TAG, "!!!! loadInBackground returning masterBanksList list from QueryUtils");
        return noMarkers; //globalBanksList;
    }
//    private ArrayList<Quake> offlineJsonGetter() {
//        offlineJsonHandler jsonHandler = new offlineJsonHandler(this.getContext());
//        String jsonResponse = jsonHandler.loadJSONFromAsset();
//        ArrayList<Quake> globalBanksList = QueryUtils.extractFeatureFromOfflineJson(jsonResponse);
//        return globalBanksList;
//    }
}
