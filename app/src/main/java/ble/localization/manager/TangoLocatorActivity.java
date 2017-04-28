package ble.localization.manager;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static org.opencv.core.CvType.CV_32FC1;
import static org.opencv.imgproc.Imgproc.getAffineTransform;

/**
 * Our locator.
 */
public class TangoLocatorActivity extends AppCompatActivity {

    static {
        System.loadLibrary("opencv_java3");
    }

    // Some general variables.
    private static final String TAG = "TangoLocatorActivity";

    private MapView mapView;
    private TextView coordView;
    public static final String LOCATOR_BROADCAST_ACTION = "ble.localization.locator.LOCATE";

    // Broadcast receivers and intent filters
    private BroadcastReceiver mCoordinateChangeReceiver = new coordinateChangeReceiver();
    private BroadcastReceiver mLocalizationReceiver = new localizationReceiver();
    private IntentFilter coordinateChangeFilter = new IntentFilter(MapView.COORDINATE_TEXT_UPDATE_BROADCAST);
    private IntentFilter localizationFilter = new IntentFilter(LOCATOR_BROADCAST_ACTION);

    // Button-related stuff
    private Button locateButton;
    private boolean tapToLocateEnabled;

    // Floor/floor-plan-related stuff
    private int floor_curr_index = Globals.floor_start_index;
    private String curr_floor = Globals.floor_names[floor_curr_index];
    private String prev_floor = curr_floor;
    private Resources resources;

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
        setContentView(R.layout.activity_locator_tango);

        resources = this.getResources();

        ProgressDialog loading_dialog = ProgressDialog.show(TangoLocatorActivity.this, "Loading map", "Please wait...", true);
        loading_dialog.setCancelable(false);
        loading_dialog.setCanceledOnTouchOutside(false);

        // Load the floor plan.
        mapView = (MapView)findViewById(R.id.mapView);
        int startPlanResID = getFloorPlanResourceID(formatToValidResourceName(Globals.floor_names[floor_curr_index]));
        mapView.setImage(ImageSource.resource(startPlanResID));
        mapView.setTouchAllowedBool(false);

        loading_dialog.dismiss();

        // Find the text view that shows the coordinates.
        coordView = (TextView)findViewById(R.id.coordinateText);

        Intent intent = getIntent();
        mIsLearningMode = intent.getBooleanExtra(AdfUuidListViewActivity.USE_AREA_LEARNING, false);
        mIsConstantSpaceRelocalize = intent.getBooleanExtra(AdfUuidListViewActivity.LOAD_ADF, false);
        selectedUUID = getIntent().getExtras().getString("uuidName");

        // Initialize the locator button.
        locateButton = (Button)findViewById(R.id.locateButton);
        assert (locateButton != null);
        locateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Switch the button between starting and stopping localization.
                if(tapToLocateEnabled) {
                    tapToLocateEnabled = false;
                    locateButton.setText("Stop Localization");
                } else {
                    tapToLocateEnabled = true;
                    locateButton.setText("Localize");
                }
            }
        });
        tapToLocateEnabled = true;

        String toastMessage = "Selected map: " + selectedUUID + "loaded";
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();

    }

    /**
     * Called so that the activity can start interacting with the user.
     */
    @Override
    public void onResume() {
        super.onResume();
        this.registerReceiver(mCoordinateChangeReceiver, coordinateChangeFilter);
        this.registerReceiver(mLocalizationReceiver, localizationFilter);

        // Initialize Tango Service as a normal Android Service, since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time when onResume gets called, we
        // should create a new Tango object.
        mTango = new Tango(TangoLocatorActivity.this, new Runnable() {
            @Override
            public void run() {
                synchronized (TangoLocatorActivity.this) {
                    try {
                        mConfig = setTangoConfig(
                                mTango, mIsLearningMode, mIsConstantSpaceRelocalize);
                        mTango.connect(mConfig);
                        startupTango();

                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, "Tango is out-of-date!", e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, "A TangoErrorException was thrown!", e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, "TangoInvalidException was thrown!", e);
                    } catch (SecurityException e) {
                        // Area Learning permissions are required. If they are not available,
                        // SecurityException is thrown.
                        Log.e(TAG, "Area Learning permissions are required.", e);
                    }
                }
            }
        });
    }

    /**
     * Called when an activity is going into the background, but has not (yet) been killed.
     * The counterpart to onResume.
     */
    @Override
    public void onPause() {
        this.unregisterReceiver(mCoordinateChangeReceiver);
        this.unregisterReceiver(mLocalizationReceiver);

        mIsRelocalized = false;

        synchronized (this) {
            try {
                mTango.disconnect();
            } catch (TangoErrorException e) {
                Log.e(TAG, "A Tango error exception was thrown!", e);
            }
        }

        super.onPause();
    }

    /**
     * Performs final cleanup before an activity is destroyed.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
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
            final Bundle intentPayload = intent.getExtras();
            final float x = intentPayload.getFloat("x");
            final float y = intentPayload.getFloat("y");

            mapView.thisTouchCoordinates[0] = x;
            mapView.thisTouchCoordinates[1] = y;

            // Update coordinate text
            Intent in = new Intent(MapView.COORDINATE_TEXT_UPDATE_BROADCAST);
            context.sendBroadcast(in);
            // For now:
            mapView.invalidate();
            // TODO: Later, update the MapView and do a floor switch.
//            if(!curr_floor.equals(prev_floor)) {
//                int newFloorResID = getFloorPlanResourceID(formatToValidResourceName(curr_floor));
//                mapView.setImage(ImageSource.resource(newFloorResID));
//                prev_x = prev_y = prev2_x = prev2_y = MapView.defaultCoord;
//                prev_floor = curr_floor;
//            } else {
//                mapView.invalidate();
//            }

        }
    }

    // Tango stuff
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;

    private float[] translation;
    private float[] orientation;

    private double mPreviousPoseTimeStamp;
    private static final double UPDATE_INTERVAL_MS = 100.0;
    private double mTimeToNextUpdate = UPDATE_INTERVAL_MS;

    private boolean mIsRelocalized;
    private boolean mIsLearningMode;
    private boolean mIsConstantSpaceRelocalize;

    private String mPositionString;
    private String mZRotationString;
    private float[] mDestinationTranslation = new float[3];

    private final Object mSharedLock = new Object();

    private float[] rotationsArray;
    private float mRotationDiff;
    private final float granularity = 0.5f;
    private String selectedUUID;

    private TangoPoseData currentPose;

    /**
     * Sets up the tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setTangoConfig(Tango tango, boolean isLearningMode, boolean isLoadAdf) {
        // Use default configuration for Tango Service.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        // Check if learning mode
        if (isLearningMode) {
            // Set learning mode to config.
            config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, true);

        }
        // Check for Load ADF/Constant Space relocalization mode.
        if (isLoadAdf) {
            ArrayList<String> fullUuidList;
            // Returns a list of ADFs with their UUIDs.
            fullUuidList = tango.listAreaDescriptions();
            // Load the latest ADF if ADFs are found.
            if (fullUuidList.size() > 0) {
                if (selectedUUID == null) {
                    config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION,
                            fullUuidList.get(fullUuidList.size() - 1));
                } else {
                    config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, selectedUUID);
                }
            }
        }
        return config;
    }

    /**
     * Set up the callback listeners for the Tango service and obtain other parameters required
     * after Tango connection.
     */
    private void startupTango() {
        // Set Tango Listeners for Poses Device wrt Start of Service, Device wrt
        // ADF and Start of Service wrt ADF.
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));
        framePairs.add(new TangoCoordinateFramePair(
                TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        mTango.connectListener(framePairs, new Tango.OnTangoUpdateListener() {

            @Override
            public void onPoseAvailable(final TangoPoseData pose) {
                // Make sure to have atomic access to Tango Data so that UI loop doesn't interfere
                // while Pose call back is updating the data.
                synchronized (mSharedLock) {
                    currentPose = pose;
                    Log.d(TAG, pose.toString());

                    // Check for Device wrt ADF pose, Device wrt Start of Service pose, Start of
                    // Service wrt ADF pose (This pose determines if the device is relocalized or
                    // not)

                    if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                            && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_DEVICE) {
                        if (pose.statusCode == TangoPoseData.POSE_VALID) {

                            mIsRelocalized = true;
                            Globals.showSnackbar(findViewById(android.R.id.content), "Localized.");

                            translation = pose.getTranslationAsFloats();
                            orientation = pose.getRotationAsFloats();

                            // CURRENT LOCATION

                            float currentX = translation[0];
                            float currentY = translation[1];

                            align_coordinates(currentX, currentY);

                            mPositionString = "X:" + translation[0] + ", Y:" + translation[1] + ", Z:" + translation[2];
                            mZRotationString = String.valueOf(MathFunctions.getEulerAngleZ(orientation));

                            final double deltaTime = (pose.timestamp - mPreviousPoseTimeStamp) *
                                    SECS_TO_MILLISECS;
                            mPreviousPoseTimeStamp = pose.timestamp;
                            mTimeToNextUpdate -= deltaTime;

                            if (mTimeToNextUpdate < 0.0 && !tapToLocateEnabled) {
                                mTimeToNextUpdate = UPDATE_INTERVAL_MS;

                                // Send the intent to complete the localization process.
                                final Intent updateMapView = new Intent(LOCATOR_BROADCAST_ACTION);
                                updateMapView.putExtra("x", currentX);
                                updateMapView.putExtra("y", currentY);
                                getApplicationContext().sendBroadcast(updateMapView);
                            }
                        } else {
                            mIsRelocalized = false;
                            Globals.showSnackbar(findViewById(android.R.id.content), "Invalid pose data received.");
                        }
                    }
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                // We are not using onXyzIjAvailable for this app.
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData xyzij) {
                // We are not using onPointCloudAvailable for this app.
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                // Ignoring TangoEvents.
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                // We are not using onFrameAvailable for this application.
            }
        });
    }

    private void align_coordinates(float currentX, float currentY){
        Point tango_coord1 = new Point(); // beacon 6708
        tango_coord1.x = 4.32;
        tango_coord1.y = -2.77;
        Point tango_coord2 = new Point(); // beacon 38988
        tango_coord2.x = 8.72;
        tango_coord2.y = -4.17;
        Point tango_coord3 = new Point(); // beacon 23110
        tango_coord3.x = 10.61;
        tango_coord3.y = -5.65;
        List<Point> tangoList = new ArrayList<Point>();
        tangoList.add(tango_coord1);
        tangoList.add(tango_coord2);
        tangoList.add(tango_coord3);

        Point map_coord1 = new Point(); // beacon 6708
        map_coord1.x = 777.21985;
        map_coord1.y = 1502.6268;
        Point map_coord2 = new Point(); // beacon 38988
        map_coord2.x = 598.5553;
        map_coord2.y = 2022.0793;
        Point map_coord3 = new Point(); // beacon 23110
        map_coord3.x = 250.12984;
        map_coord3.y = 2336.1667;
        List <Point> mapList = new ArrayList<Point>();
        mapList.add(map_coord1);
        mapList.add(map_coord2);
        mapList.add(map_coord3);

        MatOfPoint2f tangoPoints = new MatOfPoint2f();
        tangoPoints.fromList(tangoList);
        MatOfPoint2f mapPoints = new MatOfPoint2f();
        mapPoints.fromList(mapList);

        // get transformation matrix
        Mat warp_mat = new Mat( 2, 3, CV_32FC1);
        warp_mat = getAffineTransform(tangoPoints,mapPoints);

        // get current location
        Vector<Point> currentLocation = new Vector<Point>();
        Point currentLocationPoint = new Point();
        currentLocationPoint.x = currentX;
        currentLocationPoint.y = currentY;
        currentLocation.add(currentLocationPoint);

        // convert current location point to a matrix
        Mat pointWarp = Converters.vector_Point_to_Mat(currentLocation);

        // result matrix has same dimensions as original point matrix
        Mat resultMat = new Mat(pointWarp.rows(),pointWarp.cols(),CV_32FC1);

        // do matrix multiplication to transform point: resultMat = pointWarp * warp_mat
        Core.multiply(pointWarp,warp_mat,resultMat);

        Log.d("resultMat",String.valueOf(resultMat));

        // convert resultMat to a mat of points and get first (and only) point
        MatOfPoint2f newLocationMat = new MatOfPoint2f(resultMat);
        Point newLocationPoints[] = newLocationMat.toArray();
        Point newLocationPoint = new Point();
        newLocationPoint = newLocationPoints[0];

        // new coordinates of aligned point
        float newCurrentLocation_x = (float)newLocationPoint.x;
        float newCurrentLocation_y = (float)newLocationPoint.y;


    }
}
