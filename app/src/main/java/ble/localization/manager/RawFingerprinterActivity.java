package ble.localization.manager;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AlertDialog;
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
import com.jaredrummler.android.device.DeviceName;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;

/**
 * The Main Activity (i.e. our fingerprinter).
 */
public class RawFingerprinterActivity extends AppCompatActivity {

    // Some general variables
    private static final String TAG = "RawFingerprintActivity";
    private static final int timeToRecord = 10000;
    private static final String URL_ENDPOINT = "/fingerprint";
    protected static String deviceName;

    // The map and camera views
    private MapView mapView;
    private CameraView cameraView;

    // Beacon-related variables
    private BeaconManager fingerprintingBeaconManager;
    private static boolean[] isEstimoteRangingServiceReady = new boolean[1];

    // Broadcast receivers and intent filters
    private BroadcastReceiver mCoordinateChangeReceiver = new CoordinateChangeReceiver();
    private BroadcastReceiver mFingerprintReceiver = new FingerprintReceiver();
    private BroadcastReceiver mCNRequestReceiver = new CNRequestReceiver();
    private BroadcastReceiver mViewImageURLReceiver = new ImageURLReceiver();
    private BroadcastReceiver mRequestNextCoordinates = new DisplayInstructionsToSelectNextCoord();
    private BroadcastReceiver mSendCoordToMap = new SendNewCoordinatesFromViewToMap();
    private BroadcastReceiver mBeginFingerprinting = new BeginFingerprintingReceiver();
    private IntentFilter coordinateChangeFilter = new IntentFilter(MapView.COORDINATE_TEXT_UPDATE_BROADCAST);
    private IntentFilter fingerprintFilter = new IntentFilter(FINGERPRINT_BROADCAST_ACTION);
    private IntentFilter CNRequestFilter = new IntentFilter(MapView.PROVIDE_C_AND_N_VALUES);
    private IntentFilter ImageURLRequestFilter = new IntentFilter(CameraView.SEND_IMAGE_URL_BROADCAST);
    private IntentFilter nextCoordinateRequestFilter = new IntentFilter(MapView.SELECT_NEXT_COORDINATE_REQUEST);
    private IntentFilter sendCoordToMapRequestFilter = new IntentFilter(CameraView.SEND_COORD_TO_MAP_BROADCAST);
    private IntentFilter beginFingerprintingFilter = new IntentFilter(BEGIN_FINGERPINTING_BROADCAST);

    // Broadcast-related objects
    private TextView coordView;
    public static final String FINGERPRINT_BROADCAST_ACTION = "ble.localization.fingerprinter.FINGERPRINT";
    public static final String BEGIN_FINGERPINTING_BROADCAST = "ble.localization.fingerprinter.BEGIN_FP";
    private static ProgressDialog postProgressDialog;

    protected enum fingerprintingPhase {
        PHASE_ONE,
        PHASE_TWO,
        PHASE_THREE,
        SHOW_SUCCESS_MESSAGE
    }

    // Data holders for our Fingerprint
    static int FPS_SENT = 0;
    private ArrayList<LinkedHashMap<Integer, Integer>> listOfMajorRawValues = new ArrayList<>();
    private ArrayList<String> listOfRequestStrings = new ArrayList<>();

    private int floor_curr_index = Globals.floor_start_index;
    private Resources resources;    // Required to get our floor plan

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
        setContentView(R.layout.activity_rawfingerprinter);

        // Get the device model for the fingerprint.
        resources = this.getResources();
        deviceName = DeviceName.getDeviceName();

        ProgressDialog loading_dialog = ProgressDialog.show(RawFingerprinterActivity.this, "Loading map", "Please wait...", true);
        loading_dialog.setCancelable(false);
        loading_dialog.setCanceledOnTouchOutside(false);

        // Load the starting floor plan.
        mapView = (MapView) findViewById(R.id.mapView);
        int startPlanResID = getFloorPlanResourceID(formatToValidResourceName(Globals.floor_names[floor_curr_index]));
        mapView.setImage(ImageSource.resource(startPlanResID));

        // Initialize the CameraView.
        cameraView = (CameraView) findViewById(R.id.cameraView);
        cameraView.setImage(ImageSource.resource(R.drawable.placeholder));

        loading_dialog.dismiss();

        postProgressDialog = new ProgressDialog(RawFingerprinterActivity.this);

        // Set that the Estimote service is not ready.
        isEstimoteRangingServiceReady[0] = false;

        // Find the text view that shows the coordinates.
        coordView = (TextView) findViewById(R.id.coordinateText);

        // Find and set the fingerprint button.
        final Button fingerprintButton = (Button) findViewById(R.id.fingerprintButton);
        assert (fingerprintButton != null);
        fingerprintButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent beginFingerprinting = new Intent(RawFingerprinterActivity.BEGIN_FINGERPINTING_BROADCAST);
                sendBroadcast(beginFingerprinting);
            }
        });

        // Find and set the clear button.
        final Button clearButton = (Button) findViewById(R.id.clearButton);
        assert (clearButton != null);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearCoordinates();
            }
        });

        // Estimote SDK-related code.
        fingerprintingBeaconManager = new BeaconManager(this);

        fingerprintingBeaconManager.setRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(Region region, List<Beacon> list) {
                // Beacon discovery code during fingerprinting.
                LinkedHashMap<Integer, Integer> majorRawValues = new LinkedHashMap<>();

                for (Beacon b : list) {
                    majorRawValues.put(b.getMajor(), b.getRssi());
                }

                if(majorRawValues.keySet().size() != 0) {
                    listOfMajorRawValues.add(majorRawValues);
                }
            }
        });

        // Connect to the ranging service.
        fingerprintingBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                isEstimoteRangingServiceReady[0] = true;
            }
        });

    }

    /**
     * Called so that the activity can start interacting with the user.
     */
    @Override
    public void onResume() {
        super.onResume();
        // Register all receivers.
        this.registerReceiver(mCoordinateChangeReceiver, coordinateChangeFilter);
        this.registerReceiver(mFingerprintReceiver, fingerprintFilter);
        this.registerReceiver(mCNRequestReceiver, CNRequestFilter);
        this.registerReceiver(mViewImageURLReceiver, ImageURLRequestFilter);
        this.registerReceiver(mRequestNextCoordinates, nextCoordinateRequestFilter);
        this.registerReceiver(mSendCoordToMap, sendCoordToMapRequestFilter);
        this.registerReceiver(mBeginFingerprinting, beginFingerprintingFilter);
    }

    /**
     * Called when an activity is going into the background, but has not (yet) been killed.
     * The counterpart to onResume.
     */
    @Override
    public void onPause() {
        this.unregisterReceiver(this.mCoordinateChangeReceiver);
        this.unregisterReceiver(this.mFingerprintReceiver);
        this.unregisterReceiver(this.mCNRequestReceiver);
        this.unregisterReceiver(this.mViewImageURLReceiver);
        this.unregisterReceiver(this.mRequestNextCoordinates);
        this.unregisterReceiver(this.mSendCoordToMap);
        this.unregisterReceiver(this.mBeginFingerprinting);
        super.onPause();
    }

    /**
     * Performs final cleanup before an activity is destroyed.
     */
    @Override
    public void onDestroy() {
        // Disconnect the beacon manager.
        Globals.disconnectBeaconManager(fingerprintingBeaconManager, isEstimoteRangingServiceReady);
        super.onDestroy();
    }

    /**
     * Initialize the contents of the Activity's standard options menu.
     * @param menu The options menu in question.
     * @return True for the menu to be displayed.
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.fingerprinter_options_menu, menu);
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
            case R.id.cmod_launch:
                CoordinateModifierDialog cmdView = new CoordinateModifierDialog(this, mapView.thisTouchCoordinates[0], mapView.thisTouchCoordinates[1]);
                cmdView.show();
                break;
            case R.id.enlarge_view:
                // enlarge the camera view
                if (!cameraView.isImagePresent()) {
                    Globals.showSnackbar(findViewById(android.R.id.content),
                            "There is no camera view to enlarge!");
                    break;
                }
                EnlargedCameraView this_view = new EnlargedCameraView(this, cameraView.curr_image_url);
                this_view.downloadImage();
                this_view.show();
                break;
            case R.id.go_down:
                // go down a floor
                if(floor_curr_index - 1 <= 0) {
                    Globals.showSnackbar(findViewById(android.R.id.content),
                            "You're on the bottommost floor!");
                    break;
                }
                --floor_curr_index;
                int lowerPlanResID = getFloorPlanResourceID(formatToValidResourceName(Globals.floor_names[floor_curr_index]));
                mapView.setImage(ImageSource.resource(lowerPlanResID));
                clearCoordinates();
                break;
            case R.id.go_up:
                // go up a floor
                if(floor_curr_index + 1 >= Globals.floor_names.length) {
                    Globals.showSnackbar(findViewById(android.R.id.content),
                            "You're on the topmost floor!");
                    break;
                }
                ++floor_curr_index;
                int higherPlanResID = getFloorPlanResourceID(formatToValidResourceName(Globals.floor_names[floor_curr_index]));
                mapView.setImage(ImageSource.resource(higherPlanResID));
                clearCoordinates();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    /**
     * Commence the fingerprinting process.
     */
    private void beginFingerprinting() {
        // Is the ranging service ready?
        if (!isEstimoteRangingServiceReady[0]) {
            Globals.showDialogWithOKButton(this, "Beacon Ranging Not Ready", "Please wait until the ranging service is ready.");
            return;
        }

        // Have any coordinates been selected?
        if ((mapView.thisTouchCoordinates[0] == -1 && mapView.thisTouchCoordinates[1] == -1)
                || mapView.lastTouchCoordinates[0] == -1 && mapView.lastTouchCoordinates[1] == -1) {
            Globals.showDialogWithOKButton(this, "Select Coordinates", "Please select coordinates before fingerprinting.");
            return;
        }

        // Do we have the appropriate permissions?
        if (!SystemRequirementsChecker.checkWithDefaultDialogs(this)) {
            Globals.showDialogWithOKButton(this,
                    "Required Permissions Not Granted",
                    "This app requires Location and Bluetooth permissions to function properly." +
                            " Please grant these permissions and restart fingerprinting.");
        } else {
            // Else we send the intent
            final Intent beginFingerprinting = new Intent(FINGERPRINT_BROADCAST_ACTION);
            beginFingerprinting.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_ONE);
            sendBroadcast(beginFingerprinting);
        }
    }

    /**
     * Capture the RSSI values.
     */
    private void retrieveRSSIValues() {
        // Start ranging for the specified amount of time.
        fingerprintingBeaconManager.startRanging(Globals.region);
        Log.d(TAG, "Ranging started.");

        // Show a ProgressDialog that shows the time remaining.
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Fingerprinting...");
        progressDialog.setMessage("Please wait - 00:" + (timeToRecord / 1000) + " remaining.");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new CountDownTimer(timeToRecord, 1000) {

            public void onTick(long millisUntilFinished) {
                String formatted_number = String.format(Locale.ENGLISH, "%02d", millisUntilFinished / 1000);
                progressDialog.setMessage("Please wait - 00:" + formatted_number + " remaining.");
            }

            public void onFinish() {
                fingerprintingBeaconManager.stopRanging(Globals.region);
                Log.d(TAG, "Ranging stopped.");
                progressDialog.dismiss();

                // Send the intent to begin the next phase.
                final Intent nextPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                nextPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_TWO);
                sendBroadcast(nextPhaseBroadcast);
                Log.d(TAG, "Retrieval done.");
            }
        }.start();

    }

    /**
     * Confirm the captured fingerprint with the user, and process all of the captured values.
     */
    private void processValues() {
        Log.d(TAG, "Processing values.");
        Log.v(TAG, "ALL Values: " + listOfMajorRawValues.toString());

        // We might not have found any beacons. Thus, return here.
        if (listOfMajorRawValues.size() == 0) {
            Globals.showDialogWithOKButton(this, "No Beacons Detected",
                    "Make sure you are in the range of beacons and that they are working.");
            return;
        }

        String message = listOfMajorRawValues.size() + " sets of readings were obtained.\n";
        message += "\nWould you like to submit these fingerprints?";

        // Confirm using an Alert Dialog.
        new AlertDialog.Builder(this)
                .setTitle("Confirm Fingerprint")
                .setMessage(message)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        for(LinkedHashMap<Integer, Integer> s : listOfMajorRawValues) {
                            // Process some more.
                            // Put in location and phone information.
                            LinkedHashMap<String, Object> requestParameter = new LinkedHashMap<>();
                            ArrayList<Object> beaconInfo = new ArrayList<>();
                            LinkedHashMap<String, Object> locationInfo = new LinkedHashMap<>();

                            locationInfo.put("x", mapView.thisTouchCoordinates[0]);
                            locationInfo.put("y", mapView.thisTouchCoordinates[1]);
                            locationInfo.put("floor_num", floor_curr_index);
                            locationInfo.put("floor", Globals.floor_names[floor_curr_index]);
                            locationInfo.put("phone_model", deviceName);

                            // Put in information for each beacon detected.
                            for (Integer beacon : s.keySet()) {
                                Map<String, Object> values = new HashMap<>();
                                values.put("major", beacon);
                                values.put("rssi", s.get(beacon));
                                values.put("number_of_times_detected", 1); // only detected one time
                                ArrayList<Integer> temp = new ArrayList<>();
                                temp.add(s.get(beacon));
                                values.put("raw_values", new JSONArray(temp).toString());
                                beaconInfo.add(values);
                            }

                            // Put in fingerprint info.
                            requestParameter.put("type", "fingerprint");
                            requestParameter.put("f_id", null);
                            requestParameter.put("timestamp", System.currentTimeMillis());
                            requestParameter.put("beacons", beaconInfo);
                            requestParameter.put("information", locationInfo);

                            // Convert to a string.
                            String jsonFingerprintRequestString = (new JSONObject(requestParameter)).toString();
                            listOfRequestStrings.add(jsonFingerprintRequestString);

                            Log.v(TAG, "Request Map: " + requestParameter.toString());
                            Log.v(TAG, "Request JSON: " + jsonFingerprintRequestString);
                        }

                        // Send the intent for the next phase.
                        final Intent finalPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                        finalPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_THREE);
                        sendBroadcast(finalPhaseBroadcast);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // If no, cancel the process.
                        dialog.dismiss();
                        Globals.showSnackbar(findViewById(android.R.id.content), "Fingerprinting canceled.");
                        clearMaps();
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Send the fingerprint to the server.
     */
    private void sendValues() {
        Log.d(TAG, "Sending values.");

        final String requestType = "application/json";

        postProgressDialog.setTitle("Sending values...");
        postProgressDialog.setMessage("Please wait. Sent " + FPS_SENT + "/" + listOfMajorRawValues.size() + " fingerprints.");
        postProgressDialog.setIndeterminate(true);
        postProgressDialog.setCancelable(false);
        postProgressDialog.show();

        if(FPS_SENT >= listOfRequestStrings.size()) {
            postProgressDialog.dismiss();

            // Send intent to complete process
            final Intent notifyBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
            notifyBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.SHOW_SUCCESS_MESSAGE);
            sendBroadcast(notifyBroadcast);
            // Clear maps to prepare for next fingerprint
            clearMaps();
            return;
        }

        AsyncHttpClient client = new AsyncHttpClient();
        String request = listOfRequestStrings.get(FPS_SENT);
        StringEntity json = new StringEntity(request, "UTF-8");
        json.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, requestType));
        client.post(RawFingerprinterActivity.this, Globals.SERVER_BASE_API_URL + URL_ENDPOINT, json, requestType, new AsyncHttpResponseHandler() {

            @Override
            public void onStart() {
                // Initiated the request
                postProgressDialog.setMessage("Please wait. Sent " + FPS_SENT + "/" + listOfRequestStrings.size() + " fingerprints.");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                // Successfully got a response
                FPS_SENT++;

                // Send intent
                final Intent finalPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                finalPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_THREE);
                sendBroadcast(finalPhaseBroadcast);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                // Request failed
                Globals.showSnackbar(findViewById(android.R.id.content), "Fingerprinting failed.");

                new AlertDialog.Builder(RawFingerprinterActivity.this)
                        .setTitle("Sending Failed for #" + (FPS_SENT + 1))
                        .setMessage("Sending fingerprint data failed. (Server response code: " + statusCode + ")")
                        .setCancelable(false)
                        .setNegativeButton("Skip One", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                FPS_SENT++;

                                // Send intent
                                final Intent finalPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                                finalPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_THREE);
                                sendBroadcast(finalPhaseBroadcast);
                            }
                        })
                        .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();

                                // Send intent
                                final Intent finalPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                                finalPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_THREE);
                                sendBroadcast(finalPhaseBroadcast);
                            }
                        })
                        .setNeutralButton("Skip All", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                FPS_SENT = listOfRequestStrings.size();

                                // Send intent
                                final Intent finalPhaseBroadcast = new Intent(FINGERPRINT_BROADCAST_ACTION);
                                finalPhaseBroadcast.putExtra(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY, fingerprintingPhase.PHASE_THREE);
                                sendBroadcast(finalPhaseBroadcast);
                            }
                        })
                        .show();
            }

            @Override
            public void onRetry(int retryNo) {
                // Request was retried
                postProgressDialog.setTitle("Sending values (Att. #" + retryNo + ")");
            }

            @Override
            public void onProgress(long bytesWritten, long totalSize) {
                // Progress notification
                // postProgressDialog.setMessage("Please wait. " + bytesWritten + " of " + totalSize + " sent.");
            }

            @Override
            public void onFinish() {
                // Completed the request (either success or failure)
            }
        });
    }

    /**
     * Clear the coordinates on the map and textview.
     */
    private void clearCoordinates() {
        Arrays.fill(mapView.thisTouchCoordinates, -1);
        Arrays.fill(mapView.lastTouchCoordinates, -1);
        Arrays.fill(cameraView.lastTouchPixelCoordinates, -1);
        Arrays.fill(cameraView.lastTouchRealCoordinates, -1);
        coordView.setText("Select your initial camera view.");
        mapView.invalidate();
        cameraView.setImage(ImageSource.resource(R.drawable.placeholder));
    }

    /**
     * Clear the data holders.
     */
    private void clearMaps() {
        listOfMajorRawValues.clear();
        listOfRequestStrings.clear();
        FPS_SENT = 0;
    }

    /**
     * Broadcast receiver - Receives notification that the coordinates of the point on the map have been changed.
     */
    private class CoordinateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Change coordinates on screen
            coordView.setText("(" + mapView.thisTouchCoordinates[0] + ", " + mapView.thisTouchCoordinates[1] + ")");
        }
    }

    /**
     * Broadcast receiver - Updated the camera view using the new coordinates.
     */
    private class CNRequestReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Bundle intentPayload = intent.getExtras();
            final float x_c = (float) intentPayload.get("x_c");
            final float y_c = (float) intentPayload.get("y_c");
            final float x_n = (float) intentPayload.get("x_n");
            final float y_n = (float) intentPayload.get("y_n");

            cameraView.updateCameraView(x_c, y_c, x_n, y_n);
        }
    }

    /**
     * Broadcast receiver - Receives the URL to the new camera view, downloads it, and sets it.
     */
    private class ImageURLReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Bundle intentPayload = intent.getExtras();
            final String view_image_url = (String) intentPayload.get("url");

            assert (view_image_url != null);

            new DownloadImageTask(cameraView).execute(view_image_url);
        }
    }

    /**
     * Broadcast receiver - Invalidates the map view and tells the user to set new coordinates.
     */
    private class DisplayInstructionsToSelectNextCoord extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mapView.invalidate();
            coordView.setText("Set approximate fingerprinting location.");
        }
    }

    /**
     * Broadcast receiver - Sends new coordinates from the view to the map.
     */
    private class SendNewCoordinatesFromViewToMap extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Bundle intentPayload = intent.getExtras();
            final float new_x = (float)(double)intentPayload.get("x");
            final float new_y = (float)(double)intentPayload.get("y");
            mapView.thisTouchCoordinates[0] = new_x;
            mapView.thisTouchCoordinates[1] = new_y;

            Intent change_coordinate_text = new Intent(MapView.COORDINATE_TEXT_UPDATE_BROADCAST);
            context.sendBroadcast(change_coordinate_text);

            mapView.invalidate();
        }
    }

    /**
     * Broadcast receiver - Facilitates the fingerprinting process.
     */
    private class FingerprintReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent received.");
            final Bundle intentPayload = intent.getExtras();
            final fingerprintingPhase target = (fingerprintingPhase) intentPayload.get(Globals.PHASE_CHANGE_BROADCAST_PAYLOAD_KEY);

            if (target == null) throw new AssertionError("The fingerprintingPhase target should not be null!");

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
                    Globals.showSnackbar(findViewById(android.R.id.content), "Fingerprinting complete at (" + mapView.thisTouchCoordinates[0] + ", " + mapView.thisTouchCoordinates[1] + ").");
                    break;

            }
        }
    }

    /**
     * Broadcast receiver - Begins fingerprinting.
     */
    private class BeginFingerprintingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            beginFingerprinting();
        }
    }
}
