package ble.localization.manager;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.apache.commons.lang3.text.WordUtils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import uk.co.senab.photoview.PhotoViewAttacher;

import static org.opencv.core.CvType.CV_64F;
import static org.opencv.imgproc.Imgproc.getAffineTransform;

public class TangoNavigatorActivity extends AppCompatActivity implements View.OnClickListener {

    static {
        System.loadLibrary("opencv_java3");
    }

    // Initialize global variables
    private static final String TAG = "NavigatorActivity";
    public static final int ZXING_REQUEST_CODE = 0x0000c0de;
    public static final int SPEECH_REQUEST_CODE = 123;
    private static final String TEXT_RETRIEVAL_BASE_URL = Globals.SERVER_BASE_URL + "/media/graph_txts/";

    private ArrayList<Path> route = new ArrayList<>();
    private ArrayList<Integer> distances = new ArrayList<>();
    private ArrayList<PointF> routePoints = new ArrayList<>();
    private Toolbar mainToolbar;
    private ImageButton mapButtonNext, mapButtonPrev, speechButton, scanButton, directionNext, directionPrev;
    private TextView locationTxt, destTxt, directionText;
    private TextToSpeech tts;
    public AlertDialog.Builder destinationsMenu, startPointMenu, unitsMenu, beaconTypesMenu;
    private Toast toastError, toastSuccess;
    private ImageView floorMap;
    private PhotoViewAttacher fAttacher;
    private int[] imgArray;
    private ArrayList<Bitmap> floors = new ArrayList<>();
    private ArrayList<String> qrCodes = new ArrayList<>();
    private ArrayList<Integer> FloorIdx = new ArrayList<>();
    private ArrayList<Integer> xCoords = new ArrayList<>();
    private ArrayList<Integer> yCoords = new ArrayList<>();
    private ArrayList<String> Locations = new ArrayList<>();
    private ArrayList<Integer> NodeIdx = new ArrayList<>();
    private ArrayList<DijkstraAlgorithm> floorGraph = new ArrayList<>();
    private ArrayList<ArrayList<Vertex>> nodeArray = new ArrayList<>();
    private ArrayList<ArrayList<Point>> nodePoints = new ArrayList<>();
    private ArrayList<String> listDirections = new ArrayList<>();
    private ArrayList<Point> targetNodes = new ArrayList<>();
    private int lastScan = -1;
    private int curMap = 0;
    private int curDirection = 0;
    private Vibrator vibrate;
    private boolean isVibrationOn = true;
    private int tappedCode = -1;
    private int tappedCodePrev = -2;
    private boolean samePoint = false;
    private boolean isMatch = false;

    public enum measurementUnits {
        IMPERIAL,
        METRIC,
        STEPS
    }

    public String[] stringListOfAllowedUnits;
    private measurementUnits[] enumListOfAllowedUnits = measurementUnits.values();
    private measurementUnits currUnits = measurementUnits.IMPERIAL; // use imperial as default

    private static double allowedErrorInFeet = 4.8;
    private static final double feetPerStep = 2.4;
    private static final double feetPerMeter = 3.28084;

    private boolean isSpeechOn = true;
    private boolean curPath = false;
    private boolean sayDest = false;
    private int x_tap,y_tap;
    private Matrix prevZoom;

    private String[] floorNames;
    private float[] mapScaleFeet;
    private float[] mapScaleMeters;

    private Point currPosition = new Point(0,0);
    private float currPosition_floatX = 0.0f;
    private float currPosition_floatY = 0.0f;
    private Menu menu;
    private boolean localizationIsDisabled = true;

    private float prev_x = MapView.defaultCoord;
    private float prev_y = MapView.defaultCoord;

    private float prev2_x = MapView.defaultCoord;
    private float prev2_y = MapView.defaultCoord;

//    private static ArrayList<String> allAvailableBeaconCats = null;
//    private static String currentBeaconCat = "";

//    private Snackbar waitingforReadingsSnackbar;
//    private boolean isWaitSnackbarShowing = false;
//    private Snackbar waitingForLocationSnackbar;

    /*
    onCreate - This function is called at the program start. Some of our global variables and our UI elements are initialized here.
    R.id.<item> is used to specify that we want to edit (from our resource files)
    We also use this function to load our floorplans as bitmaps, as well as add the qr code values to an arraylist.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigator);

        // Carry out network operations on the main thread
        // TODO: Later, put on a background thread.
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Initialize UI Elements
        mainToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(mainToolbar);
        getSupportActionBar().setTitle(R.string.default_floor);
        vibrate = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        scanButton = (ImageButton)findViewById(R.id.scan_button);
        mapButtonNext = (ImageButton)findViewById(R.id.map_button_next);
        mapButtonPrev = (ImageButton)findViewById(R.id.map_button_prev);
        speechButton = (ImageButton)findViewById(R.id.speech_button);
        directionPrev = (ImageButton)findViewById(R.id.direction_button_prev);
        directionNext = (ImageButton)findViewById(R.id.direction_button_next);
        //floorTxt = (TextView)findViewById(R.id.current_floor);
        locationTxt = (TextView)findViewById(R.id.source_text);
        destTxt = (TextView)findViewById(R.id.destination_text);
        directionText = (TextView)findViewById(R.id.direction_text);
        floorMap = (ImageView)findViewById(R.id.map);
        fAttacher = new PhotoViewAttacher(floorMap);


        // Declare error and success Toasts we use to display whether or not the scan was successful.
        toastError = Toast.makeText(getApplicationContext(), "QR code was unsuccessfully scanned", Toast.LENGTH_SHORT);
        toastSuccess = Toast.makeText(getApplicationContext(), "QR code was successfully scanned!", Toast.LENGTH_SHORT);

        // Get an array of IDs that correspond to each floorplan bitmap in the drawable folder (the name of the drawable is the filename w/o the extension)
        imgArray = new int[] {R.drawable.clevel_map, R.drawable.ground_map};
        floorNames = new String[] {"C Level", "Ground"};  // TODO: Somehow correspond to Globals value.
        mapScaleFeet = new float[] {0.02674f, 0.08f};
        mapScaleMeters = new float[] {0.00815f, 0.0244f};

        if(!isNetworkAvailable()) {
            new AlertDialog.Builder(this)
                    .setTitle("No Internet Detected")
                    .setMessage("The Navigator requires an Internet connection to function " +
                            "properly. Please connect to the Internet and relaunch the Navigator.")
                    .setCancelable(false)
                    .setNeutralButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }

        // we do a lot of checks beforehand, so we'll probably never call this, but it's good to have just in case
        if(!checkLocateRequirements()) {
            new AlertDialog.Builder(this)
                    .setTitle("Not a Tango Device")
                    .setMessage("The Tango Navigator requires this device to be a Tango-enabled device. " +
                            "This is not a Tango device; therefore, this app cannot be used.")
                    .setCancelable(false)
                    .setNeutralButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .show();
            return;
        }

        // Load all of the checkpoint (QR code) data from the checkpoints.txt file in the assets folder.
        loadCheckpoints();

        // Set our custom ImageViewTouch element to display the current map element and mark the maps.
        loadNodeCoords();

        //loadFloors();
        markMaps();

        loadGraphs();

        // Set a listener to both the scan and map buttons. When a button is clicked, it will call the onClick function with the view corresponding to that button.
        scanButton.setOnClickListener(this);
        mapButtonNext.setOnClickListener(this);
        mapButtonPrev.setOnClickListener(this);
        speechButton.setOnClickListener(this);
        directionNext.setOnClickListener(this);
        directionPrev.setOnClickListener(this);
        fAttacher.setOnPhotoTapListener(new PhotoTapListener());

        tts = new TextToSpeech(TangoNavigatorActivity.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS){
                    int result = tts.setLanguage(Locale.US);
                    if(result==TextToSpeech.LANG_MISSING_DATA || result==TextToSpeech.LANG_NOT_SUPPORTED){
                        Log.e(TAG, "Error: Language is not supported");
                    }
                }
                else
                    Log.e(TAG, "TextToSpeech Initialization failed!");
            }
        });

        CharSequence selectLocations[] = Locations.toArray(new CharSequence[Locations.size()]);
        destinationsMenu = new AlertDialog.Builder(this);
        destinationsMenu.setTitle("Choose a Destination");
        destinationsMenu.setItems(selectLocations, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String location = Locations.get(which);
                destTxt.setText("Destination: " + location);
                if (isVibrationOn)
                    vibrate.vibrate(100);
                tappedCode = which;
                Toast toast = Toast.makeText(TangoNavigatorActivity.this, "You have selected the " + location, Toast.LENGTH_SHORT);
                if(lastScan < 0)
                    speakDirections(2);
                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                toast.show();
                markMaps();
            }
        });
        startPointMenu = new AlertDialog.Builder(this);
        startPointMenu.setTitle("Choose a Starting Point");
        startPointMenu.setItems(selectLocations, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String location = Locations.get(which);
                locationTxt.setText("Last scanned location: " + location);
                if(isVibrationOn)
                    vibrate.vibrate(250);
                lastScan = which;
                Toast toast = Toast.makeText(TangoNavigatorActivity.this, "Start location set to " + location, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,0);
                toast.show();
                markMaps();
            }
        });

        // Initialize the units selector
        stringListOfAllowedUnits = new String[enumListOfAllowedUnits.length];
        for (int i = 0; i < enumListOfAllowedUnits.length; i++) {
            stringListOfAllowedUnits[i] = WordUtils.capitalize(enumListOfAllowedUnits[i].name().toLowerCase());
        }

        unitsMenu = new AlertDialog.Builder(this);
        unitsMenu.setTitle("Choose desired units");
        unitsMenu.setItems(stringListOfAllowedUnits, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currUnits = enumListOfAllowedUnits[which];
            }
        });

        // Tango intent receiver
        Intent intent = getIntent();
        mIsLearningMode = intent.getBooleanExtra(AdfUuidListViewActivity.USE_AREA_LEARNING, false);
        mIsConstantSpaceRelocalize = intent.getBooleanExtra(AdfUuidListViewActivity.LOAD_ADF, false);
        selectedUUID = getIntent().getExtras().getString("uuidName");
    }

    /**
     * Called so that the activity can start interacting with the user.
     */
    @Override
    public void onResume() {
        super.onResume();
        this.registerReceiver(mLocalizationReceiver, localizationFilter);
        this.registerReceiver(mErrorModifier, errorModifierFilter);

        // Initialize Tango Service as a normal Android Service, since we call mTango.disconnect()
        // in onPause, this will unbind Tango Service, so every time when onResume gets called, we
        // should create a new Tango object.
        mTango = new Tango(TangoNavigatorActivity.this, new Runnable() {
            @Override
            public void run() {
                synchronized (TangoNavigatorActivity.this) {
                    try {
                        mConfig = setTangoConfig(
                                mTango, mIsLearningMode, mIsConstantSpaceRelocalize);
                        mTango.connect(mConfig);
                        startupTango();

                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, getString(R.string.tango_out_of_date_exception), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, getString(R.string.tango_error), e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, getString(R.string.tango_invalid), e);
                    } catch (SecurityException e) {
                        // Area Learning permissions are required. If they are not available,
                        // SecurityException is thrown.
                        Log.e(TAG, getString(R.string.no_permissions), e);
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
        this.unregisterReceiver(mLocalizationReceiver);
        this.unregisterReceiver(mErrorModifier);

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
        if(tts != null) {
            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TTS Destroyed");
        }

        super.onDestroy();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void resetAll(){
        lastScan = -1;
        curMap = 0;
        tappedCode = -1;
        tappedCodePrev = -2;
        listDirections.clear();
        distances.clear();
        targetNodes.clear();
        route.clear();
        isVibrationOn = true;
        samePoint = false;
        isMatch = false;
        currUnits = measurementUnits.IMPERIAL;
        isSpeechOn = true;
        invalidateOptionsMenu();
        //floorTxt.setText("NAC Building Floor 1");
        getSupportActionBar().setTitle(R.string.default_floor);
        locationTxt.setText(R.string.default_source);
        destTxt.setText(R.string.default_destination);
        directionText.setText(R.string.default_direction);

        currPosition.set(0,0);
        prev_x = prev_y = prev2_x = prev2_y = MapView.defaultCoord;
        // TODO: Check if there's a crash if we're not ranging.
        if(!localizationIsDisabled) {
            tryToToggleLocalization();
        }

        markMaps();
    }

    private void showNodes(int floorIdx, Canvas canvas, Paint paint){
        int prevColor = paint.getColor();
        paint.setColor(Color.rgb(255,69,0));
        paint.setTextSize(20f);
        for(int i = 0; i < nodePoints.get(floorIdx).size(); i++){
            canvas.drawCircle(nodePoints.get(floorIdx).get(i).x,nodePoints.get(floorIdx).get(i).y,5,paint);
            canvas.drawText(String.valueOf(i),nodePoints.get(floorIdx).get(i).x,nodePoints.get(floorIdx).get(i).y - 10,paint);
        }
        paint.setColor(prevColor);
    }

    /*
    loadFloors() - This function  loads all of the floorplan images (which is in res/drawable-nodpi folder) in the floors arraylist.
    It is used to both initialize the bitmap arraylist with all of the images as well as to reset the bitmaps of any modifications
    (such as marking the matched points on the map)
     */
    private void loadFloors(){
        // Create a bitmap array to hold each floorplan image.
        floors.clear();
        prevZoom = fAttacher.getDisplayMatrix();
        for(int i = 0; i < imgArray.length; i++){
            floors.add(BitmapFactory.decodeResource(getResources(),imgArray[i]).copy(Bitmap.Config.ARGB_8888, true));
            Canvas canvas = new Canvas(floors.get(i));
            Paint paint = new Paint();
            paint.setColor(Color.GREEN);
            paint.setTextSize(50);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            int width = floors.get(i).getWidth();
            int height = floors.get(i).getHeight();
/*
            canvas.drawText("Destination", width - 300, height - 35, paint);
            canvas.drawCircle(width - 325, height - 50, 20, paint);
            paint.setColor(Color.RED);
            canvas.drawText("Start", width - 300, height - 85, paint);
            canvas.drawCircle(width - 325, height - 100, 20, paint);
            paint.setColor(Color.BLUE);
            canvas.drawText("QR Code", width - 300, height - 135, paint);
           /*canvas.drawCircle(width - 325, height - 150, 20, paint);*/
            //showNodes(i,canvas,paint);
        }
        floorMap.setImageBitmap(floors.get(curMap));
        fAttacher.update();
    }

    /*
    loadCheckpointss() - This function will parse the text file for all of the QR code entries and put the data
    for each code into its corresponding arraylist.
    qrCodes - Holds the qr code string (the data the qr code contains) for every QR code registered.
    FloorIdx - Contains the floor each QR code is located in.
    xCoords - Contains the x-coordinate of the QR code's location
    yCoords - Contains the y-coordinate of the QR code's location
    Locations - Contains the name of key location nearest to the QR code (i.e. NAC Ballroom)
     */
    private void loadCheckpoints(){
        try {
            URL url = new URL(TEXT_RETRIEVAL_BASE_URL + "checkpoints.txt");
            // The text file is loaded into a BufferedReader, which is used to process each line
            // of the text file and extract the data as a series of strings (one per line)
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            String str;
            // While there is still a valid unread string in the file, we will continue to parse it
            while((str = br.readLine()) != null){
                // First we split the string (which contains all the data separated by whitespaces)
                // into a string array where each element is one of data elements.
                String[] temp_strs = str.split(" ");
                // Then we add each element into its corresponding arraylist
                // qrCodes and Locations are strings, so we can just add them
                // However, the other lists are integer lists, so we use Integer.parseInt() to
                // convert the strings into an integer.
                qrCodes.add(temp_strs[0]);
                FloorIdx.add(Integer.parseInt(temp_strs[1]));
                xCoords.add(Integer.parseInt(temp_strs[2]));
                yCoords.add(Integer.parseInt(temp_strs[3]));
                Locations.add(temp_strs[4].replaceAll("_"," "));
                NodeIdx.add(Integer.parseInt(temp_strs[5]));
            }
            br.close();
            Log.i(TAG, "Finished parsing checkpoints.txt");
        }
        // This catch statement is executed in the case where checkpoints.txt cannot be opened
        catch(IOException e){
            e.printStackTrace();
            Log.e(TAG, "Error opening checkpoints.txt");
        }
    }

    private void loadNodeCoords(){
        for(int floor = 0; floor < imgArray.length; floor++) {
            try {
                URL url = new URL(TEXT_RETRIEVAL_BASE_URL + floorNames[floor].replaceAll("\\s+","").toLowerCase() + "_Nodes.txt");

                BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                String str;
                ArrayList<Point> temp_arraylist = new ArrayList<>();
                while ((str = br.readLine()) != null) {
                    String[] temp_strs = str.split(" ");
                    temp_arraylist.add(new Point(Integer.parseInt(temp_strs[1]), Integer.parseInt(temp_strs[2])));
                }
                nodePoints.add(temp_arraylist);
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error loading node list for floor " + floor);
            }
        }
        Log.i(TAG, "Finished loading node list for all floors!");
    }

    private void loadGraphs(){
        for(int floor = 0; floor < imgArray.length; floor++) {
            try {
                URL url = new URL(TEXT_RETRIEVAL_BASE_URL + floorNames[floor].replaceAll("\\s+","").toLowerCase() + "_Graph.txt");

                BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
                String str;
                int count = 0;
                ArrayList<Vertex> nodes = new ArrayList<>();
                ArrayList<Edge> edges = new ArrayList<>();
                for (int i = 0; i < nodePoints.get(floor).size(); i++) {
                    Vertex location = new Vertex(Integer.toString(i), "Node_" + i);
                    nodes.add(location);
                }
                while ((str = br.readLine()) != null) {
                    String[] temp_strs = str.split(" ");
                    //edges.add(new Edge(Integer.parseInt(temp_strs[0]), Integer.parseInt(temp_strs[1]), Integer.parseInt(temp_strs[2])));
                    edges.add(new Edge("Edge_" + count, nodes.get(Integer.parseInt(temp_strs[0])), nodes.get(Integer.parseInt(temp_strs[1])), Integer.parseInt(temp_strs[2])));
                    edges.add(new Edge("Edge_" + count, nodes.get(Integer.parseInt(temp_strs[1])), nodes.get(Integer.parseInt(temp_strs[0])), Integer.parseInt(temp_strs[2])));
                    count++;
                }
                Graph g = new Graph(nodes, edges);
                DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(g);
                floorGraph.add(dijkstra);
                nodeArray.add(nodes);
                /*
                dijkstra.execute(nodes.get(4));
                ArrayList<Vertex> pathto = dijkstra.getPath(nodes.get(17));
                Log.i(TAG, "Path is: " + pathto.toString());
                //dijkstra.execute(nodes.get(5));
                //DijkstraAlgorithm d2 = new DijkstraAlgorithm(g);
                dijkstra.execute(nodes.get(6));
                ArrayList<Vertex> path2 = dijkstra.getPath(nodes.get(19));
                Log.i(TAG, "Path is: " + path2.toString());*/
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error loading graph for floor" + floor);
            }
        }
        Log.i(TAG, "Finished loading all floor graphs!");
    }

    /*
    markMaps() - This function will go through the bitmap floorplans and draw circle at specified locations
    These locations are determined beforehand and will be stored in a text file to make the code neater
    Initially, all the QR code locations are marked using the color blue. However, after a match is made, a red
    color is used to denote the point that was matched.
     */
    private void markMaps() {
        markMaps(false);    // the default call allows speaking of the direction
    }

    private void markMaps(boolean noSpeak){

        // Reset the map view (default zoom/pan) and reload the floor bitmaps.
        //floorMap.resetMatrix();
        loadFloors();

        // Iterate through all of the registered QR codes and mark them on the map.
        // qrCodes, FloorIdx, xCoords, and yCoords are arraylists loaded from checkpoints.txt
        for(int i = 0; i < qrCodes.size(); i++) {
            int curFloor = FloorIdx.get(i) - 1;
            Canvas canvas = new Canvas(floors.get(curFloor));
            Paint paint = new Paint();
            paint.setColor(Color.BLUE);
            canvas.drawCircle(xCoords.get(i), yCoords.get(i), 20, paint);

            // Draw circle for curr pos.
            if(currPosition.x != 0 || currPosition.y != 0) {
                paint.setColor(Color.MAGENTA);
                canvas.drawCircle(currPosition.x, currPosition.y, 30, paint);
            }

            // If a scan has been performed, lastScan will have the index of the matching QR code
            // Now we will mark the matched QR code on the map
            if(lastScan == i) {
                paint.setColor(Color.RED);
                canvas.drawCircle(xCoords.get(i), yCoords.get(i), 30, paint);
                // Now we set curMap (int used to keep track of the current floor) to be the floor
                // where the matched QR code is located. The displayed map and text is updated accordingly.
                curMap = curFloor;
                floorMap.setImageBitmap(floors.get(curMap));
                fAttacher.update();
                //floorTxt.setText("NAC Building Floor " + (curMap + 1));
                getSupportActionBar().setTitle(floorNames[curMap]);
                locationTxt.setText("Last scanned location: " + Locations.get(lastScan));
            }
            if(tappedCode == i && tappedCode != lastScan && tappedCode != tappedCodePrev){
                paint.setColor(Color.GREEN);
                canvas.drawCircle(xCoords.get(i), yCoords.get(i), 20, paint);
                fAttacher.setDisplayMatrix(prevZoom);
                fAttacher.update();
            }
            if(tappedCode == tappedCodePrev) {
                tappedCodePrev = -2;
                samePoint = true;
            }
            if (tappedCode >= 0 && lastScan >= 0 && FloorIdx.get(tappedCode) == FloorIdx.get(lastScan) && i == tappedCode && tappedCode != lastScan) {
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(10);

                int source = NodeIdx.get(lastScan);
                int destination = NodeIdx.get(tappedCode);
                int floor = FloorIdx.get(lastScan) - 1;
                floorGraph.get(floor).execute(nodeArray.get(floor).get(source));
                //ArrayList<Integer> path = floorGraph.get(0).getPathIndices(nodeArray.get(0).get(destination));
                ArrayList<Integer> path = floorGraph.get(floor).getPathIndices(nodeArray.get(floor).get(destination));
                if(path == null) {
                    Log.e(TAG, "Error: no path found");
                    return;
                }
                Log.i(TAG, "Path Found is: " + path.toString());
                listDirections.clear();
                targetNodes.clear();
                if(!curPath) {
                    if (localizationIsDisabled) {
                        if (!tryToToggleLocalization()) return;
                        // if we failed to turn localization on due to a permission problem,
                        // don't continue
                    }
                    curDirection = 0;
                }
                drawPath(floor, path,canvas,paint);
                //markCurrentPath();
                directionText.setText(listDirections.get(curDirection));
                if(!noSpeak) {
                    speakDirections(1);
                }
                isMatch = false;
            }
        }
    }

    private void drawPath(int floor, ArrayList<Integer> path, Canvas canvas, Paint paint){
        route.clear();
        distances.clear();
        String unit, cur_dir, next_dir;
        cur_dir = null;
        Point lastNode = new Point();

        unit = "";
        switch (currUnits) {
            case IMPERIAL:
                unit = "feet";
                break;
            case METRIC:
                unit = "meters";
                break;
            case STEPS:
                unit = "steps";
                break;
        }

        int start_x = nodePoints.get(floor).get(path.get(0)).x;
        int start_y = nodePoints.get(floor).get(path.get(0)).y;
        int end_x,end_y;

        for(int index = 1; index < path.size() - 1; index++){
            int prevIdx = path.get(index - 1);
            int curIdx = path.get(index);
            int nextIdx = path.get(index + 1);

            int prev_x = nodePoints.get(floor).get(prevIdx).x;
            int prev_y = nodePoints.get(floor).get(prevIdx).y;
            int cur_x = nodePoints.get(floor).get(curIdx).x;
            int cur_y = nodePoints.get(floor).get(curIdx).y;
            int next_x = nodePoints.get(floor).get(nextIdx).x;
            int next_y = nodePoints.get(floor).get(nextIdx).y;

            int slope_1 = getSlope(prev_x, prev_y, cur_x, cur_y);
            int slope_2 = getSlope(cur_x, cur_y, next_x, next_y);

            // if(slope_1 != slope_2){
                end_x = cur_x;
                end_y = cur_y;
                routePoints.add(new PointF((float)start_x, (float)start_y));
                Path temppath = new Path();
                temppath.moveTo((float) start_x, (float) start_y);
                temppath.lineTo((float) end_x, (float) end_y);
                route.add(temppath);
                //canvas.drawLine((,(float)end_x,(float)end_y,paint);
                //fillArrow(canvas,(float)start_x,(float)start_y,(float)end_x,(float)end_y);

                // First node is done differently from the others since we are turning in the opposite direction
                // Get angle between firstNode -> nextNode and first->node QRCode
                if(listDirections.size() == 0) {
                    Point QRCodeCoord = new Point(xCoords.get(lastScan),yCoords.get(lastScan));
                    Point firstNode = new Point(start_x, start_y);
                    Point nextNode = new Point(end_x, end_y);
                    // The direction given by the old instruction here doesn't seem to be useful right now...
                    // listDirections.add(getAngle(nextNode, firstNode, QRCodeCoord) + " and walk " + getDistance(start_x, start_y, end_x, end_y) + " " + unit);
                    listDirections.add("Start by walking " + getDistance(start_x, start_y, end_x, end_y) + " " + unit);
                    lastNode = firstNode;   // Save the current vertex (firstNode) into lastNode to be used on next iteration
                } else {  // Get angle between v->currentNode and v->next node and finds the turn
                    Point currentNode = new Point(start_x, start_y);
                    Point nextNode = new Point(end_x, end_y);

                    String turning_instruction = getAngle(lastNode, currentNode, nextNode);
                    if (turning_instruction.equals("Go straight ahead")) {
                        listDirections.add(turning_instruction + " and walk for another " + getDistance(start_x, start_y, end_x, end_y) + " " + unit);
                    } else {
                        listDirections.add(turning_instruction + " and walk " + getDistance(start_x, start_y, end_x, end_y) + " " + unit);
                    }

                    lastNode = currentNode;   // Save the current vertex (currentNode) into lastNode to be used on next iteration
                }
                targetNodes.add(new Point(end_x, end_y));
                distances.add(getDistance(start_x, start_y, end_x, end_y));
                start_x = end_x;
                start_y = end_y;
            // }
        }

        end_x = nodePoints.get(floor).get(path.get(path.size() - 1)).x;
        end_y = nodePoints.get(floor).get(path.get(path.size() - 1)).y;
        //canvas.drawLine((float)start_x,(float)start_y,(float)end_x,(float)end_y,paint);
        Path temppath = new Path();
        temppath.moveTo((float)start_x,(float)start_y);
        temppath.lineTo((float) end_x, (float) end_y);
        route.add(temppath);
        routePoints.add(new PointF((float) start_x, (float) start_y));
        routePoints.add(new PointF((float) end_x, (float) end_y));
        for(int i = 0; i < route.size(); i++) {
            if((curPath && i == curDirection) || (curDirection == i && i == 0)){
                paint.setColor(Color.CYAN);
                canvas.drawPath(route.get(i),paint);
                paint.setColor(Color.RED);
                continue;
            }
            canvas.drawPath(route.get(i), paint);
        }
        fillArrow(canvas, (float) start_x, (float) start_y, (float) end_x, (float) end_y);
        //listDirections.add("Please walk " + getDistance(start_x, start_y, end_x, end_y) + " " + unit + " " + getDirection(start_x, start_y, end_x, end_y));

        //next_dir = getDirection(start_x, start_y, end_x, end_y);
        Point currentNode = new Point(start_x, start_y);
        Point nextNode = new Point(end_x, end_y);

        String turning_instruction = getAngle(lastNode, currentNode, nextNode);
        if (turning_instruction.equals("Go straight ahead")) {
            listDirections.add(turning_instruction + " and walk for another " + getDistance(start_x, start_y, end_x, end_y) + " " + unit);
        } else {
            listDirections.add(turning_instruction + " and walk " + getDistance(start_x, start_y, end_x, end_y) + " " + unit);
        }

        targetNodes.add(new Point(end_x, end_y));
        distances.add(getDistance(start_x, start_y, end_x, end_y));
        listDirections.add("Arrived at the " + Locations.get(tappedCode));
    }

    private String getAngle(Point p1, Point v, Point p2){
        //Gets the angle between the vertex v -> p1 and v -> p2
        //Then uses the angle to find what direction to turn
        double angle = Math.atan2(p1.y - v.y, p1.x - v.x) - Math.atan2(p2.y - v.y, p2.x - v.x);
        angle = angle * 360 / (2 * Math.PI);    //convert from radians to degrees
        if(angle < 0)   // Puts angle in range from 0<->360 instead of -360<->360
            angle = angle + 360;

        // Log.d(TAG, "Angle: " + Double.toString(angle));
        if(angle == 0)  // Shouldn't happen
            return "Make a U-turn";
        else if (angle < 50)
            return "Turn sharply right";
        else if(angle >= 50 && angle < 130)
            return "Turn right";
        else if(angle >= 130 && angle < 179)
            return "Turn slightly right";
        else if(angle >= 179 && angle <= 181)
            return "Go straight ahead";
        else if (angle > 310)
            return "Turn sharply left";
        else if (angle <= 310 && angle > 230)
            return "Turn left";
        else /* if (angle <= 230 && angle > 181) */
            return "Turn slightly left";
    }

    private int getDistance(int x1, int y1, int x2, int y2){
        double distanceInPixels = Math.sqrt(Math.pow((double)(x2 - x1),2) + Math.pow((double)(y2 - y1),2));

        switch (currUnits) {
            case METRIC:
                return (int)(distanceInPixels * mapScaleMeters[curMap]);
            case IMPERIAL:
                return (int)(distanceInPixels * mapScaleFeet[curMap]);
            case STEPS:
                return (int)Math.round((distanceInPixels * mapScaleFeet[curMap])/feetPerStep);
            default:
                return 0; // shouldn't happen
        }
    }

    private int getSlope(int x1, int y1, int x2, int y2){
        if((x2 - x1) != 0)
            return  (y2 - y1) / (x2 - x1);
        else
            return Integer.MAX_VALUE;
    }

    private void fillArrow(Canvas canvas, float x0, float y0, float x1, float y1) {
        Paint paint = new Paint();
        if(curDirection == listDirections.size() + 1)
            paint.setColor(Color.CYAN);
        else
            paint.setColor(Color.RED);

        paint.setStyle(Paint.Style.FILL);

        float deltaX = x1 - x0;
        float deltaY = y1 - y0;
        double distance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
        float frac = (float) (1 / (distance / 30));

        float point_x_1 = x0 + (float) ((1 - frac) * deltaX + frac * deltaY);
        float point_y_1 = y0 + (float) ((1 - frac) * deltaY - frac * deltaX);

        float point_x_2 = x1;
        float point_y_2 = y1;

        float point_x_3 = x0 + (float) ((1 - frac) * deltaX - frac * deltaY);
        float point_y_3 = y0 + (float) ((1 - frac) * deltaY + frac * deltaX);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);

        path.moveTo(point_x_1, point_y_1);
        path.lineTo(point_x_2, point_y_2);
        path.lineTo(point_x_3, point_y_3);
        path.lineTo(point_x_1, point_y_1);
        path.lineTo(point_x_1, point_y_1);
        path.close();

        canvas.drawPath(path, paint);
    }

/*    private void markCurrentPath(){
        if(route.size() > 0 && curDirection < route.size()){
            paint.setColor(Color.RED);
            Paint current_path = new Paint();
            current_path.setColor(Color.CYAN);
            current_path.setStyle(Paint.Style.STROKE);
            current_path.setStrokeWidth(20);
            Path curPath = route.get(curDirection);
            Log.i(TAG, "Curdirection: " + curDirection);
            ///canvas.drawPath(curPath,current_path);
            for(int i = 0; i < route.size(); i++){
                if(i == curDirection){
                    paint.setColor(Color.CYAN);
                    canvas.drawPath(route.get(i),paint);
                    continue;
                }
                paint.setColor(Color.RED);
                canvas.drawPath(route.get(i), paint);
            }
            canvas.drawLine(routePoints.get(curDirection).x,routePoints.get(curDirection).y,routePoints.get(curDirection + 1).x,routePoints.get(curDirection + 1).y,current_path);
        }
    }*/

    /*
    onClick(View v) - This function is called whenever one of the buttons are pressed.
    v.getId() gives us the id of what was clicked. We can use this to set up a case
    for each button press and perform the corresponding action.
     */
    public void onClick(View v){
        // If the "scan qr code" button is clicked, start an intent to grab the QR code
        if(v.getId() == R.id.scan_button){
            // The scanner will only look for QR codes and will automatically finish executing
            // once a valid QR code is scanned or the user presses the back button.
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
            integrator.setPrompt("Scan a QR Code");
            integrator.initiateScan();
        }
        if (v.getId() == R.id.map_button_next){
            //The next floor button is pressed
            if(curMap == floors.size() - 1)
                curMap = -1;
            floorMap.setImageBitmap(floors.get(++curMap));
            fAttacher.update();
        }
        if(v.getId() == R.id.map_button_prev){
            //the prev floor button is pressed
            if(curMap == 0)
                curMap = floors.size();
            floorMap.setImageBitmap(floors.get(--curMap));
            fAttacher.update();
        }
        if(v.getId() == R.id.speech_button){
            promptSpeechInput();
        }
        if(v.getId() == R.id.direction_button_next) {
            advanceInstruction();
        }
        if(v.getId() == R.id.direction_button_prev){
            if(curDirection == 0)
                return;
            directionText.setText(listDirections.get(--curDirection));
            //markCurrentPath();
            curPath = true;
            markMaps();
            curPath = false;
            speakDirections(1);
        }
        if(v.getId() == R.id.direction_text){
            speakDirections(1);
        }
        //floorTxt.setText("NAC Building Floor " + (curMap + 1));
        getSupportActionBar().setTitle(floorNames[curMap]);
    }

    private void advanceInstruction() {
        advanceInstruction(true);
    }

    private void advanceInstruction(boolean speak) {
        if(curDirection == listDirections.size() - 1 || listDirections.size() == 0) return;

        directionText.setText(listDirections.get(++curDirection));
        if(curDirection == listDirections.size() - 1 && !localizationIsDisabled) {
            tryToToggleLocalization();  // turn it off since we're at our last position/instruction
        }
        //markCurrentPath();
        curPath = true;
        markMaps(!speak);
        curPath = false;
        if (speak) speakDirections(1);
    }

    private void speakDirections(int select){
        if(!isSpeechOn) return;

        if(Build.VERSION.SDK_INT >= 21) {
            if (listDirections.size() > 0 && select == 1) {
                tts.speak(listDirections.get(curDirection), TextToSpeech.QUEUE_FLUSH, null, "ble_navigator_direction_" + Long.toString(System.currentTimeMillis()));
            } else if(select == 2) {
                tts.speak("You have selected the " + Locations.get(tappedCode), TextToSpeech.QUEUE_FLUSH, null, "ble_navigator_direction_" + Long.toString(System.currentTimeMillis()));
            }
        } else {
            if (listDirections.size() > 0 && select == 1) {
                // noinspection deprecation
                tts.speak(listDirections.get(curDirection), TextToSpeech.QUEUE_FLUSH, null);
            } else if (select == 2) {
                // noinspection deprecation
                tts.speak("You have selected the " + Locations.get(tappedCode), TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /*
    onActivityResult(int requestCode, int resultCode, Intent intent) - this function is called after the qr code
    scanner intent finishes executing. We can grab the result of the scan and match it to the qr codes to find a match.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent intent){
        //retrieve the scanning result
        switch(requestCode) {
            case ZXING_REQUEST_CODE:
                IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

                if (result != null) {
                    //valid result obtained
                    String scannedContent = result.getContents();
                    if (scannedContent != null) {
                        Log.i(TAG, "QR Code Contents: " + scannedContent);
                        Boolean foundMatch = false;
                        int index = 0;
                        // Check the qrCodes list to see if the scanned code is a match. If it is, record the index of the
                        // matched code and set the bool foundMatch to true.
                        for (int i = 0; i < qrCodes.size(); i++) {
                            if (qrCodes.get(i).equals(scannedContent)) {
                                foundMatch = true;
                                index = i;
                            }
                        }
                        // Match is found, vibrate the device and mark the map with the scanned qr code.
                        if (foundMatch) {
                            if(isVibrationOn)
                                vibrate.vibrate(500);
                            Log.i(TAG, "Found a match!");
                            lastScan = index;
                            isMatch = true;
                            markMaps();
                        }
                        // No match is found, set the text to show the user the result.
                        else {
                            Log.i(TAG, "Scanned code did not match");
                            lastScan = -1;
                            markMaps();
                            //floorTxt.setText("Could not find a match");
                            locationTxt.setText("Please scan another QR code");
                        }
                        toastSuccess.show();
                    } else {
                        toastError.show();
                    }
                } else {
                    //No result or invalid result obtained (i.e. user presses the back button instead of scanning something)
                    toastError.show();
                }
                break;
            case SPEECH_REQUEST_CODE:
                if(resultCode == RESULT_OK && intent != null){
                    ArrayList<String> speechResult = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    Log.i(TAG,speechResult.get(0));

                    for(int i = 0; i < Locations.size(); i++){
                        String location = Locations.get(i);
                        String result_location = speechResult.get(0);
                        if(location.equalsIgnoreCase(result_location)) {
                            destTxt.setText("Destination: " + location);
                            if(isVibrationOn)
                                vibrate.vibrate(100);
                            tappedCode = i;
                            Toast toast = Toast.makeText(TangoNavigatorActivity.this, "You have selected the " + location, Toast.LENGTH_SHORT);
                            speakDirections(2);
                            toast.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,0);
                            toast.show();
                            markMaps();
                            return;
                        }
                    }
                    Toast.makeText(TangoNavigatorActivity.this, "No location found.", Toast.LENGTH_SHORT).show();
                    break;
                }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.tango_navigator_options_menu, menu);
        menu.findItem(R.id.action_vibration).setChecked(true);
        menu.findItem(R.id.action_units).setChecked(false);
        menu.findItem(R.id.action_speak).setChecked(true);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch(item.getItemId()) {
            //noinspection SimplifiableIfStatement
            case R.id.start_localizing:
                tryToToggleLocalization();
                return true;
            case R.id.action_reset:
                resetAll();
                return true;
            case R.id.action_locations:
                destinationsMenu.show();
                return true;
            case R.id.action_source:
                startPointMenu.show();
                return true;
            case R.id.action_vibration:
                if(item.isChecked()) {
                    item.setChecked(false);
                    isVibrationOn = false;
                }
                else{
                    item.setChecked(true);
                    isVibrationOn = true;
                }
                return true;
            case R.id.action_units:
                unitsMenu.show();
                return true;
            case R.id.action_modifyerror:
                AllowedErrorModifierDialog aemDialog = new AllowedErrorModifierDialog(this, feetPerStep, feetPerMeter, allowedErrorInFeet);
                aemDialog.show();
                return true;
            case R.id.action_speak:
                if(item.isChecked()) {
                    item.setChecked(false);
                    isSpeechOn = false;
                }
                else{
                    item.setChecked(true);
                    isSpeechOn = true;
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("DefaultLocale")
    private boolean tryToToggleLocalization() {
        if(localizationIsDisabled) {
            localizationIsDisabled = false;
            MenuItem localizingMI = menu.findItem(R.id.start_localizing);
            localizingMI.setTitle("Stop Localization");
        } else {
            localizationIsDisabled = true;
            prev_x = prev_y = prev2_x = prev2_y = MapView.defaultCoord;
            MenuItem localizingMI = menu.findItem(R.id.start_localizing);
            localizingMI.setTitle("Start Locating");
        }
        return true;
    }

    private class PhotoTapListener implements PhotoViewAttacher.OnPhotoTapListener {

        @Override
        public void onPhotoTap(View view, float x, float y) {
            x_tap = (int)(x * floors.get(curMap).getWidth());
            y_tap = (int)(y * floors.get(curMap).getHeight());

            for (int i = 0; i < qrCodes.size(); i++) {
                int x_pos = xCoords.get(i);
                int y_pos = yCoords.get(i);
                if (Math.abs(x_tap - x_pos) <= 50 && Math.abs(y_tap - y_pos) <= 50 && curMap == (FloorIdx.get(i) - 1)) {
                    tappedCode = i;
                    if(isVibrationOn)
                        vibrate.vibrate(100);
                    destTxt.setText("Destination: " + Locations.get(tappedCode));
                    Toast toast = Toast.makeText(TangoNavigatorActivity.this, "You have selected the " + Locations.get(tappedCode), Toast.LENGTH_SHORT);
                    if(lastScan < 0)
                        speakDirections(2);
                    toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                    toast.show();
                    markMaps();
                    if(!samePoint) {
                        tappedCodePrev = i;
                        samePoint = false;
                    }
                    break;
                }
            }
            //Toast showPos = Toast.makeText(NavigatorActivity.this, "x: " + x_tap + "  y: " + y_tap, Toast.LENGTH_SHORT);
            //showPos.show();
        }

        @Override
        public void onOutsidePhotoTap() {
            // showToast("You have a tap event on the place where out of the photo.");
        }
    }

    // DO NOT MODIFY BELOW THIS
    public class Vertex {
        final private String id;
        final private String name;


        Vertex(String id, String name) {
            this.id = id;
            this.name = name;
        }
        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Vertex other = (Vertex) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return name;
        }

    }

    private class Edge  {
        private final String id;
        private final Vertex source;
        private final Vertex destination;
        private final int weight;

        Edge(String id, Vertex source, Vertex destination, int weight) {
            this.id = id;
            this.source = source;
            this.destination = destination;
            this.weight = weight;
        }

        public String getId() {
            return id;
        }
        Vertex getDestination() {
            return destination;
        }

        public Vertex getSource() {
            return source;
        }
        int getWeight() {
            return weight;
        }

        @Override
        public String toString() {
            return source + " " + destination;
        }


    }

    public class Graph {
        private final List<Vertex> vertexes;
        private final List<Edge> edges;

        Graph(List<Vertex> vertexes, List<Edge> edges) {
            this.vertexes = vertexes;
            this.edges = edges;
        }

        List<Vertex> getVertexes() {
            return vertexes;
        }

        List<Edge> getEdges() {
            return edges;
        }


    }

    public class DijkstraAlgorithm {

        private final List<Vertex> nodes;
        private final List<Edge> edges;
        private Set<Vertex> settledNodes;
        private Set<Vertex> unSettledNodes;
        private Map<Vertex, Vertex> predecessors;
        private Map<Vertex, Integer> distance;

        DijkstraAlgorithm(Graph graph) {
            // create a copy of the array so that we can operate on this array
            this.nodes = new ArrayList<Vertex>(graph.getVertexes());
            this.edges = new ArrayList<Edge>(graph.getEdges());
        }

        void execute(Vertex source) {
            settledNodes = new HashSet<Vertex>();
            unSettledNodes = new HashSet<Vertex>();
            distance = new HashMap<Vertex, Integer>();
            predecessors = new HashMap<Vertex, Vertex>();
            distance.put(source, 0);
            unSettledNodes.add(source);
            while (unSettledNodes.size() > 0) {
                Vertex node = getMinimum(unSettledNodes);
                settledNodes.add(node);
                unSettledNodes.remove(node);
                findMinimalDistances(node);
            }
        }

        private void findMinimalDistances(Vertex node) {
            List<Vertex> adjacentNodes = getNeighbors(node);
            for (Vertex target : adjacentNodes) {
                if (getShortestDistance(target) > getShortestDistance(node)
                        + getDistance(node, target)) {
                    distance.put(target, getShortestDistance(node)
                            + getDistance(node, target));
                    predecessors.put(target, node);
                    unSettledNodes.add(target);
                }
            }

        }

        private int getDistance(Vertex node, Vertex target) {
            for (Edge edge : edges) {
                if (edge.getSource().equals(node)
                        && edge.getDestination().equals(target)) {
                    return edge.getWeight();
                }
            }
            throw new RuntimeException("Should not happen");
        }

        private List<Vertex> getNeighbors(Vertex node) {
            List<Vertex> neighbors = new ArrayList<Vertex>();
            for (Edge edge : edges) {
                if (edge.getSource().equals(node)
                        && !isSettled(edge.getDestination())) {
                    neighbors.add(edge.getDestination());
                }
            }
            return neighbors;
        }

        private Vertex getMinimum(Set<Vertex> vertexes) {
            Vertex minimum = null;
            for (Vertex vertex : vertexes) {
                if (minimum == null) {
                    minimum = vertex;
                } else {
                    if (getShortestDistance(vertex) < getShortestDistance(minimum)) {
                        minimum = vertex;
                    }
                }
            }
            return minimum;
        }

        private boolean isSettled(Vertex vertex) {
            return settledNodes.contains(vertex);
        }

        private int getShortestDistance(Vertex destination) {
            Integer d = distance.get(destination);
            if (d == null) {
                return Integer.MAX_VALUE;
            } else {
                return d;
            }
        }

        /*
         * This method returns the path from the source to the selected target and
         * NULL if no path exists
         */
        public ArrayList<Vertex> getPath(Vertex target) {
            //LinkedList<Vertex> path = new LinkedList<Vertex>();
            ArrayList<Vertex> path = new ArrayList<>();
            Vertex step = target;
            // check if a path exists
            if (predecessors.get(step) == null) {
                return null;
            }
            path.add(step);
            while (predecessors.get(step) != null) {
                step = predecessors.get(step);
                path.add(step);
            }
            // Put it into the correct order
            Collections.reverse(path);
            return path;
        }

        ArrayList<Integer> getPathIndices(Vertex target) {
            //LinkedList<Vertex> path = new LinkedList<Vertex>();
            ArrayList<Integer> path = new ArrayList<>();
            Vertex step = target;
            // check if a path exists
            if (predecessors.get(step) == null) {
                return null;
            }
            path.add(Integer.parseInt(step.getId()));
            while (predecessors.get(step) != null) {
                step = predecessors.get(step);
                path.add(Integer.parseInt(step.getId()));
            }
            // Put it into the correct order
            Collections.reverse(path);
            return path;
        }

    }
    // DO NOT MODIFY ABOVE THIS

    /*** Beacon-related variables ***/
    public static final String LOCATOR_BROADCAST_ACTION = "ble.localization.navigator.LOCATE";
    public static final String ERROR_CHANGE_BROADCAST_ACTION = "ble.localization.navigator.CHANGE_ERROR";

    private BroadcastReceiver mLocalizationReceiver = new localizationReceiver();
    private IntentFilter localizationFilter = new IntentFilter(LOCATOR_BROADCAST_ACTION);

    private BroadcastReceiver mErrorModifier = new ChangeErrorReceiver();
    private IntentFilter errorModifierFilter = new IntentFilter(ERROR_CHANGE_BROADCAST_ACTION);

    // Check if we've met the requirement to localize, and start the ranging service.
    private boolean checkLocateRequirements() {
        return Globals.isTangoDevice(getApplicationContext());
    }

    /**
     * Updates the variables that deal with position.
     * @param x The new, current x.
     * @param y The new, current y.
     */
    private void updatePositionHolders(float x, float y) {
//        prev2_x = prev_x;
//        prev2_y = prev_y;

        prev2_x = currPosition_floatX;
        prev2_y = currPosition_floatY;

        currPosition.set((int)x, (int)y);   // Point only accepts ints apparently.
        currPosition_floatX = prev_x = x;
        currPosition_floatY = prev_y = y;
    }

    /**
     * Broadcast receiver - Facilitates the localization process.
     */
    private class localizationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent received.");
            // TODO: Integrate multiple floors.
            final Bundle intentPayload = intent.getExtras();
            final float x = intentPayload.getFloat("x");
            final float y = intentPayload.getFloat("y");
            // Update map dot and invalidate map, so it's redrawn.
            // Probably what we'll do is: store coordinates into a variable, and then call markMaps, which'll draw.
            updatePositionHolders(x, y);
            // Check if we're at or near our next coordinates or even a future node. If we are, change the current direction to that node.
            int closestTarget = curDirection - 1;
            for(int i = curDirection; i < targetNodes.size(); i++) {
                Point targetNode = targetNodes.get(i);
                int dist = getDistance(targetNode.x, targetNode.y, currPosition.x, currPosition.y);
                if( dist <= currentAllowedError() ) {
                    closestTarget = i;
                    break;
                }
            }
            if(closestTarget >= curDirection) {
                for(int i = curDirection; i < closestTarget; i++) {
                    advanceInstruction(false);
                }
                advanceInstruction();
            } else {
                curPath = true;
                markMaps(true); // just update the location dot without speaking the direction again
                curPath = false;
            }

        }
    }

    private double currentAllowedError() {
        switch (currUnits) {
            case IMPERIAL:
                return allowedErrorInFeet;
            case METRIC:
                return (allowedErrorInFeet / feetPerMeter);
            case STEPS:
                return (allowedErrorInFeet / feetPerStep);
            default:    // shouldn't happen
                return 0;
        }
    }

    /**
     * Broadcast receiver - Changes the allowed error.
     */
    private class ChangeErrorReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Bundle intentPayload = intent.getExtras();
            allowedErrorInFeet = intentPayload.getDouble("desired_error");
            // Toast.makeText(getApplicationContext(), "Allowed error changed to " + Integer.toString(allowedErrorInFeet) + "!", Toast.LENGTH_SHORT).show();
        }
    }

    // Tango stuff
    private static final int SECS_TO_MILLISECS = 1000;
    private Tango mTango;
    private TangoConfig mConfig;

    private float[] translation;
    private float[] orientation;

    private double mPreviousPoseTimeStamp;
    private static final double UPDATE_INTERVAL_MS = 1000.0;
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

    float currentX;
    float currentY;

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
                            // Globals.showSnackbar(findViewById(android.R.id.content), "Localized.");

                            translation = pose.getTranslationAsFloats();
                            orientation = pose.getRotationAsFloats();

                            // CURRENT LOCATION

                            currentX = translation[0];
                            currentY = translation[1];

                            mPositionString = "X:" + translation[0] + ", Y:" + translation[1] + ", Z:" + translation[2];
                            mZRotationString = String.valueOf(MathFunctions.getEulerAngleZ(orientation));

                            final double deltaTime = (pose.timestamp - mPreviousPoseTimeStamp) *
                                    SECS_TO_MILLISECS;
                            mPreviousPoseTimeStamp = pose.timestamp;
                            mTimeToNextUpdate -= deltaTime;
                        } else {
                            mIsRelocalized = false;
                            Globals.showSnackbar(findViewById(android.R.id.content), "Invalid pose data received.");
                        }
                    }
                }

                // TODO: Improve this!
                if (mTimeToNextUpdate < 0.0 && !localizationIsDisabled) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (mSharedLock) {
                                float[] results = align_coordinates(currentX, currentY);

                                // Send the intent to complete the localization process.
                                final Intent updateMapView = new Intent(LOCATOR_BROADCAST_ACTION);
                                updateMapView.putExtra("x", results[0]);
                                updateMapView.putExtra("y", results[1]);
                                getApplicationContext().sendBroadcast(updateMapView);
                            }
                        }
                    });
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

    private float[] align_coordinates(float currentX, float currentY){
        org.opencv.core.Point tango_coord1 = new org.opencv.core.Point(); // beacon 24540
        tango_coord1.x = 0.55;
        tango_coord1.y = -8.52;
        org.opencv.core.Point tango_coord2 = new org.opencv.core.Point(); // beacon 6707
        tango_coord2.x = -8.67;
        tango_coord2.y = 5.57;
        org.opencv.core.Point tango_coord3 = new org.opencv.core.Point(); // beacon 31905
        tango_coord3.x = 10.87;
        tango_coord3.y = 3.27;
        List<org.opencv.core.Point> tangoList = new ArrayList<org.opencv.core.Point>();
        tangoList.add(tango_coord1);
        tangoList.add(tango_coord2);
        tangoList.add(tango_coord3);

        org.opencv.core.Point map_coord1 = new org.opencv.core.Point(); // beacon 24540
        map_coord1.x = 228.04;
        map_coord1.y = 840.05;
        org.opencv.core.Point map_coord2 = new org.opencv.core.Point(); // beacon 6707
        map_coord2.x = 2188.31;
        map_coord2.y = 380.04;
        org.opencv.core.Point map_coord3 = new org.opencv.core.Point(); // beacon 31905
        map_coord3.x = 1298.49;
        map_coord3.y = 2640.91;
        List <org.opencv.core.Point> mapList = new ArrayList<org.opencv.core.Point>();
        mapList.add(map_coord1);
        mapList.add(map_coord2);
        mapList.add(map_coord3);

        MatOfPoint2f tangoPoints = new MatOfPoint2f();
        tangoPoints.fromList(tangoList);
        MatOfPoint2f mapPoints = new MatOfPoint2f();
        mapPoints.fromList(mapList);

        // get transformation matrix
        Mat warp_mat = new Mat( 2, 3, CV_64F);
        warp_mat = getAffineTransform(tangoPoints,mapPoints);
        Log.d("matrix result",String.valueOf(warp_mat));

        // get current location
        double a00_arr[] = warp_mat.get(0,0);
        double a00 = a00_arr[0];

        double a01_arr[] = warp_mat.get(0,1);
        double a01 = a01_arr[0];

        double b00_arr[] = warp_mat.get(0,2);
        double b00 = b00_arr[0];

        double a10_arr[] = warp_mat.get(1,0);
        double a10 = a10_arr[0];

        double a11_arr[] = warp_mat.get(1,1);
        double a11 = a11_arr[0];

        double b10_arr[] = warp_mat.get(1,2);
        double b10 = b10_arr[0];

        double x_point = a00*currentX + a01*currentY + b00;
        double y_point = a10*currentX + a11*currentY + b10;

        // new coordinates of aligned point
        float newCurrentLocation_x = (float)x_point;
        float newCurrentLocation_y = (float)y_point;
        Log.d("matrix result", Float.toString(newCurrentLocation_x));
        Log.d("matrix result", Float.toString(newCurrentLocation_y));

        return new float[]{newCurrentLocation_x, newCurrentLocation_y};

    }
}
