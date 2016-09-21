package com.example.android.recyclingbanks;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SeekBar;

import com.google.android.gms.maps.OnStreetViewPanoramaReadyCallback;
import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.StreetViewPanoramaCamera;
import com.google.android.gms.maps.model.StreetViewPanoramaLocation;

/**
 * Created by Sam on 14/09/2016.
 */

public class StreetViewFragment extends AppCompatActivity implements
        StreetViewPanorama.OnStreetViewPanoramaChangeListener {

    private LatLng mBankLatLng;
    private StreetViewPanorama mStreetViewPanorama;
    private SeekBar mCustomDurationBar;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // get the bank location out of the intent
        Bundle bundle = this.getIntent().getParcelableExtra("bundle");
        mBankLatLng = bundle.getParcelable("bankLatLng");

        setContentView(R.layout.street_view_panorama);

        SupportStreetViewPanoramaFragment streetViewPanoramaFragment =
                (SupportStreetViewPanoramaFragment)
                        getSupportFragmentManager().findFragmentById(R.id.streetviewpanorama);
        streetViewPanoramaFragment.getStreetViewPanoramaAsync(
                new OnStreetViewPanoramaReadyCallback() {
                    @Override
                    public void onStreetViewPanoramaReady(StreetViewPanorama panorama) {
                        mStreetViewPanorama = panorama;
                        mStreetViewPanorama.setPosition(mBankLatLng);
                        mStreetViewPanorama.setOnStreetViewPanoramaChangeListener(StreetViewFragment.this);
                    }
                });
    }
    /**
     * called once the panorama is setup and we're placing the camera, allows us an event
     * to set a direction for the camera
     * @param streetViewPanoramaLocation a Location object type thing
     */
    @Override
    public void onStreetViewPanoramaChange(StreetViewPanoramaLocation streetViewPanoramaLocation){
        //Get the angle between the target location and road side location
        float bearing = getBearing(streetViewPanoramaLocation.position.latitude,
                streetViewPanoramaLocation.position.longitude);

        //Remove the listener
        //getStreetViewPanorama().setOnStreetViewPanoramaChangeListener(null);
        mStreetViewPanorama.setOnStreetViewPanoramaChangeListener(null);
        //Change the camera angle
        StreetViewPanoramaCamera camera = new StreetViewPanoramaCamera.Builder()
                .bearing(bearing)
                .build();
        mStreetViewPanorama.animateTo(camera, 1);
    }

    /**
     * Get a bearing from the camera to the bin so we can point the camera the right way
     * @param startLat latitude on the road of the camera
     * @param startLng longitude on the road of the camera
     * @return a float, bearing from camera on road to bin gps
     */
    private float getBearing(double startLat, double startLng) {
        Location startLocation = new Location("startlocation");
        startLocation.setLatitude(startLat);
        startLocation.setLongitude(startLng);

        Location endLocation = new Location("endlocation");
        endLocation.setLatitude(mBankLatLng.latitude);
        endLocation.setLongitude(mBankLatLng.longitude);

        return startLocation.bearingTo(endLocation);
    }

    /**
     * Menu stuff
     */
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
            composeEmail(addresses, "Suggestions for Edinburgh Recycle App");
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

}
