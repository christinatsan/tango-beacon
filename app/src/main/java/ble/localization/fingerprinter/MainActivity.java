package ble.localization.fingerprinter;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.CountDownTimer;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;

public class MainActivity extends AppCompatActivity {

    // Some general variables
    private static final String TAG = "FingerprintActivity";
    protected static final int PERCENT_CUTOFF = 15;
    protected static final int DECIMAL_PLACES = 2;
    private static final int timeToRecord = 15000;
    private static final String URL_ENDPOINT = "/fingerprint";

    // The map view
    private ModifiedSubsamplingScaleImageView mapView;

    // Beacon-related variables
    private BeaconManager fingerprintingBeaconManager;
    private static boolean[] isEstimoteRangingServiceReady = new boolean[1];

    // Broadcast receivers and intent filters
    private BroadcastReceiver mCoordinateChangeReceiver = new coordinateChangeReceiver();
    private BroadcastReceiver mFingerprintReceiver = new fingerprintReceiver();
    private IntentFilter coordinateChangeFilter = new IntentFilter(ModifiedSubsamplingScaleImageView.COORDINATE_TEXT_UPDATE_BROADCAST);
    private IntentFilter fingerprintFilter = new IntentFilter(FINGERPRINT_BROADCAST_ACTION);

    // Broadcast-related objects
    private TextView coordView;
    public static final String FINGERPRINT_BROADCAST_ACTION = "ble.localization.fingerprinter.FINGERPRINT";

    protected enum fingerprintingPhase {
        PHASE_ONE,
        PHASE_TWO,
        PHASE_THREE,
        SHOW_SUCCESS_MESSAGE
    }

    // Data holders
    private Map<Integer, ArrayList<Integer>> beaconRssiValues = new HashMap<>();
    private Map<Integer, Double> averageBeaconRSSIValues = new HashMap<>();

    private Map<String, Object> requestParameter = new HashMap<>();
    private ArrayList<Object> beaconInfo = new ArrayList<>();   // major-RSSI values
    private Map<String, Object> locationInfo = new HashMap<>();
    private String jsonFingerprintRequestString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ProgressDialog loading_dialog = ProgressDialog.show(MainActivity.this, "Loading map", "Please wait...", true);
        loading_dialog.setCancelable(false);
        loading_dialog.setCanceledOnTouchOutside(false);

        mapView = (ModifiedSubsamplingScaleImageView)findViewById(R.id.mapView);
        mapView.setImage(ImageSource.resource(R.drawable.home_floor_plan));

        loading_dialog.dismiss();

        isEstimoteRangingServiceReady[0] = false;

        coordView = (TextView)findViewById(R.id.coordinateText);

        final Button fingerprintButton = (Button)findViewById(R.id.fingerprintButton);
        assert (fingerprintButton != null);
        fingerprintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginFingerprinting();
            }
        });

        final Button clearButton = (Button)findViewById(R.id.clearButton);
        assert (clearButton != null);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCoordinates();
            }
        });

        // Estimote-related code
        fingerprintingBeaconManager = new BeaconManager(this);

        fingerprintingBeaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> list) {
                // Beacon discovery code goes here.
                for(Beacon b : list) {
                    if(!beaconRssiValues.containsKey(b.getMajor())) {
                        beaconRssiValues.put(b.getMajor(), new ArrayList<Integer>());
                    }
                    beaconRssiValues.get(b.getMajor()).add(b.getRssi());
                }
            }
        });

        fingerprintingBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                isEstimoteRangingServiceReady[0] = true;
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        this.registerReceiver(mCoordinateChangeReceiver, coordinateChangeFilter);
        this.registerReceiver(mFingerprintReceiver, fingerprintFilter);
    }

    @Override
    public void onPause() {
        this.unregisterReceiver(this.mCoordinateChangeReceiver);
        this.unregisterReceiver(this.mFingerprintReceiver);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        try {
            this.unregisterReceiver(this.mCoordinateChangeReceiver);
            this.unregisterReceiver(this.mFingerprintReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "IllegalArgumentException thrown!", e);
        }
        Globals.disconnectBeaconManager(fingerprintingBeaconManager, isEstimoteRangingServiceReady);
        super.onDestroy();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.fingerprinter_options_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // respond to menu item selection
        switch (item.getItemId()) {
            case R.id.locator_launch:
                // launch locator here
                startActivity(new Intent(this, LocatorActivity.class));
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void beginFingerprinting() {
        // Actual fingerprinting code
        if(!isEstimoteRangingServiceReady[0]) {
            Globals.showDialogWithOKButton(this, "Beacon Ranging Not Ready", "Please wait until the ranging service is ready.");
            return;
        }

        if(mapView.lastTouchCoordinates[0] == -1 && mapView.lastTouchCoordinates[1] == -1) {
            Globals.showDialogWithOKButton(this, "Select Coordinates", "Please select coordinates before fingerprinting.");
            return;
        }

        if(!SystemRequirementsChecker.checkWithDefaultDialogs(this)) {
            Globals.showDialogWithOKButton(getApplicationContext(),
                    "Required Permissions Not Granted",
                    "This app requires Location and Bluetooth permissions to function properly." +
                            " Please restart fingerprinting and grant these permissions.");
            return;
        }

        // Send intent
        final Intent beginFingerprinting = new Intent(FINGERPRINT_BROADCAST_ACTION);
        beginFingerprinting.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_ONE);
        getApplicationContext().sendBroadcast(beginFingerprinting);
    }

    private void retrieveRSSIValues() {
        fingerprintingBeaconManager.startRanging(Globals.region);
        Log.d(TAG, "Ranging started.");

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Fingerprinting...");
        progressDialog.setMessage("Please wait - 00:" + (timeToRecord/1000) + " remaining.");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new CountDownTimer(timeToRecord, 1000) {

            public void onTick(long millisUntilFinished) {
                String formatted_number = String.format(Locale.ENGLISH, "%02d", millisUntilFinished/1000);
                progressDialog.setMessage("Please wait - 00:" + formatted_number + " remaining.");
            }

            public void onFinish() {
                fingerprintingBeaconManager.stopRanging(Globals.region);
                Log.d(TAG, "Ranging stopped.");
                progressDialog.dismiss();

                // Send intent
                final Intent nextPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                nextPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_TWO);
                getApplicationContext().sendBroadcast(nextPhaseBroadcast);
                Log.d(TAG, "Retrieval done.");
            }
        }.start();

    }

    private void processValues() {
        Log.d(TAG, "Processing values.");
        Log.v(TAG, "ALL Values: " + beaconRssiValues.toString());

        if(beaconRssiValues.keySet().size() == 0) {
            Globals.showDialogWithOKButton(this, "No Beacons Detected",
                    "Make sure you are in the range of beacons and that they are working.");
            return;
        }

        String message = "In this fingerprint, the following number of RSSIs were collected for each of the following beacons:\n";

        for(Integer key : beaconRssiValues.keySet()) {
            message += (key + " - " + beaconRssiValues.get(key).size() + " values.\n");
        }
        message += "\nWould you like to submit this fingerprint?";

        new AlertDialog.Builder(this)
                .setTitle("Confirm Fingerprint")
                .setMessage(message)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        // Process some more.
                        for(Integer key : beaconRssiValues.keySet()) {
                            ArrayList<Double> RSSIs = new ArrayList<>();
                            for(Integer rssi : beaconRssiValues.get(key)) {
                                RSSIs.add((double)rssi);
                            }
                            Double avg = MathFunctions.doubleRound(MathFunctions.trimmedMean(RSSIs, PERCENT_CUTOFF), DECIMAL_PLACES);
                            averageBeaconRSSIValues.put(key, avg);
                        }

                        Log.v(TAG, "AVERAGED Values: " + averageBeaconRSSIValues.toString());

                        // Put in location information
                        locationInfo.put("x", mapView.lastTouchCoordinates[0]);
                        locationInfo.put("y", mapView.lastTouchCoordinates[1]);
                        locationInfo.put("floor_num", 0);   // Will be dynamic in production
                        locationInfo.put("floor", "Ground Floor");  // Will be dynamic in production

                        for(Integer beacon : averageBeaconRSSIValues.keySet()) {
                            Map<String, Object> values = new HashMap<>();
                            values.put("major", beacon);
                            values.put("rssi", averageBeaconRSSIValues.get(beacon));
                            beaconInfo.add(values);
                        }

                        requestParameter.put("type", "fingerprint");
                        requestParameter.put("timestamp", System.currentTimeMillis());
                        requestParameter.put("beacons", beaconInfo);
                        requestParameter.put("information", locationInfo);

                        jsonFingerprintRequestString = (new JSONObject(requestParameter)).toString();

                        Log.v(TAG, "Request Map: " + requestParameter.toString());
                        Log.v(TAG, "Request JSON: " + jsonFingerprintRequestString);

                        // Send intent
                        final Intent finalPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                        finalPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_THREE);
                        getApplicationContext().sendBroadcast(finalPhaseBroadcast);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Globals.showSnackbar(findViewById(android.R.id.content), "Fingerprinting canceled.");
                        clearMaps();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void sendValues() {
        Log.d(TAG, "Sending values.");

        final String requestType = "application/json";

        AsyncHttpClient client = new AsyncHttpClient();
        StringEntity json = new StringEntity(jsonFingerprintRequestString, "UTF-8");
        json.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, requestType));

        final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("Sending values...");
        progressDialog.setMessage("Please wait.");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();

        client.post(MainActivity.this, Globals.SERVER_BASE_URL + URL_ENDPOINT, json, requestType, new AsyncHttpResponseHandler() {

            @Override
            public void onStart() {
                // Initiated the request
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                // Successfully got a response
                // Send intent to complete process
                final Intent notifyBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                notifyBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.SHOW_SUCCESS_MESSAGE);
                getApplicationContext().sendBroadcast(notifyBroadcast);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error)
            {
                // Request failed
                Globals.showDialogWithOKButton(getApplicationContext(), "Sending Failed", "Sending fingerprint data failed. (Server response code: " + statusCode + ")\n\nFingerprinting has been aborted.");
                Globals.showSnackbar(findViewById(android.R.id.content), "Fingerprinting failed.");
            }

            @Override
            public void onRetry(int retryNo) {
                // Request was retried
                progressDialog.setTitle("Sending values...Attempt #" + retryNo);
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
                // Progress notification
                progressDialog.setMessage("Please wait. " + bytesWritten + " of " + totalSize + " bytes sent.");
            }

            @Override
            public void onFinish() {
                // Completed the request (either success or failure)
                progressDialog.dismiss();
                // Clear maps to prepare for next fingerprint
                clearMaps();
            }
        });
    }

    private void clearCoordinates() {
        Arrays.fill(mapView.lastTouchCoordinates, -1);
        coordView.setText("None");
        mapView.invalidate();
    }

    private void clearMaps() {
        beaconRssiValues.clear();
        averageBeaconRSSIValues.clear();
        requestParameter.clear();
        beaconInfo.clear();
        locationInfo.clear();
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

    private class fingerprintReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent received.");
            final Bundle intentPayload = intent.getExtras();
            final fingerprintingPhase target = (fingerprintingPhase)intentPayload.get(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY);

            assert (target != null);

            switch (target) {
                case PHASE_ONE:
                    retrieveRSSIValues();
                    break;

                case PHASE_TWO:
                    processValues();
                    break;

                case PHASE_THREE:
                    sendValues();
                    break;

                case SHOW_SUCCESS_MESSAGE:
                    Globals.showSnackbar(findViewById(android.R.id.content), "Fingerprinting complete at (" + mapView.lastTouchCoordinates[0] + ", " + mapView.lastTouchCoordinates[1] + ").");
                    break;

            }

        }
    }

}
