package ble.localization.fingerprinter;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.CountDownTimer;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.SystemRequirementsChecker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// To jsonify: https://developer.android.com/reference/org/json/JSONObject.html

public class MainActivity extends AppCompatActivity {

    private static final int PERCENT_CUTOFF = 15;
    private static final int DECIMAL_PLACES = 2;
    private static final String TAG = "MainActivity";
    private static final int timeToRecord = 15000;

    private modifiedSubsamplingScaleImageView imageView;

    private BeaconManager beaconManager;
    private static final UUID beaconRegionUUID = UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D");
    private static final String beaconRegionName = "ranged region";
    private static final Region region = new Region(beaconRegionName, beaconRegionUUID, null, null);
    private static boolean isEstimoteRangingServiceReady = false;

    private TextView coordView;
    private BroadcastReceiver mCoordinateChangeReceiver = new coordinateChangeReceiver();
    private BroadcastReceiver mFingerprintReceiver = new fingerprintReceiver();
    private IntentFilter coordinateChangeFilter = new IntentFilter(modifiedSubsamplingScaleImageView.BROADCAST_ACTION);
    private IntentFilter fingerprintFilter = new IntentFilter(FINGERPRINT_BROADCAST_ACTION);

    public static final String FINGERPRINT_BROADCAST_ACTION = "ble.localization.fingerprinter.FINGERPRINT";
    public static final String BROADCAST_PAYLOAD_KEY = "TARGET_PHASE";
    public static final String PHASE_ONE_BROADCAST = "BEGIN_RSSI_RETRIEVAL";
    public static final String PHASE_TWO_BROADCAST = "BEGIN_PROCESSING";
    public static final String PHASE_THREE_BROADCAST = "BEGIN_TRANSMISSION";
    public static final String NOTIFY_COMPLETE_BROADCAST = "SHOW_SUCCESS_MESSAGE";

    private Map<Integer, ArrayList<Integer>> beaconRssiValues = new HashMap<>();
    private Map<Integer, Double> averageBeaconRSSIValues = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ProgressDialog loading_dialog = ProgressDialog.show(MainActivity.this, "Loading map", "Please wait...", true);
        loading_dialog.setCancelable(false);
        loading_dialog.setCanceledOnTouchOutside(false);

        imageView = (modifiedSubsamplingScaleImageView)findViewById(R.id.mapView);
        imageView.setImage(ImageSource.resource(R.drawable.home_floor_plan));

        loading_dialog.dismiss();

        coordView = (TextView)findViewById(R.id.coordinateText);

        this.registerReceiver(mCoordinateChangeReceiver, coordinateChangeFilter);
        this.registerReceiver(mFingerprintReceiver, fingerprintFilter);

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
        beaconManager = new BeaconManager(this);

        beaconManager.setRangingListener(new BeaconManager.RangingListener() {
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

        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                isEstimoteRangingServiceReady = true;
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
        } catch (IllegalArgumentException e) { }
        beaconManager.disconnect();
        super.onDestroy();
    }


    private void beginFingerprinting() {
        // Actual fingerprinting code
        if(!isEstimoteRangingServiceReady) {
            showDialogWithOKButton("Beacon Ranging Not Ready", "Please wait until the ranging service is ready.");
            return;
        }

        if(imageView.lastTouchCoordinates[0] == -1 && imageView.lastTouchCoordinates[1] == -1) {
            showDialogWithOKButton("Select Coordinates", "Please select coordinates before fingerprinting.");
            return;
        }

        SystemRequirementsChecker.checkWithDefaultDialogs(this);

        // Send intent
        final Intent beginFingerprinting = new Intent(FINGERPRINT_BROADCAST_ACTION);
        beginFingerprinting.putExtra(BROADCAST_PAYLOAD_KEY, PHASE_ONE_BROADCAST);
        getApplicationContext().sendBroadcast(beginFingerprinting);
    }

    private void retrieveRSSIValues() {
        beaconManager.startRanging(region);
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
                beaconManager.stopRanging(region);
                Log.d(TAG, "Ranging stopped.");
                progressDialog.dismiss();

                // Send intent
                final Intent nextPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                nextPhaseBroadcast.putExtra(BROADCAST_PAYLOAD_KEY, PHASE_TWO_BROADCAST);
                getApplicationContext().sendBroadcast(nextPhaseBroadcast);
                Log.d(TAG, "Retrieval done.");
            }
        }.start();

    }

    private void processValues() {
        Log.d(TAG, "Processing values.");
        Log.v(TAG, "ALL Values: " + beaconRssiValues.toString());

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
                            Double avg = mathFunctions.doubleRound(mathFunctions.trimmedMean(RSSIs, PERCENT_CUTOFF), DECIMAL_PLACES);
                            averageBeaconRSSIValues.put(key, avg);
                        }

                        Log.v(TAG, "AVERAGED Values: " + averageBeaconRSSIValues.toString());

                        // Send intent
                        final Intent finalPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                        finalPhaseBroadcast.putExtra(BROADCAST_PAYLOAD_KEY, PHASE_THREE_BROADCAST);
                        getApplicationContext().sendBroadcast(finalPhaseBroadcast);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        showSnackbar("Fingerprinting canceled.");
                        beaconRssiValues.clear();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void sendValues() {
        Log.d(TAG, "Sending values.");

        // Send intent
        final Intent notifyBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
        notifyBroadcast.putExtra(BROADCAST_PAYLOAD_KEY, NOTIFY_COMPLETE_BROADCAST);
        getApplicationContext().sendBroadcast(notifyBroadcast);

        // Clear maps to prepare for next fingerprint
        beaconRssiValues.clear();
        averageBeaconRSSIValues.clear();
    }

    private void clearCoordinates() {
        Arrays.fill(imageView.lastTouchCoordinates, -1);
        coordView.setText("None");
        imageView.invalidate();
    }

    // Receives notification that the selected coordinates have been changed.
    private class coordinateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Change coordinates on screen
            coordView.setText("(" + imageView.lastTouchCoordinates[0] + ", " + imageView.lastTouchCoordinates[1] + ")");
        }
    }

    private class fingerprintReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent received.");
            final Bundle intentPayload = intent.getExtras();
            final String target = intentPayload.getString(BROADCAST_PAYLOAD_KEY);

            switch (target) {
                case PHASE_ONE_BROADCAST:
                    retrieveRSSIValues();
                    break;

                case PHASE_TWO_BROADCAST:
                    processValues();
                    break;

                case PHASE_THREE_BROADCAST:
                    sendValues();
                    break;

                case NOTIFY_COMPLETE_BROADCAST:
                    showSnackbar("Fingerprinting complete at (" + imageView.lastTouchCoordinates[0] + ", " + imageView.lastTouchCoordinates[1] + ").");
                    break;

            }

        }
    }

    private void showSnackbar(String snackbarText) {
        Snackbar
                .make(this.findViewById(android.R.id.content), snackbarText, 3000)
                .show();
    }

    private void showDialogWithOKButton(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
}
