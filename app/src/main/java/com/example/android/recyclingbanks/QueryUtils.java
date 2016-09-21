package com.example.android.recyclingbanks;

import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED;

/**
 * Helper methods related to requesting and receiving earthquake data from USGS.
 */
public final class QueryUtils {

    public static final String LOG_TAG = QueryUtils.class.getName();
    public static final String USGS = "http://data.edinburghopendata.info/api/action/datastore_search?resource_id=4cfb5177-d3db-4efc-ac6f-351af75f9f92&limit=500";

    // public so avoid endless getter methods
    public static List<MarkerOptions> mPackagingBanks = new ArrayList<>();
    public static List<MarkerOptions> mPaperBanks = new ArrayList<>();
    public static List<MarkerOptions> mBottleBanks = new ArrayList<>();
    public static List<MarkerOptions> mTextileBanks = new ArrayList<>();
    public static List<MarkerOptions> mOthers = new ArrayList<>();
    // for multiple banks clustered at same location
    public static List<MarkerOptions> mCombinedBanks = new ArrayList<>();

    public static MarkerOptions homeMarkerRaw = new MarkerOptions();

    // dictionary: combinedBank Marker keys to value [a array of bank type keys to true / false values]
    public static HashMap<MarkerOptions, Boolean[]> mapCombinedMarkerToBanks = new HashMap<>();

    //private final static HashMap<String, Float> HASHMAP = new HashMap<String, Float>();

    /**
     * Create a private constructor because no one should ever create a {@link QueryUtils} object.
     * This class is only meant to hold static variables and methods, which can be accessed
     * directly from the class name QueryUtils (and an object instance of QueryUtils is not needed).
     */
    private QueryUtils() {
    }

    /**
     * Return a list of {@link Quake} objects that has been built up from
     * parsing a JSON response.
     */
//    public static Boolean extractBanksList(String urlString, int distanceFromHome) {
//        URL url = createUrl(urlString);
//
//        String jsonResponse = null;
//        //Log.i(LOG_TAG, "!!! extractBanksList asking for data");
////        try {
////            Thread.sleep(2000);
////        } catch (InterruptedException e) {
////            e.printStackTrace();
////        }
//        try {
//            jsonResponse = makeHttpRequest(url);
//        } catch (IOException e) {
//            e.printStackTrace();
//            //Log.e(LOG_TAG, "!!! Problem making the HTTP request.", e);
//        }
//        //Log.i(LOG_TAG, "!!!! extractBanksList got data, sending to be parsed");
//
//        // now send back to AsyncTask for onPostExecute display to UI
//        //Log.w(LOG_TAG, "!!!! extractBanksList returning globalBanksList list");
//        //Log.wtf(LOG_TAG, "num markers: " + String.valueOf(globalBanksList.size()));
//
//        // returns true or false
//        return extractFeatureFromJson2(jsonResponse, distanceFromHome);
//    }

    public static Boolean extractBanksList(InputStream is, int distanceFromHome, Double homeLatitude, Double homeLongitude) {
        String jsonResponse = null;
        try {
            //AssetManager assetManager = getBaseContext().getAssets();
            //InputStream is =  assetManager.open("recycleBanksOffline.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            jsonResponse = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }

        return extractFeatureFromJson2(jsonResponse, distanceFromHome, homeLatitude, homeLongitude);
    }
    /**
     * Returns new URL object from the given string URL.
     */
    private static URL createUrl(String stringUrl) {
        URL url = null;
        try {
            url = new URL(stringUrl);
        } catch (MalformedURLException e) {
            //Log.e(LOG_TAG, "Error with creating URL ", e);
        }
        return url;
    }

    /**
     * extracts and sorts features offline
     */
//    public static ArrayList<Quake> extractFeatureFromOfflineJson(String JSONstring) {
//        return extractFeatureFromJson(JSONstring);
//    }
    // TODO: investigate clusters, cluster manager
    // TODO: investigate showing only what's in viewport

    /**
     * tells us whether to exclude a bank or include it, and so build a marker
     * @param startLatitude of users house
     * @param startLongitude of users house
     * @param  bankLatitude latitude of bank
     * @param  bankLongitude longitude of bank
     * @param distanceFromHome user set radius from house
     * @return whether recycle bank is within distanceFromHome radius
     */
    private static Boolean notInRangeOfLocation(Double startLatitude, Double startLongitude,
                                                Double bankLatitude, Double bankLongitude, int distanceFromHome) {
        float results[] = new float[3];
        //Log.d("notInRangeOfLocation", String.valueOf(distanceFromHome));
        Location.distanceBetween(startLatitude, startLongitude, bankLatitude, bankLongitude, results);
        int distanceInMeters = (int) results[0];
        //Log.wtf(LOG_TAG, "distance from start to pt in m:" + results[0]);
        // Computes the approximate distance in meters between two locations, and optionally the initial and final bearings of the shortest path between them.
        if (distanceInMeters > distanceFromHome) {
            //Log.i("LOG_TAG", "Too far thrown one out: " + String.valueOf(distanceInMeters));
            return true;
        }
        else {
            //Log.i("LOG_TAG", "In range? " + String.valueOf(distanceInMeters));
            // in range of location
            return false;
        }
    }
    /**
     * IN CASE we have a bank at the same location, process the bank_type to remove the appendix word "Bank"
     */
    private static String removeBankFromString(String bank_type) {
        int split_point = bank_type.indexOf("Bank");

        if (split_point == -1) {
            // doesn't include the words bank
            bank_type = bank_type + " ";
        }
        else {
            // remove the word 'bank', all bottle banks get compressed
            bank_type = bank_type.substring(0, split_point);
        }
        return bank_type;

    }
    /**
     * Takes raw JSON, modifies multiple lists, returns false if can't get a JSON file
     * @param earthquakeJSON
     * @param homeLatitude
     *@param homeLongitude @return a boolean, false if no JSON to process
     */
    private static boolean extractFeatureFromJson2(String earthquakeJSON, int distanceFromHome, Double homeLatitude, Double homeLongitude) {
        String LOG_TAG = "extracting Features";
        // If the JSON string is empty or null, then return early.

        if (TextUtils.isEmpty(earthquakeJSON)) {
            // why we need to return something, to catch this
            Log.wtf(LOG_TAG, "no JSON retrieved ALERT!!!");
            return true;
        }

        // map from bankType to it's presence,
        HashMap<String, Boolean> mapBankTypes = new HashMap<String, Boolean>();
        // Try to parse the SAMPLE_JSON_RESPONSE. If there's a problem with the way the JSON
        // is formatted, a JSONException exception object will be thrown.
        // Catch the exception so the app doesn't crash, and print the error message to the logs.
        try {
            // build up a list of objects with the corresponding data.

            // Convert SAMPLE_JSON_RESPONSE String into a JSONObject
            JSONObject jsonRootObject = new JSONObject(earthquakeJSON);

            // Extract “records” JSONArray
            JSONArray jsonRecordsArray = jsonRootObject.optJSONObject("result").optJSONArray("records");
            //Log.i(LOG_TAG, "trying to get records, are they root?");
            // SAM ARE RECORDS IN ROOT, OR IN FACT IN ROOT/RESULT?

            // grab first bank JSON object
            JSONObject jsonComparisonRecordObject = jsonRecordsArray.getJSONObject(0);
            // build it's location
            Double bankLatitude = Double.parseDouble(jsonComparisonRecordObject.optString("Latitude"));
            Double bankLongitude = Double.parseDouble(jsonComparisonRecordObject.optString("Longitude"));
            LatLng locationLatLngToCompare = new LatLng(bankLatitude, bankLongitude);
            // TODO what if the very first bank is out of range of home?  Loop till find one in range?

            // save the bank type to snippet_builder so can append other Banks
            String prior_bank_type = jsonComparisonRecordObject.optString("BankTypeNa");
            String bank_type_map_title = removeBankFromString(prior_bank_type);
            // extract location details
            String prior_location_details = jsonComparisonRecordObject.optString("RecyclingS");

            // setup my HOUSE
            //LatLng myPresentLocation = new LatLng(55.942835, -3.218902);
            // TODO what is this for?
            Location myPresentLocation = new Location("");
            myPresentLocation.setLatitude(55.942835);
            myPresentLocation.setLongitude(-3.218902);

//            double homeLatitude = 55.942835;
//            double homeLongitude = -3.218902;
            LatLng houseLatLng = new LatLng(homeLatitude, homeLongitude);
//            // make house marker available for placing on map
//            buildMyHouse(houseLatLng);
            // TODO have to pass them through, ARGGG!
//            SharedPreferences sharedPrefs = getDefaultSharedPreferences(this);
//            Double mUserLatitude = Double.valueOf(mSharedPrefs.getString("manualLatitude", "55.9532520"));
//            Double mUserLongitude = Double.valueOf(mSharedPrefs.getString("manualLatitude", "-3.1882670"));

            // create a flag for many banks at same location
            Boolean cluster = false;
            // declare object for next location
            LatLng locationLatLng;
            String bank_type;
            // for combinedMarkers this store which bankTypes are present @ the location
            Boolean[] bankTypesArray = new Boolean[5];
            Arrays.fill(bankTypesArray, Boolean.FALSE);

            for (int i = 1; i < jsonRecordsArray.length(); i++) {
                // Every time round the loop we get a fresh Bank
                JSONObject jsonRecordObject = jsonRecordsArray.getJSONObject(i);
                bankLatitude = Double.parseDouble(jsonRecordObject.optString("Latitude"));
                bankLongitude = Double.parseDouble(jsonRecordObject.optString("Longitude"));

                if (notInRangeOfLocation(homeLatitude, homeLongitude, bankLatitude, bankLongitude, distanceFromHome)) {
                    // TODO build all banks and save them into 0.5km increment distance buckets?
                    continue;
                }
                locationLatLng = new LatLng(bankLatitude, bankLongitude);

                // make sure the NEW bank type is available to everything by getting it here
                bank_type = jsonRecordObject.optString("BankTypeNa");


                // we compare latitude and longitude because the addresses have been erroneously
                // entered in the data. Same latlong co-ords will only display last marker made
                if (locationLatLngToCompare.equals(locationLatLng)) {
                    // create lean version of title ready for concatentation
                    bank_type = removeBankFromString(bank_type);
                    // if not had a collision before, then we need to flip the bundleBinArray for
                    // the founding member of the cluster, and the collision entry (later)
                    if(cluster != true) {
                        cluster = true;
                        // starting a new cluster so reset bankTypes Array
                        bankTypesArray = new Boolean[5];
                        Arrays.fill(bankTypesArray, Boolean.FALSE);
                        bankTypesArray = recordBankTypes(bank_type, bankTypesArray);

                        //TODO potential invisible error in MainActivity
                        // make sure when getting from mapBankTypes we lookup using compost Bins
                    }
                    //Log.w("BANK Cluster", bank_type);

                    // check to see if we've already recorded this bank type
                    if (bank_type_map_title.contains(bank_type)) {
                        // already got it
                        //Log.wtf(LOG_TAG, "duplicate bank, discarding: " + bank_type);
                        continue;
                    }
                    else {
                        // don't have a duplicate bank @ location so add to marker title
                        bank_type_map_title += bank_type;
                        // record the bank type in hashMap
                        bankTypesArray = recordBankTypes(bank_type, bankTypesArray);
                        //mapBankTypes.put(bank_type, true);
                    }
                    //Log.wtf("Snippet", bank_type_map_title);
                    // if they are on the same co-ords we don't need to save the rest of the info.
                    // for the global list, so move to next jsonRecord
                    continue;
                }
                else {
                    // we don't have same gps, so new location, pack away old location or cluster
                    clearAwayPriorBank(locationLatLngToCompare, bank_type_map_title, prior_bank_type, prior_location_details, bankTypesArray, cluster);

                    // IN ALL CASES OF A FRESH LOCATION WE:
                    // set cluster flag indicating we don't have multiple banks for new location yet
                    cluster = false;
                    // extract bank type
                    prior_bank_type = jsonRecordObject.optString("BankTypeNa");
                    // copy the bank_type IN CASE this is the first member of a cluster INEFFICIENT?
                    bank_type_map_title = removeBankFromString(prior_bank_type);
                    // extract location details
                    prior_location_details = jsonRecordObject.optString("RecyclingS").toString();

                    // reset locationToCompare to present bank's location
                    locationLatLngToCompare = buildNewLocation(jsonRecordObject);


                    continue;
                }
            }
            // pack away present location - last in the list
            clearAwayPriorBank(locationLatLngToCompare, bank_type_map_title, prior_bank_type, prior_location_details, bankTypesArray, cluster);
            //Log.wtf(LOG_TAG, "finished all parsing, final records uploaded");
        } catch (JSONException e) {
            // If an error is thrown when executing any of the above statements in the "try" block,
            // catch the exception here, so the app doesn't crash. Print a log message
            // with the message from the exception.
            Log.e(LOG_TAG, "Problem parsing the earthquake JSON results", e);
        }
        //printBanksList(globalBanksList);
        return false;
    }

    private static Boolean[] recordBankTypes(String bank_type, Boolean[] bankTypesArray) {
        // the bankType received here has been edited for making a concantenated multi-bank title
        switch (bank_type) {
            case "Packaging ":
                bankTypesArray[0] = true;
                break;
            case "Paper ":
                bankTypesArray[1] = true;
                break;
            case "Textile ":
                bankTypesArray[2] = true;
                break;
            case "Bottle ":
                bankTypesArray[3] = true;
                break;
            case "Can ":
            case "Plastic ":
            case "Book ":
            case "Compost Bins ":
                bankTypesArray[4] = true;
                break;
            default:
                Log.wtf(LOG_TAG, "combined bank with unknown banktype: " + bank_type);
                break;
        }
        return bankTypesArray;
    }

    private static void clearAwayPriorBank(LatLng locationLatLngToCompare, String bank_type_map_title, String bank_type, String prior_location_details, Boolean[] bankTypesArray, Boolean cluster) {
        if( cluster == true ) {
            MarkerOptions newCombinedMarker = buildAndSortAMarker(locationLatLngToCompare, bank_type_map_title, prior_location_details);
            mapCombinedMarkerToBanks.put(newCombinedMarker, bankTypesArray);
        }
        else{
            // not a cluster, just a lone bank, don't need to map stuff for recall
            buildAndSortAMarker(locationLatLngToCompare, bank_type, prior_location_details);
        }
    }

    /**
     * builds a new marker
     * @param location of bin, Latlng object
     * @param bankType a String
     * @param locationDetails String with specifics
     * @param markerColour an integer
     */
    private static MarkerOptions buildAmarker(LatLng location, String bankType, String locationDetails,
                                              float markerColour) {

        return new MarkerOptions().position(location).title(bankType)
                .snippet(locationDetails)
                .icon(BitmapDescriptorFactory.defaultMarker(markerColour));
        //.snippet(siteName + " | " + locationDetails);
    }
    private static void buildMyHouse(LatLng location) {
        // TODO need to scale to be larger
        String title = "home";
        BitmapDescriptor home_icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_home);
        homeMarkerRaw = new MarkerOptions().position(location).title(title).icon(home_icon);
        return;
    }

    private static MarkerOptions buildAndSortAMarker(LatLng location, String bank_type, String locationDetails) {
        float markerColour;
        MarkerOptions newMarker;

        switch (bank_type) {
            case "Packaging":
                markerColour = BitmapDescriptorFactory.HUE_YELLOW;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mPackagingBanks.add(newMarker);
                break;
            case "Paper Bank":
                markerColour = BitmapDescriptorFactory.HUE_MAGENTA;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mPaperBanks.add(newMarker);
                break;
            case "Textile Bank":
                markerColour = BitmapDescriptorFactory.HUE_ORANGE;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mTextileBanks.add(newMarker);
                break;
            case "Bottle Bank - Mixed":
                markerColour = BitmapDescriptorFactory.HUE_GREEN;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mBottleBanks.add(newMarker);
                break;
            case "Bottle Bank - Green":
            case "Bottle Bank - Clear":
            case "Bottle Bank - Brown":
                markerColour = BitmapDescriptorFactory.HUE_GREEN;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mBottleBanks.add(newMarker);
                break;
            // these three come under 'others' on GUI
            case "Can Banks":
                markerColour = BitmapDescriptorFactory.HUE_ROSE;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mOthers.add(newMarker);
                break;
            case "Book Bank":
                markerColour = BitmapDescriptorFactory.HUE_ORANGE;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mOthers.add(newMarker);
                break;
            case "Compost Bins":
                markerColour = BitmapDescriptorFactory.HUE_VIOLET;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mOthers.add(newMarker);
                break;
            case "Plastic Bank":
                markerColour = BitmapDescriptorFactory.HUE_AZURE;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mOthers.add(newMarker);
                break;
            default:
                // for compressed markers, replace with rainbow marker?
                markerColour = HUE_RED;
                newMarker = buildAmarker(location, bank_type, locationDetails, markerColour);
                mCombinedBanks.add(newMarker);
                break;
        }
        return newMarker;
    }

    /**
     * builds a new location object
     */
    private static LatLng buildNewLocation(JSONObject jsonRecordObject) {
        Double longitude = Double.parseDouble(jsonRecordObject.optString("Longitude"));
        Double latitude = Double.parseDouble(jsonRecordObject.optString("Latitude"));
        return new LatLng(latitude, longitude);
    }
    /**
    * Get bank object fields.  Save to MasterList.  sortToBankType.
     * Only sorting things in range at mo
     */
//    private static void buildAbank(JSONObject jsonRecordObject, LatLng locationLatLng, int i) {
//        // extract site name
//        String site_name = jsonRecordObject.optString("Site_Name").toString();
//        // extract bank type
//        String bank_type = jsonRecordObject.optString("BankTypeNa").toString();
//        // extract location details
//        String location_details = jsonRecordObject.optString("RecyclingS").toString();
//        // extract location ID. Potentially useful for reporting on full banks
//        int bank_ID = Integer.parseInt(jsonRecordObject.optString("_id"));
//        // Create Bank java object from fields
//        Quake jsonBank = new Quake(bank_ID, site_name, bank_type, locationLatLng, location_details);
//        //Log.i("NEW BANK OBJECT: ", site_name + " " + bank_type + " " + location_details);
//
//        //sortToBankType(jsonBank, bank_type, i);
//        masterBanksList.add(jsonBank);
//
//    }

    /**
     * Make an HTTP request to the given URL and return a String as the response.
     */
    private static String makeHttpRequest(URL url) throws IOException {
        String jsonResponse = "";

        // If the URL is null, then return early.
        if (url == null) {
            return jsonResponse;
        }

        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(10000 /* milliseconds */);
            urlConnection.setConnectTimeout(15000 /* milliseconds */);
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            // If the request was successful (response code 200),
            // then read the input stream and parse the response.
            if (urlConnection.getResponseCode() == 200) {
                inputStream = urlConnection.getInputStream();
                jsonResponse = readFromStream(inputStream);
            } else {
                Log.e(LOG_TAG, "Error response code: " + urlConnection.getResponseCode());
            }
        } catch (IOException e) {
            Log.wtf(LOG_TAG, "Problem retrieving the earthquake JSON results.", e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
        return jsonResponse;
    }

    /**
     * Convert the {@link InputStream} into a String which contains the
     * whole JSON response from the server.
     */
    private static String readFromStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
            BufferedReader reader = new BufferedReader(inputStreamReader);
            String line = reader.readLine();
            while (line != null) {
                output.append(line);
                line = reader.readLine();
            }
        }
        return output.toString();
    }
}
