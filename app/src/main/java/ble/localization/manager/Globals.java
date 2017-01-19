package ble.localization.manager;

import android.content.Context;
import android.content.DialogInterface;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.view.View;

import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;

import java.util.UUID;

/**
 * Contains variables used by all classes.
 */
final class Globals {

    static final String PHASE_CHANGE_BROADCAST_PAYLOAD_KEY = "TARGET_PHASE";

    static String[] ALL_URLS = {"http://192.168.0.10:8000", "http://argon.olmschenk.com", "http://ble-server.192.168.0.10.nip.io"};
    static final int DEFAULT_URL = 0;
    static String SERVER_BASE_URL = ALL_URLS[DEFAULT_URL];
    static final String SERVER_BASE_API_URL = SERVER_BASE_URL + "/api";

    private static final UUID beaconRegionUUID = UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D");
    private static final String beaconRegionName = "ranged region";
    static final Region region = new Region(beaconRegionName, beaconRegionUUID, null, null);

    static final String[] floor_names = {null, "C Level", "Ground"};  // Manually set the floor names at this time
    static final int floor_start_index = 1; // Manually set

    // Bool to tell us if we're using the image fetching for the camera view.
    static final boolean usingCameraViewImageFetch = false;

    static void disconnectBeaconManager(BeaconManager mBeaconManager, boolean[] isEstimoteRangingServiceReady) {
        mBeaconManager.disconnect();
        isEstimoteRangingServiceReady[0] = false;
    }

    static void showSnackbar(View view, String snackbarText) {
        Snackbar
                .make(view, snackbarText, Snackbar.LENGTH_SHORT)
                .show();
    }

    static void showDialogWithOKButton(Context context, String title, String message) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
}
