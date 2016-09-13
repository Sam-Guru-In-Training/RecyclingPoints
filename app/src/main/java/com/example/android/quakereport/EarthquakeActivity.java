/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.quakereport;

import android.Manifest;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.example.android.quakereport.QueryUtils.homeMarkerRaw;
import static com.example.android.quakereport.QueryUtils.mBottleBanks;
import static com.example.android.quakereport.QueryUtils.mCombinedBanks;
import static com.example.android.quakereport.QueryUtils.mOthers;
import static com.example.android.quakereport.QueryUtils.mPackagingBanks;
import static com.example.android.quakereport.QueryUtils.mPaperBanks;
import static com.example.android.quakereport.QueryUtils.mTextileBanks;
import static com.example.android.quakereport.QueryUtils.mapCombinedMarkerToBanks;

//import static com.google.android.gms.analytics.internal.zzy.i;

public class EarthquakeActivity extends AppCompatActivity implements OnMapReadyCallback,
        LoaderManager.LoaderCallbacks<Boolean>, GoogleMap.OnMarkerClickListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String LOG_TAG = EarthquakeActivity.class.getName();
    public static final String REQUEST_URL = "http://data.edinburghopendata.info/api/action/datastore_search?resource_id=4cfb5177-d3db-4efc-ac6f-351af75f9f92&limit=4757"; //4757";
    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    // Provides the entry point to Google Play services.  Protected means only accessible inside app
    protected GoogleApiClient mGoogleApiClient;
    protected Location mLastLocation;
    protected double mUserLatitude;
    protected double mUserLongitude;
    protected LatLng mUserLatLng;


    static final int REQUEST_IMAGE_CAPTURE = 1;
    public QuakeAdapter adapter;
    private TextView mEmptyView;
    private ProgressBar mProgressBar;

    public GoogleMap m_map;
    private boolean mapReady = false;
    private SharedPreferences preferences;

    private final int BUTTONonCOLOR = R.color.white;
    private final int BUTTONoffCOLOR = R.color.colorPrimaryDark;
    // maps from button id, to present button color (activated/deactivated), keeps track of state
    HashMap<Integer, Boolean> buttonActivationMap = new HashMap<>();

    // for combinedBanks, maps from Marker key to array indicating bankTypes present
    HashMap<Marker, Boolean[]> mapMarkerToBank = new HashMap<>();


    // stores the map markers once they're attached to the map, for toggling visibility
    List<Marker> mPackagingBankMarkers = new ArrayList<>();
    List<Marker> mPaperBankMarkers = new ArrayList<>();
    List<Marker> mTextileBankMarkers = new ArrayList<>();
    List<Marker> mBottleBankMarkers = new ArrayList<>();
    List<Marker> mOtherBankMarkers = new ArrayList<>();
    List<Marker> mCombinedBankMarkers = new ArrayList<>();

    Marker homeMarkerFinal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bottombar_constraint_layout);
        //setContentView(R.layout.earthquake_activity);
        // TODO ask for postcode and house number on first login, tell them you can change this using [icon] later
        //DownloadTask task = new DownloadTask();
        //task.execute(REQUEST_URL);
        // load preference defaults for buttons press,
        // false means we won't ever read this again after first app boot
        PreferenceManager.setDefaultValues(this, R.xml.default_preferences, true);
        // preferences contains booleans to record click status of bottom bar
        preferences = getPreferences(MODE_PRIVATE);

        // Find a reference to the {@link ListView} in the layout
        ListView earthquakeListView = (ListView) findViewById(R.id.list);

        mProgressBar = (ProgressBar) findViewById(R.id.loading_spinner);
        // WANT TO create a message if the list is empty
        mEmptyView = (TextView) findViewById(R.id.emptyView);
        // by attaching this to listView, it only shows if listView is empty

        Log.i(LOG_TAG, "!!!!! launching a new LoaderManager");

        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    /**
     * need to put logic in onStart so it rebuilds the map after leaving preferences screen.
     */
    @Override
    public void onStart() {
        super.onStart();
        // setup bottomBar button colours
        setupButtonColours();


        // find out if connected to the internet
        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        if (isConnected) {
            LoaderManager loaderManager = getLoaderManager();
            loaderManager.initLoader(0, null, this);
        } else {
            mProgressBar.setVisibility(View.GONE);
            mEmptyView.setText("No internet connection");
        }

        // if don't have user location permission ask for it
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            Log.i("!!!!!!onMapReady: ", "asking for fine location permission");
            //return;
        }
        // setup the map
        Log.i(LOG_TAG, "setting up the map");
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_report_full_bank:
                Intent webIntent = new Intent(this, WebViewActivity.class);
                webIntent.putExtra("urlString", "https://my.edinburgh.gov.uk/app/waste/full_overflowing_communal_bin");
                webIntent.putExtra("title", "");
                startActivity(webIntent);
                break;

            case R.id.action_change_search_radius:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                break;

            case R.id.action_set_new_home_location:
                // TODO error here?
                Log.i(LOG_TAG, "going to SettingsActivity");
                settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                break;

            case R.id.action_features_poll:
                Intent featuresIntent = new Intent(this, WebViewActivity.class);
                featuresIntent.putExtra("urlString", "https://docs.google.com/forms/d/e/1FAIpQLScPaxkfAmhOFKLMqIJ93bhtngt3A1gm83IprABzbVQpSMyQWg/viewform");
                featuresIntent.putExtra("title", "Future frogs");
                startActivity(featuresIntent);
                break;

            case R.id.action_feedback:
                String[] addresses = new String[1];
                composeEmail(addresses, "Feedback on Edinburgh Recycle App");
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    /**
     * composes an email, strictly for email apps.  This version takes no attachments
     */
    public void composeEmail(String[] addresses, String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, "sam.stratford@gmail.com");//addresses);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    /**
     * Implementation of AsyncTask, to fetch the data in the background away from
     * the UI thread.
     */
    //private class DownloadTask extends AsyncTask<String, Void, ArrayList<Quake>> {
    @Override
    public android.content.Loader<Boolean> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created.  This
        // sample only has one Loader, so we don't care about the ID.
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        // get the distance from home, this is in meters, default = 1000m
        int maxRadius = Integer.parseInt(sharedPrefs.getString(
                getString(R.string.settings_distance_from_home_key),
                getString(R.string.settings_distance_from_home_default)));

        Log.i("onCreateLoader", "!!!!!!! creating new Earthquake loader object" + String.valueOf(maxRadius));

        return new EarthquakeLoader(this, maxRadius);
        //return new EarthquakeLoader(this, REQUEST_URL, maxRadius);

    }

    /**
     * Uses the logging framework to display the output of the fetch
     * operation in the log fragment.
     */
    @Override
    public void onLoadFinished(android.content.Loader<Boolean> loader, Boolean noMarkers) {
        Log.i("onLoadFinished", "all json processed");
        mProgressBar.setVisibility(ProgressBar.GONE);
        if (noMarkers == true) {// || globalBanksList.isEmpty()) {
            // we only assign text here after first load completed to prevent it
            // flashing up while quakes load and misleading user.
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyView.setText(R.string.no_banks_downloaded_text);
            return;
        }
        // Update the information displayed to the user.

        // check we have permissions to progress
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
        // add home marker, save it, so we can identify when this marker is clicked on
        homeMarkerFinal = m_map.addMarker(homeMarkerRaw);

        boolean packagingActivated = preferences.getBoolean("Packaging", false);
        if (packagingActivated) {
            mPackagingBankMarkers = addBankListToMap(mPackagingBanks);
        }
        boolean paperActivated = preferences.getBoolean("Paper", false);
        if (paperActivated) {
            mPaperBankMarkers = addBankListToMap(mPaperBanks);
        }
        boolean textilesActivated = preferences.getBoolean("Textiles", false);
        if (textilesActivated) {
            mTextileBankMarkers = addBankListToMap(mTextileBanks);
        }
        boolean bottlesActivated = preferences.getBoolean("Bottles", false);
        if (bottlesActivated) {
            mBottleBankMarkers = addBankListToMap(mBottleBanks);
        }

        boolean othersActivated = preferences.getBoolean("Others", false);
        if (bottlesActivated) {
            mOtherBankMarkers = addBankListToMap(mOthers);
        }

        Boolean[] bankTypesActivated = {packagingActivated, paperActivated, textilesActivated,
                bottlesActivated, othersActivated};

        // TODO need to sort this so displays if a combined pt. contains a activated bank
        mCombinedBankMarkers = addCombinedBanksToMap(mCombinedBanks, bankTypesActivated);
        // free up memory
        //mCombinedBanks = null;

        // setup click event for home marker to launch change home screen
        m_map.setOnMarkerClickListener(EarthquakeActivity.this);
    }

    private List<Marker> addCombinedBanksToMap(List<MarkerOptions> mCombinedBanks, Boolean[] bankTypesActivated) {
        Log.i("ADDING a selected", "banks list to map");
        // we need to save NEW lists of map attached markers OR can't change marker visibility
        List<Marker> newBankList = new ArrayList<>();
        // a flag to tell us if a combinedMarker has a activated bank
        Boolean showMarker;

        for (int i = 0; i < mCombinedBanks.size(); i++) {
            MarkerOptions binMarker = mCombinedBanks.get(i);
            Marker newMarker = m_map.addMarker(binMarker);
            mCombinedBankMarkers.add(newMarker);

            // reconnect hashMap from Markers to bankTypes at site
            Boolean[] bankTypeArray = mapCombinedMarkerToBanks.get(binMarker);
            mapMarkerToBank.put(newMarker, bankTypeArray);

            // marker is visible, check whether to hide
            // when do we hide?  if the marker contains 0 activated bankTypes
            showMarker = false;
            for (int j = 0; j < bankTypesActivated.length; j++) {
                if (bankTypesActivated[j] == true && mapMarkerToBank.get(newMarker)[j] == true) {
                    // if the bankType is activated & our newMarker contains the bank,
                    showMarker = true;
                    // just need one match and we're showing it
                    break;
                }
            }
            if (showMarker == false) {
                newMarker.setVisible(false);
            }
        }
        // free up memory
        //mapCombinedMarkerToBanks = null;

        return mCombinedBankMarkers;
    }

    /**
     * spools through a bank list and adds the markers to the map.  Used on app loading
     * @param oneBankList list of markers for a single bankType
     */
    private List<Marker> addBankListToMap(List<MarkerOptions> oneBankList) {
        Log.i("ADDING a selected", "banks list to map");
        // we need to save NEW lists of map attached markers OR can't change marker visibility
        List<Marker> newBankList = new ArrayList<>();
        for (int i = 0; i < oneBankList.size(); i++) {
            MarkerOptions binMarker = oneBankList.get(i);
            Marker newMarker = m_map.addMarker(binMarker);
            newMarker.setVisible(true);
            newBankList.add(newMarker);
            //Log.e("!!!addingBank", newMarker.getTitle() + newMarker.getSnippet());
        }
        return newBankList;
    }

    // TODO onInfoWindowClick set a bin empty day reminder?
    // TODO fix rotation, make sure rebuilds map.
    @Override
    public boolean onMarkerClick(Marker marker) {
        Log.wtf("!!!!!!!!!!!!onMarkerClick", "A marker was clicked!");
        if (marker.equals(homeMarkerFinal)) {
            // TODO make this launch the change home address screen
            Log.wtf("markerClicked", "!!!!");
            Intent settingsIntent = new Intent(EarthquakeActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        }
        else {
            marker.showInfoWindow();
        }
        return true;
    }

    /**
     * Toggle the button colour
     * @param buttonId
     * @param buttonColor new colour for the button
     */
    private void setButtonColor(int buttonId, int buttonColor) {
        ImageView ivVectorImage = (ImageView) findViewById(buttonId);
        ivVectorImage.setColorFilter(getResources().getColor(buttonColor));
    }

    /**
     * reads from SharedPrefs, populates yieldButton hashmap, setups button colours. Called from onCreate
     */
    public void setupButtonColours() {
        buttonActivationMap.put(R.id.packagingBanks, preferences.getBoolean("Packaging", true));
        buttonActivationMap.put(R.id.paperBanks, preferences.getBoolean("Paper", true));
        buttonActivationMap.put(R.id.textileBanks, preferences.getBoolean("Textiles", true));
        buttonActivationMap.put(R.id.bottleBanks, preferences.getBoolean("Bottles", true));
        buttonActivationMap.put(R.id.cansBooksFoodBins, preferences.getBoolean("Others", true));

        int buttonId;
        Boolean activated;
        Iterator iterator = buttonActivationMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry keyValuePair = (Map.Entry) iterator.next();
            buttonId = (int) keyValuePair.getKey();
            activated = (Boolean) keyValuePair.getValue();
            if (activated) {
                setButtonColor(buttonId, BUTTONonCOLOR);
            } else {
                setButtonColor(buttonId, BUTTONoffCOLOR);
            }
        }
    }

    public void onClick(View view) {
        int buttonId = view.getId();
        String toastText = (String) view.getTag();
        Boolean activated = buttonActivationMap.get(buttonId);

        //change button state
        activated = !activated;
        buttonActivationMap.put(buttonId, activated);

        if (activated == true) {
            // turning button on
            setButtonColor(buttonId, BUTTONonCOLOR);
        } else {
            // turning button off
            toastText = "Hiding " + toastText;
            setButtonColor(buttonId, BUTTONoffCOLOR);
        }
        // show toast, funkiness to make 650 milli secs
        final Toast toast = Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
        toast.show();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toast.cancel();
            }
        }, 650);

        // hide or show banks on map
        // TODO make asyncTask possibly.
        // TODO this isn't filtering CombinedBanks properly
        switch (buttonId) {
            case R.id.packagingBanks:
                // filter combinedBanks
                hideOrShowCombinedBanks(activated, 0);

                if (mPackagingBankMarkers.isEmpty()) {
                    // if haven't already loaded the markers do so and display them
                    mPackagingBankMarkers = addBankListToMap(mPackagingBanks);
                    break;
                }
                // the markers HAVE been loaded, so flip their visibility
                hideOrShowBanks(mPackagingBankMarkers, activated);
                break;
            case R.id.paperBanks:
                hideOrShowCombinedBanks(activated, 1);
                if (mPaperBankMarkers.isEmpty()) {
                    mPaperBankMarkers = addBankListToMap(mPaperBanks);
                    break;
                }
                hideOrShowBanks(mPaperBankMarkers, activated);
                break;
            case R.id.textileBanks:
                hideOrShowCombinedBanks(activated, 2);
                if (mTextileBankMarkers.isEmpty()) {
                    mTextileBankMarkers = addBankListToMap(mTextileBanks);
                    break;
                }
                hideOrShowBanks(mTextileBankMarkers, activated);
                return;
            case R.id.bottleBanks:
                hideOrShowCombinedBanks(activated, 3);
                if (mBottleBankMarkers.isEmpty()) {
                    mBottleBankMarkers = addBankListToMap(mBottleBanks);
                    break;
                }
                hideOrShowBanks(mBottleBankMarkers, activated);
                break;
            case R.id.cansBooksFoodBins:
                hideOrShowCombinedBanks(activated, 4);
                if (mOtherBankMarkers.isEmpty()) {
                    mOtherBankMarkers = addBankListToMap(mOthers);
                    break;
                }
                hideOrShowBanks(mOtherBankMarkers, activated);
                break;
        }
        return;
    }

    /**
     * filters CombinedBankMarkers to show them, activated = true, or hide them, activated = false
     */
    private void hideOrShowCombinedBanks(Boolean activated, int bankType) {
        for (int i = 0; i < mCombinedBankMarkers.size(); i++) {
            Marker testMarker = mCombinedBankMarkers.get(i);
            // get boolean array out of testMarker, and look at entry corresponding to flipped bank
            Boolean bankTypePresentInTestMarker = mapMarkerToBank.get(testMarker)[bankType];
            // if the marker contains the bank of interest
            if (bankTypePresentInTestMarker) {
                // change it's visibility
                testMarker.setVisible(activated);
            }
        }
    }

    /**
     * takes a list of banks and whether to show them, on = true, or hide them, on = false
     */
    public void hideOrShowBanks(List bankList, boolean on) {
        // check if we've loaded the marker onto the map, if haven't bankList will be empty
        for (int i = 0; i < bankList.size(); i++) {
            Marker markerToTurnOff = (Marker) bankList.get(i);
            //Log.wtf("attempting to hide/show", markerToTurnOff.getTitle() + ". Visible? " + String.valueOf(markerToTurnOff.isVisible()));
            markerToTurnOff.setVisible(on);
            //markerToTurnOff
        }
        // now filter the combinedBanks
    }

    public void onPause() {
        super.onPause();
        // save users display
        SharedPreferences.Editor editor = preferences.edit();
        Boolean activated = buttonActivationMap.get(R.id.packagingBanks);
        editor.putBoolean("Packaging", activated);
        activated = buttonActivationMap.get(R.id.paperBanks);
        editor.putBoolean("Paper", activated);
        activated = buttonActivationMap.get(R.id.textileBanks);
        editor.putBoolean("Textiles", activated);
        activated = buttonActivationMap.get(R.id.bottleBanks);
        editor.putBoolean("Bottles", activated);
        activated = buttonActivationMap.get(R.id.cansBooksFoodBins);
        editor.putBoolean("Others", activated);
        editor.apply();
    }

    /**
     * Loads markers for a specific bank type.
     * hides all non relevant markers
     */
    @Override
    public void onLoaderReset(android.content.Loader<Boolean> loader) {
        Log.i("onLoaderReset", "!!!!! Why has this been called?  What to do?");
        // reset it, changed device orientation, so clear out the adapter, and connected listView
        //adapter.clear();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mapReady = true;
        m_map = googleMap;

        Log.i("!!!!!!onMapReady: ", "should have location permission");
        m_map.setMyLocationEnabled(true);

        // center on present location zoomed in
        //Location userLocation = getMyLocation();
        //LatLng userCoords = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());

        LatLng edinburgh = new LatLng(55.948611, -3.199935);
        CameraPosition target = CameraPosition.builder().target(edinburgh).zoom(14).build();
        m_map.moveCamera(CameraUpdateFactory.newCameraPosition(target));
    }

    /*
    * Get's users location, used for placing camera on start point
     */
    private Location getMyLocation() {
        // Get location from GPS if it's available
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // if don't have user location permission ask for it
        requestLocationPermission();
        Log.wtf("getMyLocation", "trying to get user location");
        Location myLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        // Location wasn't found, check the next most accurate place for the current location
        if (myLocation == null) {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            // Finds a provider that matches the criteria
            String provider = lm.getBestProvider(criteria, true);
            // Use the provider to get the last known location
            myLocation = lm.getLastKnownLocation(provider);
        }
        Log.wtf("getMyLocation", "got user location: " + String.valueOf(myLocation));
        int x = 0;
        return myLocation;
    }

    /*
    * checks if ACCESS_FINE_LOCATION permission granted
    * if not asks for it
    * will not let program proceed without it
     */
    private void requestLocationPermission() {
        while (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            Log.i("!!!!!!onMapReady: ", "asking for fine location permission");
        }
        return;
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.  (for obtaining user gps
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        // TODO fix asking for permissions here.
        // Provides a simple way of getting a device's location and is well suited for
        // applications that do not require a fine-grained location and that do not need location
        // updates. Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            mUserLatitude = mLastLocation.getLatitude();
            mUserLongitude = mLastLocation.getLongitude();
        } else {
            Log.wtf(LOG_TAG, "no known LastLocation, SHITE");
            Toast.makeText(this, "no_location_detected", Toast.LENGTH_SHORT).show();
        }
        Log.wtf("getMyLocation", "got user location: " + String.valueOf(mUserLatitude));
    }

    @Override
    public void onConnectionSuspended(int i) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(LOG_TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(LOG_TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }
}

