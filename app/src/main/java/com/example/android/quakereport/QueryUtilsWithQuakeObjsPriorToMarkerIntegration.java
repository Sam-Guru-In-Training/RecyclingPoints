package com.example.android.quakereport;

import android.location.Location;
import android.text.TextUtils;
import android.util.Log;

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
import java.util.List;

/**
 * Helper methods related to requesting and receiving earthquake data from USGS.
 */
public final class QueryUtilsWithQuakeObjsPriorToMarkerIntegration {

    public static final String LOG_TAG = QueryUtilsWithQuakeObjsPriorToMarkerIntegration.class.getName();
    public static final String USGS = "http://data.edinburghopendata.info/api/action/datastore_search?resource_id=4cfb5177-d3db-4efc-ac6f-351af75f9f92&limit=500";

    // public lists of pointers to bank types in the master list
    // public so avoid endless getter methods
    public static List<Integer> mPackagingBanks = new ArrayList<Integer>();
    public static List<Integer> mPaperBanks = new ArrayList<Integer>();
    public static List<Integer> mBottleBanks = new ArrayList<Integer>();
    public static List<Integer> mColouredBottleBanks = new ArrayList<Integer>();
    public static List<Integer> mTextileBanks = new ArrayList<Integer>();
    public static List<Integer> mOthers = new ArrayList<Integer>();
    public static List<Integer> mBookBanks = new ArrayList<Integer>();
    public static List<Integer> mCompostBins = new ArrayList<Integer>();

    // Keep a list of every recycle Bank
    public static ArrayList<Quake> masterBanksList = new ArrayList<Quake>();
    // a filtered list, that doesn't have multiple banks @ the same location
    public static ArrayList<Quake> globalBanksList = new ArrayList<Quake>();

    /**
     * Create a private constructor because no one should ever create a {@link QueryUtilsWithQuakeObjsPriorToMarkerIntegration} object.
     * This class is only meant to hold static variables and methods, which can be accessed
     * directly from the class name QueryUtils (and an object instance of QueryUtils is not needed).
     */
    private QueryUtilsWithQuakeObjsPriorToMarkerIntegration() {
    }

    /**
     * Return a list of {@link Quake} objects that has been built up from
     * parsing a JSON response.
     */
    public static ArrayList<Quake> extractBanksList(String urlString, int distanceFromHome) {
        URL url = createUrl(urlString);

        String jsonResponse = null;
        //Log.i(LOG_TAG, "!!! extractBanksList asking for data");
//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        try {
            jsonResponse = makeHttpRequest(url);
        } catch (IOException e) {
            e.printStackTrace();
            //Log.e(LOG_TAG, "!!! Problem making the HTTP request.", e);
        }
        //Log.i(LOG_TAG, "!!!! extractBanksList got data, sending to be parsed");
        ArrayList<Quake> masterBanksList = extractFeatureFromJson(jsonResponse, distanceFromHome);
        // now send back to AsyncTask for onPostExecute display to UI
        //Log.w(LOG_TAG, "!!!! extractBanksList returning globalBanksList list");
        //Log.wtf(LOG_TAG, "num markers: " + String.valueOf(globalBanksList.size()));
        printBanksList(globalBanksList);
        return globalBanksList;
    }


    /**
     * prints global banks list to logs
     */
    private static void printBanksList(ArrayList<Quake> banksList) {
        if (banksList.size() == 0) {
            //Log.wtf("printsBanksList", "no banks in list WTF?!");
        }
        for(int i = 0; i < banksList.size(); i++) {
            Quake bin = banksList.get(i);
            LatLng location = bin.getLocation();
            String binType = bin.getBankType();
            String siteName = bin.getSiteName();
            String locationDetails = bin.getLocationDetails();
            //Log.w("!!!++Bin to map", siteName + " " + String.valueOf(location));
        }
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
     * Takes raw JSON, modifies multiple lists, returns a list of location-compressed bank objects
     * @param earthquakeJSON
     * @return globalBanksList
     */
    private static ArrayList<Quake> extractFeatureFromJson(String earthquakeJSON, int distanceFromHome) {
        String LOG_TAG = "extracting Features";
        // If the JSON string is empty or null, then return early.
        if (TextUtils.isEmpty(earthquakeJSON)) {
            return null;
        }

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
            LatLng locationLatLngToCompare = buildNewLocation(jsonComparisonRecordObject);
            // Create Bank java object, save to masterList and sort to BankType
            buildAbank(jsonComparisonRecordObject, locationLatLngToCompare, 0);

            // create a flag for many banks at same location
            Boolean cluster = false;
            // save the bank type to snippet_builder so can append other Banks
            String bank_type = jsonComparisonRecordObject.optString("BankTypeNa").toString();
            String bank_type_map_title = removeBankFromString(bank_type);

            // my HOUSE
            //LatLng myPresentLocation = new LatLng(55.942835, -3.218902);
            Location myPresentLocation = new Location("");
            myPresentLocation.setLatitude(55.942835);
            double startLatitude = 55.942835;
            myPresentLocation.setLongitude(-3.218902);
            double startLongitude = -3.218902;

            for (int i = 1; i < jsonRecordsArray.length(); i++) {
                // Every time round the loop we get a fresh Bank
                JSONObject jsonRecordObject = jsonRecordsArray.getJSONObject(i);
                if (notInRangeOfLocation(startLatitude, startLongitude, jsonRecordObject, distanceFromHome)) {
                    // TODO We need to build all banks and save them to GLOBAL_LIST, with a subset being GLOBAL_DISPLAY_LIST
                    continue;
                }
                LatLng locationLatLng = buildNewLocation(jsonRecordObject);
                // setup new marker, save to masterList and sort to type
                buildAbank(jsonRecordObject, locationLatLng, i);
                // make sure the NEW bank type is available to everything by getting it here
                bank_type = jsonRecordObject.optString("BankTypeNa").toString();


                // we compare latitude and longitude because the addresses have been erroneously
                // entered in the data. Same latlong co-ords will only display last marker made
                if (locationLatLngToCompare.equals(locationLatLng)) {

                    cluster = true;
                    //Log.w("BANK Cluster", bank_type);
                    bank_type = removeBankFromString(bank_type);
                    // check to see if we've already recorded this bank type
                    if (bank_type_map_title.contains(bank_type)) {
                        // already got it
                        //Log.wtf(LOG_TAG, "duplicate bank, discarding: " + bank_type);
                        continue;
                    }
                    // don't have a duplicate bank @ location so add to marker title
                    bank_type_map_title += bank_type;
                    //Log.wtf("Snippet", bank_type_map_title);
                    // if they are on the same co-ords we don't need to save the rest of the info.
                    // for the global list, so move to next jsonRecord
                    continue;
                }
                else {
                    // we don't have same gps, so new location, pack away old location or cluster
                    clearToGlobalBankList(cluster, bank_type_map_title);

                    // IN ALL CASES OF A FRESH LOCATION WE:
                    // set cluster flag indicating we don't have multiple banks for new location yet
                    cluster = false;
                    bank_type_map_title = removeBankFromString(bank_type);;

                    // reset locationToCompare to present bank's location
                    //locationLatLngToCompare = (LatLng) locationLatLng.clone();
                    locationLatLngToCompare = buildNewLocation(jsonRecordObject);
                    continue;
                }
            }
            clearToGlobalBankList(cluster, bank_type_map_title);
            //Log.wtf(LOG_TAG, "finished all parsing, final records uploaded");
        } catch (JSONException e) {
            // If an error is thrown when executing any of the above statements in the "try" block,
            // catch the exception here, so the app doesn't crash. Print a log message
            // with the message from the exception.
            //Log.e("QueryUtils", "Problem parsing the earthquake JSON results", e);
        }
        //printBanksList(globalBanksList);
        return globalBanksList;
    }
    /**
     * extracts and sorts features offline
     */
//    public static ArrayList<Quake> extractFeatureFromOfflineJson(String JSONstring) {
//        return extractFeatureFromJson(JSONstring);
//    }
    // TODO: investigate clusters, cluster manager
    // TODO: investigate showing only what's in viewport
    private static Boolean notInRangeOfLocation(Double startLatitude, Double startLongitude,
                                                JSONObject jsonRecordObject, int distanceFromHome) {
        float results[] = new float[3];
        //Log.d("notInRangeOfLocation", String.valueOf(distanceFromHome));
        double bankLatitude = Double.parseDouble(jsonRecordObject.optString("Latitude"));
        double bankLongitude = Double.parseDouble(jsonRecordObject.optString("Longitude"));
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
            return false;
        }
    }
    /**
     * we have an bank at the same location, process the bank_type to remove the appendix word "Bank"
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
     * clear away old location or cluster to global list, so we can set a new comparison location
     */
    private static void clearToGlobalBankList(Boolean cluster, String bank_type_marker_title) {
        // pack away prior bank, or prior bank cluster
        // Deal with last location if cluster
        if (cluster == true) {
        //if (bank_type_marker_title != "") {
            // there's a cluster of markers being compressed and sent to globalBanksList
            //Log.w("BANK CLUSTER: ", "clearing to global list");
            // remove trailing space
            bank_type_marker_title =
                    bank_type_marker_title.trim(); //.substring(0, bank_type_marker_title.length()-1);
            // retrieve the last quake
            try {
                Quake priorBank = (Quake) masterBanksList.get(masterBanksList.size() - 1).clone();
                priorBank.changeBankType(bank_type_marker_title);
                globalBanksList.add(priorBank);
                //Log.wtf("UPLOADED CLUSTER?", bank_type_marker_title);
                printBanksList(globalBanksList);
            } catch (CloneNotSupportedException e) {
                //Log.wtf(LOG_TAG, "FAILURE TO CLONE FOR GLOBAL BANK LIST");
                e.printStackTrace();
            }
        } else {
            // don't have a cluster of markers,
            // add the prior marker to globalBanksList
            //Log.wtf("UPLOAD LONE WOLF?", bank_type_marker_title + String.valueOf(masterBanksList.size()));
            // TODO there may be an error here, if masterList has one element, then might try and get -1th element
            Quake priorBank = (Quake) masterBanksList.get(masterBanksList.size() - 2);
            globalBanksList.add(priorBank);
            //Log.wtf("UPLOADED LONE WOLF?", bank_type_marker_title);
            //printBanksList(globalBanksList);
        }
        // cannot add to globalList here because might have thrown an exception
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
    private static void buildAbank(JSONObject jsonRecordObject, LatLng locationLatLng, int i) {
        // extract site name
        String site_name = jsonRecordObject.optString("Site_Name").toString();
        // extract bank type
        String bank_type = jsonRecordObject.optString("BankTypeNa").toString();
        // extract location details
        String location_details = jsonRecordObject.optString("RecyclingS").toString();
        // extract location ID. Potentially useful for reporting on full banks
        int bank_ID = Integer.parseInt(jsonRecordObject.optString("_id"));
        // Create Bank java object from fields
        Quake jsonBank = new Quake(bank_ID, site_name, bank_type, locationLatLng, location_details);
        //Log.i("NEW BANK OBJECT: ", site_name + " " + bank_type + " " + location_details);

        sortToBankType(jsonBank, bank_type, i);
        masterBanksList.add(jsonBank);

    }

    /**
     * builds a new marker
     * @param location of bin
     * @param binType
     * @param locationDetails String with specifics
     * @param markerColour an integer
     */
    private static void buildAmarker(LatLng location, String binType, String locationDetails,
                                     int markerColour) {
        //String locationDetails = bin.getLocationDetails();
        MarkerOptions binMarker = new MarkerOptions().position(location).title(binType)
                .snippet(locationDetails)
                .icon(BitmapDescriptorFactory.defaultMarker(markerColour));
        //.snippet(siteName + " | " + locationDetails);
    }

    /*
    * a sorting hat for recycle banks,
    * There's a global list, a master list, and each bank type has an array tracking members of the
     * master list which are part of their clan.
     */
    private static void sortToBankType(Quake jsonBank, String bank_type, int bank_index) {
        switch (bank_type) {
            case "Packaging":
                mPackagingBanks.add(bank_index);
                return;
            case "Paper Bank":
                mPaperBanks.add(bank_index);
                return;
            case "Bottle Bank - Mixed":
                mBottleBanks.add(bank_index);
                return;
            case "Textile Bank":
                mTextileBanks.add(bank_index);
                return;
            case "Bottle Bank - Green":
                mColouredBottleBanks.add(bank_index);
                return;
            case "Bottle Bank - Clear":
                mColouredBottleBanks.add(bank_index);
                return;
            case "Bottle Bank - Brown":
                mColouredBottleBanks.add(bank_index);
                return;
            case "Can Banks":
                mOthers.add(bank_index);
                return;
            case "Book Bank":
                mBookBanks.add(bank_index);
                return;
            case "Compost Bins":
                mCompostBins.add(bank_index);
                return;
            default:
                //Log.wtf("WTF!? Weird bank been added?", bank_type);
                return;
        }
    }
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
