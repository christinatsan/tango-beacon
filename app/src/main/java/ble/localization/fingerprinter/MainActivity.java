package ble.localization.fingerprinter;

import android.app.ProgressDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.estimote.sdk.SystemRequirementsChecker;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;

// To jsonify: https://developer.android.com/reference/org/json/JSONObject.html

public class MainActivity extends AppCompatActivity {

    private static final int PERCENT_CUTOFF = 15;

    private SubsamplingScaleImageView imageView;

    private BeaconManager beaconManager;
    private Region region;
    // When ready to range, "beaconManager.startRanging(region);"
    // When stopping, "beaconManager.stopRanging(region);"

    Map<Integer, ArrayList<Integer>> beacon_rssi_values = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ProgressDialog loading_dialog = ProgressDialog.show(MainActivity.this, "Loading map", "Please wait...", true);
        loading_dialog.setCancelable(false);
        loading_dialog.setCanceledOnTouchOutside(false);

        imageView = (SubsamplingScaleImageView)findViewById(R.id.imageView);
        imageView.setImage(ImageSource.resource(R.drawable.home_floor_plan));

        loading_dialog.dismiss();

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

    public static double trimmean(final ArrayList<Double> arr_list, final int percent) {
        Double[] arr2 = arr_list.toArray(new Double[arr_list.size()]);
        double[] arr = ArrayUtils.toPrimitive(arr2);

        final int n = arr.length;
        final int k = (int)Math.round(n * (percent / 100.0) / 2.0);
        final DoubleArrayList list = new DoubleArrayList(arr);
        list.sort();

        return Descriptive.trimmedMean(list, Descriptive.mean(list), k, k);
    }
}
