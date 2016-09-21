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
package com.example.android.recyclingbanks;

import android.Manifest;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.ResultReceiver;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.example.android.recyclingbanks.QueryUtils.homeMarkerRaw;
import static com.example.android.recyclingbanks.QueryUtils.mBottleBanks;
import static com.example.android.recyclingbanks.QueryUtils.mCombinedBanks;
import static com.example.android.recyclingbanks.QueryUtils.mOthers;
import static com.example.android.recyclingbanks.QueryUtils.mPackagingBanks;
import static com.example.android.recyclingbanks.QueryUtils.mPaperBanks;
import static com.example.android.recyclingbanks.QueryUtils.mTextileBanks;
import static com.example.android.recyclingbanks.QueryUtils.mapCombinedMarkerToBanks;
import static com.example.android.recyclingbanks.R.id.map;
import static com.google.android.gms.common.api.GoogleApiClient.Builder;
import static com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import static com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import static com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import static com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import static com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;

//import static com.google.android.gms.analytics.internal.zzy.i;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback,
        LoaderManager.LoaderCallbacks<Boolean>, OnMarkerClickListener, OnInfoWindowClickListener,
        InfoWindowAdapter, ConnectionCallbacks, OnConnectionFailedListener {


    public static final String LOG_TAG = MainActivity.class.getName();
    //public static final String REQUEST_URL = "http://data.edinburghopendata.info/api/action/datastore_search?resource_id=4cfb5177-d3db-4efc-ac6f-351af75f9f92&limit=4757"; //4757";
    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    // Provides the entry point to Google Play services.  Protected means only accessible inside app
    protected GoogleApiClient mGoogleApiClient;
    // temporary defaults, potential race between query utils and LastknownLocation
    // may end up searching around central Edinburgh, rather than home.
    // TODO if so default to manual address?
    protected double mUserLatitude = 55.9532520;
    protected double mUserLongitude = -3.1882670;
    protected LatLng mUserLatLng;

    protected Location mLastLocation;
    /**
     * Receiver registered with this activity to get the response from FetchAddressIntentService.
     */
    private AddressResultReceiver mResultReceiver;
    // this is to keep track of if address wanted, but couldn't connect to GoogleApiClient, may not use AT ALL
    private Boolean mAddressRequested;
    // stores users address when geo-coder returns with it, this is default
    public String mAddressOutput = "Edinburgh";

    // this needs to be static, because can't pass via onSaveInstanceState, cos' activity not
    // completely destroyed, therefore need a static variable to bridge visits to child activities
    protected static LatLng EDINBURGH = new LatLng(55.9532520, -3.1882670);
    protected static CameraPosition cameraPosition = new CameraPosition.Builder().target(EDINBURGH)
                    .zoom(14).bearing(0).tilt(0).build();
    // save it in onPause, restore it upon onResume

    static final int REQUEST_IMAGE_CAPTURE = 1;
    public QuakeAdapter adapter;
    private TextView mEmptyView;
    private ProgressBar mProgressBar;
    private LinearLayout mSpinnerLayout;

    public GoogleMap m_map;
    private boolean mapReady = false;
    // to contain preferences of bottom bar
    private SharedPreferences preferences;
    // to contain preferences used here, and set in settings
    private SharedPreferences mSharedPrefs;

    private final int BUTTONonCOLOR = R.color.white;
    private final int BUTTONoffCOLOR = R.color.colorPrimaryDark;
    // maps from button id, to present button color (activated/deactivated), keeps track of state
    protected HashMap<Integer, Boolean> buttonActivationMap = new HashMap<>();

    // for combinedBanks, maps from Marker key to array indicating bankTypes present
    protected HashMap<Marker, Boolean[]> mapMarkerToBank = new HashMap<>();

    // stores the map markers once they're attached to the map, for toggling visibility
    protected List<Marker> mPackagingBankMarkers = new ArrayList<>();
    protected List<Marker> mPaperBankMarkers = new ArrayList<>();
    protected List<Marker> mTextileBankMarkers = new ArrayList<>();
    protected List<Marker> mBottleBankMarkers = new ArrayList<>();
    protected List<Marker> mOtherBankMarkers = new ArrayList<>();
    protected List<Marker> mCombinedBankMarkers = new ArrayList<>();

    private Marker homeMarkerFinal;
    protected Boolean usesGPS;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            Log.wtf("restoring state?", "should have cam pos");
            cameraPosition = savedInstanceState.getParcelable("STATE_CAMERA_POSITION");
            //mBankLatLng = bundle.getParcelable("bankLatLng");
        }
        else if (savedInstanceState == null) {
            Log.wtf("onCreate", "state not restored");
        }

        Log.i("onCreate", "seting up variable, UI etc.");
        setContentView(R.layout.bottombar_constraint_layout);
        setTitle("Recycle Banks");

        //setContentView(R.layout.earthquake_activity);
        // TODO ask for postcode and house number on first login, tell them you can change this using [icon] later
        //DownloadTask task = new DownloadTask();
        //task.execute(REQUEST_URL);
        // load preference defaults for buttons press,
        // false means we won't ever read this again after first app boot
        PreferenceManager.setDefaultValues(this, R.xml.default_preferences, true);
        // preferences contains booleans to record click status of bottom bar
        preferences = getPreferences(MODE_PRIVATE);

        mSharedPrefs = getDefaultSharedPreferences(getBaseContext());

        // Find a reference to the {@link ListView} in the layout
        ListView earthquakeListView = (ListView) findViewById(R.id.list);

        mProgressBar = (ProgressBar) findViewById(R.id.loading_spinner);
        mSpinnerLayout = (LinearLayout) findViewById(R.id.linearLayoutSpinner);
        // WANT TO create a message if the list is empty
        mEmptyView = (TextView) findViewById(R.id.emptyView);
        // by attaching this to listView, it only shows if listView is empty

        Log.i(LOG_TAG, "!!!!! launching a new LoaderManager");

        usesGPS = mSharedPrefs.getBoolean("usesGPS", false);
        if (!usesGPS) {
            // TODO
            // setup home marker
            mUserLatitude = Double.valueOf(mSharedPrefs.getString("manualLatitude", "55.9532520"));
            mUserLongitude = Double.valueOf(mSharedPrefs.getString("manualLongitude", "-3.1882670"));

            String homeAddress = mSharedPrefs.getString("homeAddress", "Edinburgh");
            Log.w("onCreate", "manual address: " + homeAddress);
            // alert user that using saved home location
            Toast.makeText(this, homeAddress + "as home, change in settings", Toast.LENGTH_SHORT );
        }
        Log.wtf("onConnected", "all prefs: " + String.valueOf(mSharedPrefs.getAll()));
        Log.wtf("onConnected", "contain usesGPS key? " + String.valueOf(mSharedPrefs.contains("usesGPS")));
        Log.wtf("onConnected", "using gps? " + String.valueOf(usesGPS));

        // Create an instance of GoogleAPIClient.  Used for getting users location
        // also used for geocoding their location to Toast their street.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    /**
     * need to REMOVE logic in onStart so it rebuilds the map after leaving preferences screen.
     * TODO move stuff from here, regarding views back to onCreate, it is messing up rotation, see onRestoreState
     * TODO custom infoWindow adapter, for displaying a streetview icon to click
     * TODO get Monkey just below spinning blue wheel
     */
    @Override
    public void onStart() {
        super.onStart();
        Log.e("onStart", "connecting and stuff");
        setupButtonColours();

        if (usesGPS) {
            mGoogleApiClient.connect();
        }
        // call this as a function, only if !usesGPS
        else if (!usesGPS) {
            getDataLoaderGoing();
        }
        // if usesGPS then function called after GOT gps, so can hand lat lons through.

//        // find out if connected to the internet
//
//
//        // if don't have user location permission ask for it
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            // && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            ActivityCompat.requestPermissions(this,
//                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
//                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
//            Log.i("!!!!!!onMapReady: ", "asking for fine location permission");
//            //return;
//        }
        // setup the map
        Log.i("onStart", "setting up the map");
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(map);
        mapFragment.getMapAsync(this);
        // this keeps pins on map on rotation, but doesn't restore references, so can't toggle them
        //mapFragment.setRetainInstance(true);



    }

    protected void getDataLoaderGoing() {
        Log.i("getDataLoaderGoing", "Loader manager next???");
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
            mSpinnerLayout.setVisibility(View.GONE);
            mEmptyView.setText("No internet connection");
        }
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        // save camera position, screen may have gone to sleep during install, so try / catch
        try {
            cameraPosition = m_map.getCameraPosition();
        }
        catch (Exception e) {
            Log.e("onPause", e.toString() );
        }
        Log.wtf("onPause", "cameraPos: " + String.valueOf(cameraPosition));
        m_map.clear();

        super.onStop();
        Log.e("onStop", "disconnecting from google, pins on map?");
        // TODO save state here, and reopen in onCreate
        Log.e("onStop", "might want to save stuff to open in onCreate if rebuilding UI");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO before launch an external activity, save camera focus LatLng to this activities intent
        // so can restore it, use saveInstanceState?
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
                // pass the geocoded home address through, for display as summary
                settingsIntent.putExtra("userAddress", mAddressOutput);
                startActivity(settingsIntent);
                break;

            case R.id.action_set_new_home_location:
                // TODO error here?
                Log.i(LOG_TAG, "going to SettingsActivity");
                settingsIntent = new Intent(this, SettingsActivity.class);
                settingsIntent.putExtra("userAddress", mAddressOutput);
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
                composeEmail(addresses, "Feedback on Edinburgh Recycle App, from map screen");
                break;
        }
        return super.onOptionsItemSelected(item);
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
     * TODO called when?  if using gps, after gps returns
     * if not !usesGPS then in onStart after got sharedPref Latlngs
     */
    //private class DownloadTask extends AsyncTask<String, Void, ArrayList<Quake>> {
    @Override
    public android.content.Loader<Boolean> onCreateLoader(int id, Bundle args) {
        Log.i("onCreateLoader", "creating new Loader");
        // This is called when a new Loader needs to be created.  This
        // sample only has one Loader, so we don't care about the ID.

        Log.i("onCreateLoader", "!!!!!!! creating new loader object using GPS: " + usesGPS);
        Log.w("Loader", "is the key present? " + String.valueOf(mSharedPrefs.contains(getString(R.string.settings_distance_from_home_key))));
        // max_radius is stored as a String in sharedprefs so cast.  Default search radius = 2000m
        int maxRadius = Integer.valueOf(mSharedPrefs.getString(getString(R.string.settings_distance_from_home_key), "2000"));
        return new EarthquakeLoader(this, maxRadius, mUserLatitude, mUserLongitude);
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
        mSpinnerLayout.setVisibility(ProgressBar.GONE);

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
        setupMap();

    }

    /**
     * places MarkerOption lists for each bankType on the map.
     * Called by onLoadFinished, and onResume, for rotation, or returning from another activity
     */
    private void setupMap() {
        Log.i("setupMap", "placing markers on map");
        // add home marker, save it, so we can identify when this marker is clicked on
        //BitmapDescriptor home_icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_home);
        //homeMarkerRaw.icon(home_icon);
        //homeMarkerFinal = m_map.addMarker(homeMarkerRaw);

        boolean packagingActivated = preferences.getBoolean("Packaging", false);
        //Log.i("packaging Button", Boolean.toString(packagingActivated));
        if (packagingActivated) {
            mPackagingBankMarkers = addBankListToMap(mPackagingBanks);
        }
        boolean paperActivated = preferences.getBoolean("Paper", false);
        //Log.e("setupMap", "is paper activated? " + Boolean.toString(paperActivated));
        if (paperActivated) {
            Log.e("setupMap", "adding paper banks to map");
            mPaperBankMarkers = addBankListToMap(mPaperBanks);
        }
        boolean textilesActivated = preferences.getBoolean("Textiles", false);
        //Log.i("textiles Button", Boolean.toString(textilesActivated));
        if (textilesActivated) {
            mTextileBankMarkers = addBankListToMap(mTextileBanks);
        }
        boolean bottlesActivated = preferences.getBoolean("Bottles", false);
        //Log.i("bottles Button", Boolean.toString(bottlesActivated));
        if (bottlesActivated) {
            mBottleBankMarkers = addBankListToMap(mBottleBanks);
        }

        boolean othersActivated = preferences.getBoolean("Others", false);
        //Log.i("others Button", Boolean.toString(othersActivated));
        if (bottlesActivated) {
            mOtherBankMarkers = addBankListToMap(mOthers);
        }

        Boolean[] bankTypesActivated = {packagingActivated, paperActivated, textilesActivated,
                bottlesActivated, othersActivated};

        mCombinedBankMarkers = addCombinedBanksToMap(mCombinedBanks, bankTypesActivated);

        // setup click event for home marker to launch change home screen
        m_map.setOnMarkerClickListener(MainActivity.this);
        // want this to launch streetview when a window is clicked
        m_map.setOnInfoWindowClickListener(MainActivity.this);
        m_map.setInfoWindowAdapter(this);
    }

    private List<Marker> addCombinedBanksToMap(List<MarkerOptions> mCombinedBanks, Boolean[] bankTypesActivated) {
        Log.i("addCombinedBanksToMap", "Marker list to map");
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
        Log.i("addBankListToMap", " banks being added to map");
        // we need to save NEW lists of map attached markers OR can't change marker visibility
        List<Marker> newBankList = new ArrayList<>();
        for (int i = 0; i < oneBankList.size(); i++) {
            MarkerOptions binMarker = oneBankList.get(i);
            Marker newMarker = m_map.addMarker(binMarker);
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
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            // send through address from gps, defaults to Edinburgh if not using gps
            settingsIntent.putExtra("userAddress", mAddressOutput);
            startActivity(settingsIntent);
        } else {
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
        //Log.i("setupButtonColours", "setting up bottom bar activation");
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
                Log.e("onClick", "num pure paper banks? " + Integer.toString(mPaperBankMarkers.size()));
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
    public void hideOrShowBanks(List bankList, boolean activated) {
        Log.w("hideOrShowBanks", "activated? " + Boolean.toString(activated));
        // check if we've loaded the marker onto the map, if haven't bankList will be empty
        for (int i = 0; i < bankList.size(); i++) {
            Marker markerToTurnOff = (Marker) bankList.get(i);
            //Log.wtf("attempting to hide/show", markerToTurnOff.getTitle() + ". Visible? " + String.valueOf(markerToTurnOff.isVisible()));
            markerToTurnOff.setVisible(activated);
            //Log.i("hideOrShowBanks", "marker turned on? " + Boolean.toString(activated));
        }
        // now filter the combinedBanks
    }

    /**
     * Called when orientation change, when switch to 2nd activity.
     * Can save String sets to prefs, but not sets of MarkerOptions
     */
    public void onPause() {
        Log.i("onPause", "saving button activation");
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
    /*
    * Save the camera position, unpack in onCreate
     */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // TODO may not be unpacking cameraPosition right, it seems to reset - wtf?
        cameraPosition = m_map.getCameraPosition();
        Log.wtf("onSaveInstanceState", "cameraPos: " + String.valueOf(cameraPosition));
        savedInstanceState.putParcelable("STATE_CAMERA_POSITION", cameraPosition);
        //savedInstanceState.putBundle("STATE_CAMERA_POSITION", args);
        if (savedInstanceState == null) {
            Log.wtf("onSaveInstanceState", "is null!!!!!!!!!");
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            cameraPosition = savedInstanceState.getParcelable("STATE_CAMERA_POSITION");
            Log.wtf("onRestoreInstanceState", "should camPos " + String.valueOf(cameraPosition));
            //mBankLatLng = bundle.getParcelable("bankLatLng");
        }
        else if (savedInstanceState == null) {
            Log.wtf("onRestore..State", "state not restored");
        }

    }

    @Override
    public void onResume(){
        super.onResume();
        Log.wtf("onResume", "device rotated?");
        // TODO load activated markers
        Log.wtf("onResume", "map ready?" + Boolean.toString(mapReady));
        if (m_map == null) {
            Log.wtf("onResume", "map null!!!!");
        }

        // TODO add activated markers
        // TODO restore gps point of focus for map camera from before
        // hide spinner monkey
        mProgressBar.setVisibility(ProgressBar.GONE);
        mSpinnerLayout.setVisibility(ProgressBar.GONE);


    }
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.e("onRestart", "test");
    }

    /**
     * Loads markers for a specific bank type.
     * hides all non relevant markers
     */
    @Override
    public void onLoaderReset(android.content.Loader<Boolean> loader) {
        Log.e("onLoaderReset", "num paper bank markers: " + String.valueOf(mPaperBankMarkers.size()));
        Log.e("onLoaderReset", "num paper bank markerOpt: " + String.valueOf(mPaperBanks.size()));
        // reset it, changed device orientation, so clear out the adapter, and connected listView
        //adapter.clear();

//        // reattach them to m_map
////        while (mapReady != true) {
////            Log.wtf("!!!onLoaderReset", "map ready? " + Boolean.toString(mapReady));
////        }
        if (cameraPosition == null) {
            Toast.makeText(this, "not got camera", Toast.LENGTH_SHORT).show();
            Log.wtf("onLoaderReset", "not got camera");
        }
        Log.wtf("!!!onLoaderReset", "map ready? " + Boolean.toString(mapReady) + " :)");
//        hideOrShowBanks(mPaperBankMarkers, true);
//        hideOrShowBanks(mPackagingBankMarkers, true);
//        addBankListToMap(mPackagingBanks);
//        setupMap();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mapReady = true;
        m_map = googleMap;

//        if (!usesGPS) {
//            getDataLoaderGoing();
//        }

        Log.i("!!!!!!onMapReady: ", "should have location permission");
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
        m_map.setMyLocationEnabled(true);

        //check if using user defined home location, or gps
        if (!usesGPS) {
            Log.i("onMapReady", "user lat: " + String.valueOf(mUserLatitude) +
                    ", user lon:" + String.valueOf(mUserLatitude));
            LatLng manualHomeLatLng = new LatLng(mUserLatitude, mUserLongitude);
            makeHomeMarker(manualHomeLatLng);
            // now move the camera
            cameraPosition = new CameraPosition.Builder().target(manualHomeLatLng)
                    .zoom(14).bearing(0).tilt(0).build();
            m_map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
        else if (cameraPosition != null) {
            // in this instance we're waiting for GPS, so just resume the last cameraPos
            m_map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            Log.wtf("onMapReady", "moving camera to old Pos");
            Toast.makeText(this, "resuming from last location", Toast.LENGTH_SHORT );
            //cameraPosition = null;
        }
        Log.wtf("onMapReady", "cameraPos: " + String.valueOf(cameraPosition));
        //setupMap();

        // center on present location zoomed in
        //Location userLocation = getMyLocation();
        //LatLng userCoords = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());

//        LatLng edinburgh = new LatLng(55.948611, -3.199935);
//        CameraPosition target = CameraPosition.builder().target(edinburgh).zoom(14).build();
//        m_map.moveCamera(CameraUpdateFactory.newCameraPosition(target));
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
        Log.i("onConnected", "connected to apiClient");
        // TODO fix asking for permissions here.
        // Provides a simple way of getting a device's location and is well suited for
        // applications that do not require a fine-grained location and that do not need location
        // updates. Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.

        //TODO need to check if I activate usesGPS in settings, that when returning it will use GPS,
        // TODO or whether this isn't called on Activity resume.
        Boolean usesGPS = mSharedPrefs.getBoolean("usesGPS", false);
        Log.wtf("onConnected", "contain usesGPS key? " + String.valueOf(mSharedPrefs.contains("usesGPS")));
        Log.wtf("onConnected", "all prefs: " + String.valueOf(mSharedPrefs.getAll()));
        //check if using user defined home location, or gps
        if (usesGPS == false) {
            Log.wtf("onConnected", "using gps? " + String.valueOf(usesGPS));
            // TODO need to make sure this happens!  Doesn't just goto EDINBURGH
            return;
        }
        Log.i("onConnected", "using GPS, seeking address");
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
            mUserLatLng = new LatLng(mUserLatitude, mUserLongitude);
            Log.i("onConnected", "got gps, now fetching String of street");

            // Determine whether a Geocoder is available to get the address from mLastLocation.
            if (Geocoder.isPresent()) { fetchAddress(); }
            else { Toast.makeText(this, R.string.no_geocoder_available, Toast.LENGTH_SHORT).show(); }
        } else {
            Log.wtf("onConnected", "lastLocation = null, huh?");
            // no gps, skip to address screen for user to enter their address
            Toast.makeText(this, "no location detected\nenter mannually", Toast.LENGTH_SHORT).show();
            // gps unavailable, change setting to not try for it
            preferences.edit().putBoolean("usesGPS", false);
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            // pass the geocoded home address through, for display as summary
            settingsIntent.putExtra("userAddress", mAddressOutput);
            startActivity(settingsIntent);
            //mUserLatLng = EDINBURGH; // co-ords of Edinburgh city center
        }
        // need to get data for the map
        Log.i("onConnected", "got gps, now getting markers with loader");
        getDataLoaderGoing();
        // TODO is this needed here?  Is it being repeated elsewhere?
        mUserLatLng = new LatLng(mUserLatitude, mUserLongitude);
        cameraPosition = new CameraPosition.Builder().target(mUserLatLng)
                .zoom(14).bearing(0).tilt(0).build();
        Log.wtf("onConnected", "cameraPos: " + String.valueOf(cameraPosition));
        m_map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        makeHomeMarker(mUserLatLng);
    }

    private void makeHomeMarker(LatLng homeLatLng) {
        BitmapDescriptor home_icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_home);
        homeMarkerRaw = new MarkerOptions().position(homeLatLng).title("home").icon(home_icon);
        homeMarkerFinal = m_map.addMarker(homeMarkerRaw);
        // TODO does this erase properly when we change it in settings and resume, or do we have 2?
        // TODO need to center the map on either home address or gps pinpoint
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
    @Override
    public void onInfoWindowClick(Marker marker) {
        Log.wtf("onInfoWindowClick", marker.getSnippet() + " clicked!");
        Toast.makeText(this, marker.getSnippet() + " clicked!", Toast.LENGTH_SHORT);
        Intent intent = new Intent(this, StreetViewFragment.class);

        // to transfer over LatLng of bank we need this:
        Bundle args = new Bundle();
        args.putParcelable("bankLatLng", marker.getPosition());
        intent.putExtra("bundle", args);

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    /*
    * The API will first call getInfoWindow(Marker) and if null is returned,
    * it will then call getInfoContents(Marker).
    * If this also returns null, then the default info window will be used.
    * To call this need to setInfoWindowApapter, and implement class
    *  Provides a view that will be used for the entire info window.
     */
    @Override
    public View getInfoWindow(Marker marker) {
        Log.i("getInfoWindow", "setting to null");
        return null;
        //return prepareInfoView(marker);
    }
    /*
    * customizes the contents of the window but keeps the default info window frame and background.
    */
    @Override
    public View getInfoContents(Marker marker) {
        Log.i("getInfoContents", "calling prepareInfoView");

        //return null;
        return prepareInfoView(marker);
    }
    private View prepareInfoView(final Marker marker){
        // TODO change this to xml?
        //prepare InfoView programmatically
        final LinearLayout infoView = new LinearLayout(MainActivity.this);
        LinearLayout.LayoutParams infoViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoView.setOrientation(LinearLayout.VERTICAL);
        // attach the above layout to the infoView
        infoView.setLayoutParams(infoViewParams);


        String markerLongitude = Double.toString(marker.getPosition().longitude);
        String markerLatitude = Double.toString(marker.getPosition().latitude);

        final String imageURL = "https://maps.googleapis.com/maps/api/streetview?size=" + "500x300&location=" +
                markerLatitude + "," + markerLongitude + "&fov=120&heading=0&pitch=0";

        //create street view preview @ top
        ImageView streetViewPreviewIV = new ImageView(MainActivity.this);

        Log.wtf("comparing TAG", String.valueOf(marker.getTag()));

        if (marker.getTag() == null ) {
            Log.i("prepareInfoView", "fetching image");
            Picasso.with(this).load(imageURL).fetch(new MarkerCallback(marker));
        }
        else {
            Log.wtf("prepareInfoView", "building info window");


            // this scales the image to match parents WIDTH?, but retain image's height??
            LinearLayout.LayoutParams streetViewImageViewParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            streetViewPreviewIV.setLayoutParams(streetViewImageViewParams);
            // TODO upon conversion to xml, the imageView needs these to scale image to box
            // android:scaleType="fitStart"
            // android:adjustViewBounds="true"
            Picasso.with(MainActivity.this).load(imageURL).into(streetViewPreviewIV);
            infoView.addView(streetViewPreviewIV);
            //Log.wtf("prepareInfoView, marker tag set?", String.valueOf(marker.getTag()));
            //Picasso.with(this).setLoggingEnabled(true);
        }

        // create text boxes
        LinearLayout subInfoView = new LinearLayout(MainActivity.this);
        LinearLayout.LayoutParams subInfoViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subInfoView.setOrientation(LinearLayout.VERTICAL);
        subInfoView.setLayoutParams(subInfoViewParams);

        TextView titleTextView = new TextView(MainActivity.this);
        titleTextView.setText(marker.getTitle());
        TextView snippetTextView = new TextView(MainActivity.this);
        snippetTextView.setText(marker.getSnippet());
        subInfoView.addView(titleTextView);
        subInfoView.addView(snippetTextView);
        infoView.addView(subInfoView);

        // add the image on the right
        ImageView streetViewIcon = new ImageView(MainActivity.this);
        // this scales the image to match parents height.
        LinearLayout.LayoutParams imageViewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        streetViewIcon.setLayoutParams(imageViewParams);
        Drawable drawable = ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_streetview);
        streetViewIcon.setImageDrawable(drawable);
        infoView.addView(streetViewIcon);
        //Picasso.with(this).load(imageURL).into(streetViewPreviewIV, new MarkerCallback(marker));

        return infoView;
    }
    public void fetchAddress() {
        Log.i("fetchAddress", "here");
        // Only start the service to fetch the address if GoogleApiClient is
        // connected.
        if (mGoogleApiClient.isConnected() && mLastLocation != null) {
            startIntentService();
        }
        // If GoogleApiClient isn't connected, process the user's request by
        // setting mAddressRequested to true. Later, when GoogleApiClient connects,
        // launch the service to fetch the address. As far as the user is
        // concerned, pressing the Fetch Address button
        // immediately kicks off the process of getting the address.
        else {
            mAddressRequested = true;
        }
    }
    /**
     * calls intentService for getting street address from phones Location.
     * Called from onConnect, after gps returns
     */
    protected void startIntentService() {
        Log.i("startIntentService", "to get the address from gps");
        mResultReceiver = new AddressResultReceiver(new Handler());
        Intent intent = new Intent(this, FetchAddressIntentService.class);
        intent.putExtra(Constants.RECEIVER, mResultReceiver);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, mLastLocation);
        startService(intent);
    }

    /**
     * Receives the result of geo-coding IntentService - address from lastKnownLocation
     */
    class AddressResultReceiver extends ResultReceiver {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            Log.i("onReceiveResult", "displaying user street?");
            // Display the address string or an error message sent from the intent service.
            mAddressOutput = resultData.getString(Constants.RESULT_DATA_KEY);

            // Show a toast message if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT) {
                Toast.makeText(MainActivity.this, "Search around: "
                        + mAddressOutput, Toast.LENGTH_SHORT).show();
            }

        }
    }
}