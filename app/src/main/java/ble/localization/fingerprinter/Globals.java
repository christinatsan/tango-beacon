package ble.localization.fingerprinter;

import android.content.Context;
import android.content.DialogInterface;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.view.View;

import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;

import java.util.UUID;

/**
 * Created by vishnunair on 7/6/16.
 */
public class Globals {

    protected static final String PHASE_CHANGE_BROADCAST_PAYLOAD_KEY = "TARGET_PHASE";
    protected static final String SERVER_BASE_URL = "http://192.168.0.10:5000";

    protected static final UUID beaconRegionUUID = UUID.fromString("B9407F30-F5F8-466E-AFF9-25556B57FE6D");
    protected static final String beaconRegionName = "ranged region";
    protected static final Region region = new Region(beaconRegionName, beaconRegionUUID, null, null);

    protected static void disconnectBeaconManager(BeaconManager mBeaconManager, boolean[] isEstimoteRangingServiceReady) {
        mBeaconManager.disconnect();
        isEstimoteRangingServiceReady[0] = false;
    }

    protected static void showSnackbar(View view, String snackbarText) {
        Snackbar
                .make(view, snackbarText, Snackbar.LENGTH_SHORT)
                .show();
    }

    protected static void showDialogWithOKButton(Context context, String title, String message) {
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
