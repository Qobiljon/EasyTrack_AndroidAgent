package kr.ac.inha.nsl;

import android.location.Location;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

public class MyLocationCallback extends LocationCallback {
    // region Constants
    static final int GPS_DATA_SOURCE_ID = 0xf00;
    static final int GPS_FASTEST_INTERVAL = 10000; // 10 seconds = 10000
    static final int GPS_SLOWEST_INTERVAL = 60000; // 10 minutes = 60000
    // endregion

    // region Variables
    private Location previouslyStoredLocation;
    // endregion

    // region Override
    @Override
    public void onLocationResult(LocationResult locationResult) {
        if (locationResult == null)
            return;

        for (Location location : locationResult.getLocations()) {
            if (previouslyStoredLocation != null && previouslyStoredLocation.equals(location))
                continue;

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            double altitude = location.getAltitude();

            Tools.saveNumericData(GPS_DATA_SOURCE_ID, location.getTime(), location.getAccuracy(), latitude, longitude, altitude);
            //Log.e("LOCATION UPDATE", String.format(Locale.getDefault(), "(ts, lat, lon, alt)=(%d, %f, %f, %f)", location.getTime(), latitude, longitude, altitude));

            if (previouslyStoredLocation == null)
                previouslyStoredLocation = location;
        }
    }
    // endregion
}