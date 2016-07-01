package ble.localization.fingerprinter;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.SystemRequirementsChecker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// To jsonify: https://developer.android.com/reference/org/json/JSONObject.html
// Fingerprinting countdown: http://stackoverflow.com/questions/10780651/display-a-countdown-timer-in-the-alert-dialog-box

public class MainActivity extends AppCompatActivity {

    private static final int PERCENT_CUTOFF = 15;
    private static final int DECIMAL_PLACES = 2;
    private static final String TAG = "FingerprinterMain";

    private modifiedSubsamplingScaleImageView imageView;

    private BeaconManager beaconManager;
    private Region region;
    // When ready to range, "beaconManager.startRanging(region);"
    // When stopping, "beaconManager.stopRanging(region);"

    private TextView coordView;
    private BroadcastReceiver coordinateChangeReceiver = new coordinateChangeReceiver();
    private IntentFilter filter = new IntentFilter(modifiedSubsamplingScaleImageView.BROADCAST_ACTION);

    private Map<Integer, ArrayList<Integer>> beacon_rssi_values = new HashMap<>();

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
        this.registerReceiver(coordinateChangeReceiver, filter);

        // Estimote-related code
        SystemRequirementsChecker.checkWithDefaultDialogs(this);

        beaconManager = new BeaconManager(this);
        region = new Region("ranged region", UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D"), null, null);

        beaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> list) {
                // Beacon discovery code goes here.
            }
        });


    }

    @Override
    public void onResume() {
        super.onResume();
        this.registerReceiver(coordinateChangeReceiver, filter);
    }

    @Override
    public void onPause() {
        this.unregisterReceiver(this.coordinateChangeReceiver);
        super.onPause();
    }

    protected void do_fingerprinting() {
        // Actual fingerprinting code
    }

    // Receives notification that the selected coordinates have been changed.
    private class coordinateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Change coordinates on screen
            coordView.setText("(" + imageView.lastTouchCoordinates[0] + ", " + imageView.lastTouchCoordinates[1] + ")");
        }
    }

}
