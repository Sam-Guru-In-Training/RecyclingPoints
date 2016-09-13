package com.example.android.quakereport;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Created by Sam on 23/08/2016.
 */

public class offlineJsonHandler {

    private final String LOG_TAG = "offLineJsonHandler";

    private static final int DATABASE_VERSION = 15;
    private Context mCtx; //<-- declare a Context reference

    public offlineJsonHandler(Context context) {
        //super(context); //, "rettinfo", null, DATABASE_VERSION);
        mCtx = context; //<-- fill it with the Context you are passed
    }

//    @Override
//    public void onCreate(SQLiteDatabase db) {
//        Log.d("Create: ", "Creating antidotlist");
//        String CREATE_ANTIDOT_TABLE = "CREATE TABLE antidots (id INTEGER PRIMARY KEY antidot TEXT, dos TEXT)";
//        Log.d("Create: ", CREATE_ANTIDOT_TABLE);
//        db.execSQL(CREATE_ANTIDOT_TABLE);
//
//        InputStream antidots = null; //<-- call getAssets on your Context object.
//        try {
//            antidots = mCtx.getAssets().open("antidot/antidots");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        InputStreamReader input = new InputStreamReader(antidots);
//        BufferedReader buffreader = new BufferedReader(input, 2 * 1024);
//        String line;
//        while ((line = buffreader.readLine()) != null) {
//            String[] point_t = line.split(",");
//        }
//        antidots.close();
//    }

    public String loadJSONFromAsset() {
        //ArrayList<MyLocations> locList = new ArrayList<>();
        String json = null;
        try {
            Log.d(LOG_TAG, Arrays.toString(mCtx.getAssets().list(".")));
        } catch (IOException e) {
            Log.wtf(LOG_TAG, "unable to find ANY files?!");
            Log.e(LOG_TAG, e.getLocalizedMessage(), e);
        }
        try {
            Log.wtf(LOG_TAG, "trying to open json file");
            InputStream is = mCtx.getAssets().open("recycleBanksOffline.JSON");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            Log.wtf(LOG_TAG, "unable to open json file");
            ex.printStackTrace();
            return null;
        }
        return json;
    }
}