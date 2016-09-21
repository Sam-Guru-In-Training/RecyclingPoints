package com.example.android.recyclingbanks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    // need to be able to use this globally :(
    static Preference homeAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
    }

    public static class SettingsPreferenceFragment extends PreferenceFragment
            implements Preference.OnPreferenceChangeListener{
        private Context mContext;
        // added context stuff 22.44 19/09

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings_main);

            Preference maxDistance = findPreference(getString(R.string.settings_distance_from_home_key));
            bindPreferenceSummaryToValue(maxDistance);

            homeAddress = findPreference(getString(R.string.settings_set_home_location_key));
            bindPreferenceSummaryToValue(homeAddress);
            Log.wtf("PreferenceFragment", "attempting to connect gps field");
            Preference usesGPS = findPreference(getString(R.string.settings_gps_key));
            // TODO could reverse geocode this here, then save both to prefs?
            Log.wtf("PreferenceFragment", "attempting to connect gps field AGAIN");
            //bindPreferenceSummaryToValue(usesGPS);

            String usersGPSaddress = getActivity().getIntent().getExtras().getString("userAddress");
            usesGPS.setSummary(usersGPSaddress);
            bindGPStoValue(usesGPS);

        }
        // doing this to get context, fires when attached to activity
        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            mContext = context;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            // TODO check if address / postcode is valid
            if (preference.getKey() == getString(R.string.settings_gps_key)) {
                Preference homeAddress = findPreference(getString(R.string.settings_set_home_location_key));
                boolean useGPS = (boolean) value;
                // turn on/off the manual entry
                homeAddress.setEnabled(!useGPS);
                // save the preference, to check in parent activity
                // TODO check this is retrieved correctly
                //preference.getEditor().putBoolean("useGPS", useGPS).apply();
                Log.wtf("onPrefChange", "using gps: " + String.valueOf(useGPS));
                // TODO move to onPause?
                return true;
            }
            if (preference.getKey() == getString(R.string.settings_set_home_location_key)) {
                Log.i("onPrefChange", "getting postcode");
                String postcode = value.toString();
                if(postcodeInvalid(postcode)) {
                    // if it looks wrong, exit
                    return false;
                }
                preference.setSummary(postcode);
                // TODO bug? if hasn't fired onAttach by this point it may not be able to pass context
                // temporarily changed home address to postcode, now look it up.
                Log.wtf("onPrefChange", "launching AsyncTask");
                FetchAddressTask task = new FetchAddressTask(mContext);
                task.execute(postcode);
                return true;
            }
            String stringValue = value.toString();
            preference.setSummary(stringValue);
            return true;
        }

        private boolean postcodeInvalid(String postcode) {
            int numDigits = postcode.replaceAll("\\D", "").length();
            Log.i("postcodeInvalid", "postcode now: " + postcode);
            if(postcode.length() > 8 || numDigits > 4) {
                Toast.makeText(mContext, "postcode invalid, re-enter", Toast.LENGTH_LONG).show();
                return true;
            }
            return false;
        }

        private void bindPreferenceSummaryToValue(Preference preference) {
            preference.setOnPreferenceChangeListener(this);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(preference.getContext());
            String preferenceString = preferences.getString(preference.getKey(), "");
            onPreferenceChange(preference, preferenceString);
        }

        /**
         * GPS checkbox is stored in memory as a boolean so got to treat it differently to textFields
         */
        private void bindGPStoValue(Preference preference) {
            preference.setOnPreferenceChangeListener(this);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(preference.getContext());
            Log.wtf("bindingPreferenceToScreen", "getting boolean from memory");
            Boolean preferenceBoolean = preferences.getBoolean(preference.getKey(), true);
            onPreferenceChange(preference, preferenceBoolean);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_webview_feedback) {
            String[] addresses = new String[1];
            composeEmail(addresses, "Suggestions for Edinburgh Recycle App, from settings");
            return true;
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
 * Implementation of AsyncTask, to fetch the address in the background away from
 * the UI thread.
 * extends<params, progress, result>, so input, progress is generally void unless doing UI funk,
 * and return value.
 */
private static class FetchAddressTask extends AsyncTask<String, Void, ReturnValues> {

        Context context;

    public FetchAddressTask(Context context) {
        this.context = context;
    }

    @Override
    protected ReturnValues doInBackground(String... input) {

        ReturnValues returnValues;
        Log.w("doInBackground", "seeking gps from postcode");
        if (input.length == 0 || input[0] == null || input.length > 1) {
            // prepare for someone manking the code outside the class
            return null;
        }
        String postcode = input[0];
        final Geocoder geocoder = new Geocoder(context);
        if(geocoder.isPresent() == false) {
            Log.e("nativeGeocoding", "geocoder service not present on device");
            //Toast.makeText(SettingsActivity.this, "Device does not allow geocoding", Toast.LENGTH_LONG).show();
            return new ReturnValues(null, null, "Device does not support this feature");
        }
        try {
            List<Address> addresses = geocoder.getFromLocationName(postcode, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address firstAddress = addresses.get(0);
                double latitude = firstAddress.getLatitude();
                double longitude = firstAddress.getLongitude();
                String street = firstAddress.getAddressLine(0);
                Log.i("getAddressLine 0", firstAddress.getAddressLine(0));

                // Use the address as needed
                String message = String.format("Latitude: %f, Longitude: %f",
                        firstAddress.getLatitude(), firstAddress.getLongitude());
                //Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_LONG).show();
                Log.wtf("geocoded postcode!", message);
                return new ReturnValues(latitude, longitude, street);
            } else {
                // Display appropriate message when Geocoder services are not available
                Log.i("doInBackground", "Unable to geocode postcode :(");
            }
        } catch (IOException e) {
            Log.e("doInBackground", "Geocoder failed " + e.getMessage() + "\nstackTrace: " + e.getStackTrace());
        }


        // Return the {@link Event}
        return new ReturnValues(null, null, "Unable to identify address.  WTH Google?");
    }

    /**
     * Uses the logging framework to display the output of the fetch
     * operation in the log fragment.
     */
    @Override
    protected void onPostExecute(ReturnValues returnValues ) {
        // Update the information displayed to the user, and save info
        homeAddress.setSummary(returnValues.getStreetAddress());
        if (returnValues.getLatitude() == null)
        {
            Log.w("onPostExecute", "no return values!");
            return;
        }
        Log.w("onPostExecute", "heading to save details");
        saveManualLatLon(returnValues);
    }
}
    /**
     * Update the sharedPrefs with the given gps
     */
    static private void saveManualLatLon(ReturnValues returnValues) {
        Log.w("saveManualLatLon", "saving lat/lon");
        // save the new address
        SharedPreferences.Editor editor = homeAddress.getEditor();
        editor.putString("manualLatitude", String.valueOf(returnValues.getLatitude()));
        editor.putString("manualLongitude", String.valueOf(returnValues.getLongitude()));
        editor.apply();
        // TODO need to retrieve this in MainActivity + convert to Doubles :)
    }
}
