package ble.localization.manager;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.SystemRequirementsChecker;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;

/**
 * Our locator.
 */
public class LocatorActivity extends AppCompatActivity {

    // Some general variables.
    private static final String TAG = "LocatorActivity";
    private static final int timeToRecord = 3000;
    private static final String URL_ENDPOINT = "/location";

    private MapView mapView;
    private TextView coordView;
    public static final String LOCATOR_BROADCAST_ACTION = "ble.localization.locator.LOCATE";

    // Beacon-related variables
    private BeaconManager locatingBeaconManager;
    private static boolean[] isEstimoteRangingServiceReady = new boolean[1];
    private long lastRecord;

    // Broadcast receivers and intent filters
    private BroadcastReceiver mCoordinateChangeReceiver = new coordinateChangeReceiver();
    private BroadcastReceiver mLocalizationReceiver = new localizationReceiver();
    private IntentFilter coordinateChangeFilter = new IntentFilter(MapView.COORDINATE_TEXT_UPDATE_BROADCAST);
    private IntentFilter localizationFilter = new IntentFilter(LOCATOR_BROADCAST_ACTION);

    // Data holders
    private Map<Integer, ArrayList<Integer>> currentBeaconRssiValues = new HashMap<>(); // current values
    private Map<Integer, ArrayList<Integer>> usedBeaconRssiValues; // used values for sending in localization

    protected enum localizationPhase {
        PHASE_ONE,
        PHASE_TWO,
        PHASE_THREE,
    }

    // Data holders
    private Map<String, Object> requestParameter = new HashMap<>();
    private ArrayList<Object> beaconInfo = new ArrayList<>();   // major-RSSI values
    private String jsonFingerprintRequestString;

    // Button-related stuff
    private Button locateButton;
    private boolean tapToLocateEnabled;

    // Floor/floor-plan-related stuff
    private int floor_curr_index = Globals.floor_start_index;
    private String curr_floor = Globals.floor_names[floor_curr_index];
    private String prev_floor = curr_floor;
    private Resources resources;

    private float prev_x = MapView.defaultCoord;
    private float prev_y = MapView.defaultCoord;

    private float prev2_x = MapView.defaultCoord;
    private float prev2_y = MapView.defaultCoord;

    private static ProgressDialog waitingForLocationDialog;

    private static ArrayList<String> allAvailableBeaconCats = null;
    private static String currentBeaconCat = "";

    public AlertDialog.Builder beaconTypesMenu;

    /**
     * Gets the resource ID of the floor plan we want.
     * @param name The formatted name of the floor in question
     * @return The resource ID.
     * @throws Resources.NotFoundException The specified resource was not found.
     */
    protected int getFloorPlanResourceID(String name) throws Resources.NotFoundException {
        // Map names are of the format: "Floor Name_map"
        return resources.getIdentifier(name + "_map", "drawable", this.getPackageName());
    }

    /**
     * Formats the floor name so it can be used to search for a resource.
     * @param floor The floor name.
     * @return The formatted name.
     */
    protected String formatToValidResourceName(String floor) {
        // Make lowercase and remove all whitespace
        return floor.toLowerCase().replaceAll("\\s+","");
    }

    /**
     * Called when the activity is starting. Performs most initialization.
     * @param savedInstanceState May contain the most recently saved state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locator);

        resources = this.getResources();

        ProgressDialog loading_dialog = ProgressDialog.show(LocatorActivity.this, "Loading map", "Please wait...", true);
        loading_dialog.setCancelable(false);
        loading_dialog.setCanceledOnTouchOutside(false);

        // Load the floor plan.
        mapView = (MapView)findViewById(R.id.mapView);
        int startPlanResID = getFloorPlanResourceID(formatToValidResourceName(Globals.floor_names[floor_curr_index]));
        mapView.setImage(ImageSource.resource(startPlanResID));
        mapView.setTouchAllowedBool(false);

        loading_dialog.dismiss();

        // Set that the Estimote service is not ready.
        isEstimoteRangingServiceReady[0] = false;

        // Find the text view that shows the coordinates.
        coordView = (TextView)findViewById(R.id.coordinateText);

        Log.d(TAG, "Registered receivers.");
        lastRecord = System.currentTimeMillis();

        // Initialize the locator button.
        locateButton = (Button)findViewById(R.id.locateButton);
        assert (locateButton != null);
        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Switch the button between starting and stopping localization.
                if(tapToLocateEnabled) {
                    if(!checkLocateRequirements()) return;
                    tapToLocateEnabled = false;
                    prev_x = prev_y = prev2_x = prev2_y = MapView.defaultCoord;
                    locateButton.setText("Stop Localization");
                } else {
                    locatingBeaconManager.stopRanging(Globals.region);
                    tapToLocateEnabled = true;
                    clearMaps();
                    clearInstantaneousData();
                    locateButton.setText("Localize");
                }
            }
        });
        tapToLocateEnabled = true;

        // Estimote SDK-related code
        locatingBeaconManager = new BeaconManager(this);

        locatingBeaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> list) {
                // Beacon discovery code during localization.
                for(Beacon b : list) {
                    if(!currentBeaconRssiValues.containsKey(b.getMajor())) {
                        currentBeaconRssiValues.put(b.getMajor(), new ArrayList<Integer>());
                    }
                    currentBeaconRssiValues.get(b.getMajor()).add(b.getRssi());

                    // Keep adding until we've surpassed the timetoRecord.
                    if (System.currentTimeMillis() >= (lastRecord + timeToRecord)) {
                        lastRecord = System.currentTimeMillis();
                        usedBeaconRssiValues = new HashMap<>(currentBeaconRssiValues);
                        currentBeaconRssiValues.clear();

                        // Send intent for the next phase of localization.
                        final Intent beginLocalizing = new Intent(LOCATOR_BROADCAST_ACTION);
                        beginLocalizing.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, localizationPhase.PHASE_ONE);
                        getApplicationContext().sendBroadcast(beginLocalizing);
                    }
                }

            }
        });

        locatingBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                isEstimoteRangingServiceReady[0] = true;
                Log.d(TAG, "Connected to ranging service.");
            }
        });

        waitingForLocationDialog = new ProgressDialog(LocatorActivity.this);
        waitingForLocationDialog.setTitle("Waiting for Location");
        waitingForLocationDialog.setMessage("Please wait.");
        waitingForLocationDialog.setIndeterminate(true);
        waitingForLocationDialog.setCancelable(false);
        waitingForLocationDialog.setCanceledOnTouchOutside(false);

        // get beacon type categories
        AsyncHttpClient client = new AsyncHttpClient();

        client.get(LocatorActivity.this, Globals.SERVER_BASE_API_URL + "/get_beacon_cats", new JsonHttpResponseHandler() {
            @Override
            public void onStart() {
                // Initiated the request
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject responseBody) {
                // Successfully got a response
                // If we didn't get a match, return.
                try {
                    JSONArray all_categories = responseBody.getJSONArray("available_categories");
                    allAvailableBeaconCats = new ArrayList<>();
                    for (int i = 0; i < all_categories.length(); i++){
                        allAvailableBeaconCats.add(all_categories.getString(i));
                    }
                } catch (JSONException e) { }

                // sort in alphabetical order
                Collections.sort(allAvailableBeaconCats, new Comparator<String>() {
                    @Override
                    public int compare(String s1, String s2) {
                        return s1.compareToIgnoreCase(s2);
                    }
                });

                allAvailableBeaconCats.add(0, "");

                final String[] menu_options = allAvailableBeaconCats.toArray(new String[allAvailableBeaconCats.size()]);
                menu_options[0] = "<Use all beacons>";

                beaconTypesMenu = new AlertDialog.Builder(LocatorActivity.this);
                beaconTypesMenu.setTitle("Choose beacon type for location calculations");
                beaconTypesMenu.setItems(menu_options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        currentBeaconCat = allAvailableBeaconCats.get(which);
                        Toast.makeText(LocatorActivity.this, "Beacons to be used: " + menu_options[which], Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable error, JSONObject responseBody)
            {
                // Request failed
                Globals.showSnackbar(findViewById(android.R.id.content), "Beacon categories could not retrieved. This feature will not be available.");
            }

            @Override
            public void onRetry(int retryNo) {
                // Request was retried
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
                // Progress notification
            }

            @Override
            public void onFinish() {
                // Completed the request (either success or failure)
                // Clear maps to prepare for next location
            }
        });
    }

    /**
     * Called so that the activity can start interacting with the user.
     */
    @Override
    public void onResume() {
        super.onResume();
        this.registerReceiver(mCoordinateChangeReceiver, coordinateChangeFilter);
        this.registerReceiver(mLocalizationReceiver, localizationFilter);
    }

    /**
     * Called when an activity is going into the background, but has not (yet) been killed.
     * The counterpart to onResume.
     */
    @Override
    public void onPause() {
        this.unregisterReceiver(mCoordinateChangeReceiver);
        this.unregisterReceiver(mLocalizationReceiver);
        locatingBeaconManager.stopRanging(Globals.region);
        super.onPause();
    }

    /**
     * Performs final cleanup before an activity is destroyed.
     */
    @Override
    public void onDestroy() {
        locatingBeaconManager.stopRanging(Globals.region);
        Globals.disconnectBeaconManager(locatingBeaconManager, isEstimoteRangingServiceReady);
        super.onDestroy();
    }

    /**
     * Initialize the contents of the Activity's standard options menu.
     * @param menu The options menu in question.
     * @return True for the menu to be displayed.
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.locator_options_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if(allAvailableBeaconCats == null) {
            menu.findItem(R.id.action_selectbeaconstobeused).setEnabled(false);
        } else {
            menu.findItem(R.id.action_selectbeaconstobeused).setEnabled(true);
        }
        return true;
    }

    /**
     * Called whenever an item in the options menu is selected.
     * @param item The menu item that was selected.
     * @return True to consume the menu option.
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        // respond to menu item selection
        switch (item.getItemId()) {
            case R.id.return_to_fingerprinter:
                // return to fingerprinter here
                finish();
                break;
            case R.id.action_selectbeaconstobeused:
                beaconTypesMenu.show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    // Check if we've met the requirement to localize, and start the ranging service.
    private boolean checkLocateRequirements() {
        // Is the ranging service ready?
        if(!isEstimoteRangingServiceReady[0]) {
            Globals.showDialogWithOKButton(this, "Beacon Ranging Not Ready", "Please wait until the ranging service is ready.");
            return false;
        }

        // Do we have the appropriate permissions?
        if(!SystemRequirementsChecker.checkWithDefaultDialogs(this)) {
            Globals.showDialogWithOKButton(this,
                    "Required Permissions Not Granted",
                    "This app requires Location and Bluetooth permissions to function properly." +
                            " Please grant these permissions and restart localization.");
            return false;
        }

        // If we've reached this spot, start ranging.
        locatingBeaconManager.startRanging(Globals.region);
        return true;
    }

    /**
     * Process the captured values.
     */
    private void processValues() {
        Log.d(TAG, "Processing values.");
        Log.v(TAG, "ALL Values: " + usedBeaconRssiValues.toString());

        if (usedBeaconRssiValues.size() == 0) return;

        // Process some more.
        for(Integer key : usedBeaconRssiValues.keySet()) {
            ArrayList<Double> RSSIs = new ArrayList<>();
            for(Integer rssi : usedBeaconRssiValues.get(key)) {
                RSSIs.add((double)rssi);
            }
            // TODO: Maybe don't use trimmed mean since we don't have too many readings?
            Double avg = MathFunctions.doubleRound(MathFunctions.trimmedMean(RSSIs, FingerprinterActivity.PERCENT_CUTOFF), FingerprinterActivity.DECIMAL_PLACES);
            Map<String, Object> beaconRssi = new HashMap<>();
            beaconRssi.put("major", key);
            beaconRssi.put("rssi", avg);
            beaconInfo.add(beaconRssi);
        }

        requestParameter.put("type", "location_info");
        requestParameter.put("measured_data", beaconInfo);
        ArrayList<Float> tmp = new ArrayList<>();
        tmp.add(0, prev_x);
        tmp.add(1, prev_y);
        requestParameter.put("previous_position", tmp);
        ArrayList<Float> tmp2 = new ArrayList<>();
        tmp2.add(0, prev2_x);
        tmp2.add(1, prev2_y);
        requestParameter.put("previous_position2", tmp2);
        requestParameter.put("beacon_type", currentBeaconCat);

        jsonFingerprintRequestString = new JSONObject(requestParameter).toString();
        Log.d(TAG, jsonFingerprintRequestString);

        // Send intent for the next phase.
        final Intent sendAllValues = new Intent(LOCATOR_BROADCAST_ACTION);
        sendAllValues.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, localizationPhase.PHASE_TWO);
        getApplicationContext().sendBroadcast(sendAllValues);
    }

    /**
     * Send the captured values to the server, and receive the calculated values.
     */
    private void sendValues() {
        Log.d(TAG, "Sending values.");

        final String requestType = "application/json";

        AsyncHttpClient client = new AsyncHttpClient();
        StringEntity json = new StringEntity(jsonFingerprintRequestString, "UTF-8");
        json.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, requestType));

        // Log.d(TAG, Globals.SERVER_BASE_API_URL + URL_ENDPOINT);
        client.put(LocatorActivity.this, Globals.SERVER_BASE_API_URL + URL_ENDPOINT, json, requestType, new JsonHttpResponseHandler() {

            @Override
            public void onStart() {
                // Initiated the request
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject responseBody) {
                // Successfully got a response
                // If we didn't get a match, return.
                try {
                    if (responseBody.get("status").equals("no_match")) return;
                } catch (JSONException e) {
                    Log.e(TAG, "Unexpected JSON Exception.", e);
                    return;
                }

                JSONArray coordinates;
                try {
                    coordinates = responseBody.getJSONObject("content").getJSONArray("coordinates");
                } catch (JSONException e) {
                    Log.e(TAG, "Unexpected JSON Exception.", e);
                    return;
                }

                try {
                    curr_floor = responseBody.getJSONObject("content").getString("floor");
                } catch (JSONException e) {
                    Log.e(TAG, "Unexpected JSON Exception.", e);
                    return;
                }

                // Else, send the intent to complete the localization process.
                final Intent updateMapView = new Intent(LOCATOR_BROADCAST_ACTION);
                updateMapView.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, localizationPhase.PHASE_THREE);
                try {
                    prev2_x = prev_x;
                    prev2_y = prev_y;

                    prev_x = mapView.thisTouchCoordinates[0];
                    prev_y = mapView.thisTouchCoordinates[1];

                    mapView.thisTouchCoordinates[0] = (float)(double)coordinates.get(0);
                    mapView.thisTouchCoordinates[1] = (float)(double)coordinates.get(1);
                } catch (JSONException e) {
                    Log.e(TAG, "Unexpected JSON Exception.", e);
                    return;
                }
                if(prev2_x != MapView.defaultCoord && prev_x != MapView.defaultCoord) {
                    waitingForLocationDialog.dismiss();
                    getApplicationContext().sendBroadcast(updateMapView);
                } else {
                    waitingForLocationDialog.show();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable error, JSONObject responseBody)
            {
                // Request failed
                Globals.showSnackbar(findViewById(android.R.id.content), "Sending location data failed. (Server response code: " + statusCode + ")");
            }

            @Override
            public void onRetry(int retryNo) {
                // Request was retried
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
                // Progress notification
            }

            @Override
            public void onFinish() {
                // Completed the request (either success or failure)
                // Clear maps to prepare for next location
                clearMaps();
            }
        });
    }

    /**
     * Clear the data holders.
     */
    private void clearMaps() {
        usedBeaconRssiValues.clear();
        requestParameter.clear();
        beaconInfo.clear();
        jsonFingerprintRequestString = "";
    }

    /**
     * Clear the instantaneous data holders not cleared in clearMaps.
     */
    private void clearInstantaneousData() {
        currentBeaconRssiValues.clear();
    }

    /**
     * Broadcast receiver - Receives the notification that the current coordinates have been
     * changed and changes them on the text view.
     */
    private class coordinateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Change coordinates on screen
            coordView.setText("(" + mapView.thisTouchCoordinates[0] + ", " + mapView.thisTouchCoordinates[1] + ")");
        }
    }

    /**
     * Broadcast receiver - Facilitates the localization process.
     */
    private class localizationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent received.");
            final Bundle intentPayload = intent.getExtras();
            final localizationPhase target = (localizationPhase)intentPayload.get(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY);

            if (target == null) throw new AssertionError("The localizationPhase target should not be null!");

            switch (target) {
                case PHASE_ONE:
                    processValues();
                    break;

                case PHASE_TWO:
                    sendValues();
                    break;

                case PHASE_THREE:
                    // Update coordinate text
                    Intent in = new Intent(MapView.COORDINATE_TEXT_UPDATE_BROADCAST);
                    context.sendBroadcast(in);
                    // Update the map view
                    if(!curr_floor.equals(prev_floor)) {
                        int newFloorResID = getFloorPlanResourceID(formatToValidResourceName(curr_floor));
                        mapView.setImage(ImageSource.resource(newFloorResID));
                        prev_x = prev_y = prev2_x = prev2_y = MapView.defaultCoord;
                        prev_floor = curr_floor;
                    } else {
                        mapView.invalidate();
                    }
                    break;

            }

        }
    }
}
