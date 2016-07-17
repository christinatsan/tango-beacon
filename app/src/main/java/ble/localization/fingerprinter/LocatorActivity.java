package ble.localization.fingerprinter;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;

public class LocatorActivity extends AppCompatActivity {

    private static final String TAG = "LocatorActivity";
    private static final int timeToRecord = 5000;
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
    private Map<Integer, ArrayList<Integer>> currentBeaconRssiValues = new HashMap<>();
    private Map<Integer, ArrayList<Integer>> usedBeaconRssiValues;

    protected enum localizationPhase {
        PHASE_ONE,
        PHASE_TWO,
        PHASE_THREE,
    }

    private Map<String, Object> requestParameter = new HashMap<>();
    private ArrayList<Object> beaconInfo = new ArrayList<>();   // major-RSSI values
    private String jsonFingerprintRequestString;

    private Button locateButton;
    private boolean tapToLocateEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locator);

        ProgressDialog loading_dialog = ProgressDialog.show(LocatorActivity.this, "Loading map", "Please wait...", true);
        loading_dialog.setCancelable(false);
        loading_dialog.setCanceledOnTouchOutside(false);

        mapView = (MapView)findViewById(R.id.mapView);
        mapView.setImage(ImageSource.resource(R.drawable.home_floor_plan));
        mapView.setTouchAllowedBool(false);

        loading_dialog.dismiss();

        isEstimoteRangingServiceReady[0] = false;

        coordView = (TextView)findViewById(R.id.coordinateText);

        this.registerReceiver(mCoordinateChangeReceiver, coordinateChangeFilter);
        this.registerReceiver(mLocalizationReceiver, localizationFilter);
        Log.d(TAG, "Registered receivers.");
        lastRecord = System.currentTimeMillis();

        locateButton = (Button)findViewById(R.id.locateButton);
        assert (locateButton != null);
        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(tapToLocateEnabled) {
                    if(!checkLocateRequirements()) return;
                    tapToLocateEnabled = false;
                    locateButton.setText("Stop Localization");
                } else {
                    locatingBeaconManager.stopRanging(Globals.region);
                    tapToLocateEnabled = true;
                    locateButton.setText("Localize");
                }
            }
        });
        tapToLocateEnabled = true;

        // Estimote-related code
        locatingBeaconManager = new BeaconManager(this);

        locatingBeaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> list) {
                // Beacon discovery code goes here.
                for(Beacon b : list) {
                    if(!currentBeaconRssiValues.containsKey(b.getMajor())) {
                        currentBeaconRssiValues.put(b.getMajor(), new ArrayList<Integer>());
                    }
                    currentBeaconRssiValues.get(b.getMajor()).add(b.getRssi());

                    if (System.currentTimeMillis() >= (lastRecord + timeToRecord)) {
                        lastRecord = System.currentTimeMillis();
                        usedBeaconRssiValues = new HashMap<>(currentBeaconRssiValues);
                        currentBeaconRssiValues.clear();

                        // Send intent
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
    }

    @Override
    public void onResume() {
        super.onResume();
        this.registerReceiver(mCoordinateChangeReceiver, coordinateChangeFilter);
        this.registerReceiver(mLocalizationReceiver, localizationFilter);
    }

    @Override
    public void onPause() {
        this.unregisterReceiver(mCoordinateChangeReceiver);
        this.unregisterReceiver(mLocalizationReceiver);
        locatingBeaconManager.stopRanging(Globals.region);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        try {
            this.unregisterReceiver(this.mCoordinateChangeReceiver);
            this.unregisterReceiver(this.mLocalizationReceiver);
        } catch (IllegalArgumentException e) { }
        locatingBeaconManager.stopRanging(Globals.region);
        Globals.disconnectBeaconManager(locatingBeaconManager, isEstimoteRangingServiceReady);
        super.onDestroy();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.locator_options_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // respond to menu item selection
        switch (item.getItemId()) {
            case R.id.return_to_fingerprinter:
                // return to fingerprinter here
                finish();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private boolean checkLocateRequirements() {
        if(!isEstimoteRangingServiceReady[0]) {
            Globals.showDialogWithOKButton(this, "Beacon Ranging Not Ready", "Please wait until the ranging service is ready.");
            return false;
        }

        if(!SystemRequirementsChecker.checkWithDefaultDialogs(this)) {
            Globals.showDialogWithOKButton(this,
                    "Required Permissions Not Granted",
                    "This app requires Location and Bluetooth permissions to function properly." +
                            " Please grant these permissions and restart localization.");
            return false;
        }

        locatingBeaconManager.startRanging(Globals.region);
        return true;
    }

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
            Double avg = MathFunctions.doubleRound(MathFunctions.trimmedMean(RSSIs, MainActivity.PERCENT_CUTOFF), MainActivity.DECIMAL_PLACES);
            Map<String, Object> beaconRssi = new HashMap<>();
            beaconRssi.put("major", key);
            beaconRssi.put("rssi", avg);
            beaconInfo.add(beaconRssi);
        }

        requestParameter.put("type", "location_info");
        requestParameter.put("measured_data", beaconInfo);

        jsonFingerprintRequestString = new JSONObject(requestParameter).toString();
        Log.d(TAG, jsonFingerprintRequestString);

        // Send intent
        final Intent sendAllValues = new Intent(LOCATOR_BROADCAST_ACTION);
        sendAllValues.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, localizationPhase.PHASE_TWO);
        getApplicationContext().sendBroadcast(sendAllValues);
    }

    private void sendValues() {
        Log.d(TAG, "Sending values.");

        final String requestType = "application/json";

        AsyncHttpClient client = new AsyncHttpClient();
        StringEntity json = new StringEntity(jsonFingerprintRequestString, "UTF-8");
        json.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, requestType));

        client.put(LocatorActivity.this, Globals.SERVER_BASE_URL + URL_ENDPOINT, json, requestType, new JsonHttpResponseHandler() {

            @Override
            public void onStart() {
                // Initiated the request
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject responseBody) {
                // Successfully got a response
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
                // Send intent to complete process
                final Intent updateMapView = new Intent(LOCATOR_BROADCAST_ACTION);
                updateMapView.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, localizationPhase.PHASE_THREE);
                try {
                    mapView.lastTouchCoordinates[0] = (float)(double)coordinates.get(0);
                    mapView.lastTouchCoordinates[1] = (float)(double)coordinates.get(1);
                } catch (JSONException e) {
                    Log.e(TAG, "Unexpected JSON Exception.", e);
                    return;
                }
                getApplicationContext().sendBroadcast(updateMapView);
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

    private void clearMaps() {
        usedBeaconRssiValues.clear();
        requestParameter.clear();
        beaconInfo.clear();
        jsonFingerprintRequestString = "";
    }

    // Receives notification that the selected coordinates have been changed.
    private class coordinateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Change coordinates on screen
            coordView.setText("(" + mapView.lastTouchCoordinates[0] + ", " + mapView.lastTouchCoordinates[1] + ")");
        }
    }

    private class localizationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent received.");
            final Bundle intentPayload = intent.getExtras();
            final localizationPhase target = (localizationPhase)intentPayload.get(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY);

            assert (target != null);

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
                    mapView.invalidate();
                    break;

            }

        }
    }
}
