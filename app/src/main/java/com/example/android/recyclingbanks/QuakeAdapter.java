package com.example.android.recyclingbanks;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * {@link QuakeAdapter} is an {@link ArrayAdapter} that can provide the layout for each list item
 * based on a data source, which is a list of {@link Quake} objects.
 */
public class QuakeAdapter extends ArrayAdapter<Quake>  {

    /** Resource ID for the background color for this list of Quakes */
    private int mColorResourceId;
    private String LOG_TAG = "Quake Adapter Class";
    private Context mContext;

    /**
     * Create a new {@link QuakeAdapter} object.
     *
     * @param context is the current context (i.e. Activity) that the adapter is being created in.
     * @param quakes is the list of {@link Quake}s to be displayed.
     */
    public QuakeAdapter(Context context, ArrayList<Quake> quakes) {
        super(context, 0, quakes);
        this.mContext = context;
        //mColorResourceId = colorResourceId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Check if an existing view is being reused, otherwise inflate the view
        View listItemView = convertView;
        if (listItemView == null) {
            listItemView = LayoutInflater.from(getContext()).inflate(
                    R.layout.list_item, parent, false);
        }

//        // Get the {@link Word} object located at this position in the list
          Quake currentQuake = getItem(position);
//
//        // Find the TextView in the list_item.xml layout with the ID mag_text_view.
//        TextView magTextView = (TextView) listItemView.findViewById(R.id.mag_text_view);
//        // Get the magnitude from the currentQuake object
//
//        // Set the proper background color on the magnitude circle.
//        // Fetch the background from the TextView, which is a GradientDrawable.
//        GradientDrawable magnitudeCircle = (GradientDrawable) magTextView.getBackground();
//
//        // Get the appropriate background color based on the current earthquake magnitude
//        int magnitudeColor = getMagnitudeColor(currentQuake.getMagnitude());
//
//        // Set the color on the magnitude circle
//        magnitudeCircle.setColor(magnitudeColor);
//
//        // create a formatter to turn mag double into a string with 2 decimal points
//        DecimalFormat formatter = new DecimalFormat("0.0");
//
//        String magAsString = formatter.format( currentQuake.getMagnitude() );
//        // and set this text on
//        // the magTextView.
//        magTextView.setText(String.valueOf( magAsString ));
//
//        TextView locationTextView = (TextView) listItemView.findViewById(R.id.location_text_view);
//        TextView distanceTextView = (TextView) listItemView.findViewById(R.id.distance_text_view);
//        String distanceAndLocation = currentQuake.getLocationString();
//
//        int split_point = distanceAndLocation.indexOf("of");
//        if (split_point == -1) {
//            // if no km given, just print to screen as is
//            locationTextView.setText(distanceAndLocation);
//            distanceTextView.setText("Near the");
//        }
//        else {
//            // split of the distance from 0 till split point
//            String distanceString = distanceAndLocation.substring(0, split_point+2);
//            distanceTextView.setText(distanceString);
//
//            // set the location text view
//            String distanceText = distanceAndLocation.substring(split_point+3, distanceAndLocation.length());
//            locationTextView.setText(distanceText);
//        }
//
//
//        TextView dateTextView = (TextView) listItemView.findViewById(R.id.date_text_view);
//        long timeSinceQuakeInMS = currentQuake.getDate();
//        // need to get long to a date object so can process this into a human format
//        Date intermediateDateObject = new Date(timeSinceQuakeInMS);
//        // now needs to be converted AGAIN!
//        SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM DD, yyyy");
//        // shove the date Object into the SimpleDateFormat object
//        String dateToDisplay = dateFormatter.format(intermediateDateObject);
//        // output it to the screen
//        dateTextView.setText(dateToDisplay);
//
//        // do the same for the time
//        TextView timeTextView = (TextView) listItemView.findViewById(R.id.time_text_view);
//        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm a");
//        String timeToDisplay = timeFormatter.format(intermediateDateObject);
//        timeTextView.setText(timeToDisplay);
//
//        // Return the whole list item layout (containing 3 TextViews) so that it can be shown in
//        // the ListView.
        TextView aField = (TextView) listItemView.findViewById(R.id.location_text_view);
        String location = currentQuake.getLocationDetails();
        aField.setText(location);
        return listItemView;
    }
    /*
    takes a magnitude for input and translates it to the color of the circle it requires
     */
    private int getBankMarkerColor(String bank_type) {

        switch ( bank_type ) {
            case "Packaging":
                // falls through to case 1
            case "Paper":
                // could use getContext()
                return ContextCompat.getColor(this.mContext, R.color.magnitude1);
            case "Bottle":
                return ContextCompat.getColor(this.mContext, R.color.magnitude2);
            case "Textile":
                return ContextCompat.getColor(this.mContext, R.color.magnitude3);
            case "Can":
                return ContextCompat.getColor(this.mContext, R.color.magnitude4);
            case "Book":
                return ContextCompat.getColor(this.mContext, R.color.magnitude5);
            case "Compost":
                return ContextCompat.getColor(this.mContext, R.color.magnitude6);
            default:
                return ContextCompat.getColor(this.mContext, R.color.magnitude6);
        }
    }
}
