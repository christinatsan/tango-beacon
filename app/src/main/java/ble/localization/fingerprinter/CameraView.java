package ble.localization.fingerprinter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

import cz.msebera.android.httpclient.Header;

/**
 * Created by vishnunair on 7/16/16.
 */
public class CameraView extends SubsamplingScaleImageView implements View.OnTouchListener {

    private static final String TAG = "CameraView";
    public static final String SEND_IMAGE_URL_BROADCAST = "ble.localization.fingerprinter.SEND_VIEW_IMAGE_URL";
    public static final String SEND_COORD_TO_MAP_BROADCAST = "ble.localization.fingerprinter.SEND_TO_MAP";
    private static final int defaultCoord = -1;
    private static final int actionToBeHandled = MotionEvent.ACTION_DOWN;

    private static final String CAMERA_FETCH_URL_ENDPOINT = "/camera_view";
    private static final String COORDINATE_FETCH_URL_ENDPOINT = "/camera_view_coord";

    protected Context context;
    private boolean imagePresent;

    private float[] values = new float[9];
    Matrix imageMatrix = new Matrix();
    public float[] lastTouchPixelCoordinates = new float[2];
    public float[] lastTouchRealCoordinates = new float[2];

    Paint paint = new Paint();

    protected String curr_image_url;
    protected String curr_image_id;
    private TextView enlargedCameraViewCoordinateTextView = null;

    public CameraView(Context context, AttributeSet attr) {
        super(context, attr);
        this.context = context;
        Arrays.fill(lastTouchPixelCoordinates, defaultCoord);
        Arrays.fill(lastTouchRealCoordinates, defaultCoord);
        imagePresent = false;
    }

    public void setImagePresent(boolean isAllowed) {
        this.imagePresent = isAllowed;
    }

    public boolean isImagePresent() { return imagePresent; }

    public void setECVCoordinateTextView(TextView tv) {
        this.enlargedCameraViewCoordinateTextView = tv;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(lastTouchPixelCoordinates[0] == defaultCoord && lastTouchPixelCoordinates[1] == defaultCoord) return;

        float density = getResources().getDisplayMetrics().densityDpi;
        int strokeWidth = (int)(density/60f);

        PointF view_coords = sourceToViewCoord(lastTouchPixelCoordinates[0], lastTouchPixelCoordinates[1]);

        float radius = (getScale() * getSWidth()) * 0.01f;

        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(strokeWidth * 2);
        paint.setColor(Color.RED);
        canvas.drawCircle(view_coords.x, view_coords.y, radius, paint);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.GREEN);
        canvas.drawCircle(view_coords.x, view_coords.y, radius, paint);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // The following condition is always true for now so that code below won't be executed.
        // Will be changed back later.
        if (((event.getAction() & MotionEvent.ACTION_MASK) != actionToBeHandled) || !imagePresent || imagePresent) {
            return super.onTouchEvent(event);
        }

        // We're only handling MotionEvent.ACTION_DOWN here.
        dumpEvent(event);
        imageMatrix = getMatrix();
        imageMatrix.getValues(values);

        float relativeX = MathFunctions.floatRound(((event.getX() - values[2]) / values[0]), 2);
        float relativeY = MathFunctions.floatRound(((event.getY() - values[5]) / values[4]), 2);
        PointF point = viewToSourceCoord(relativeX, relativeY);
        lastTouchPixelCoordinates[0] = point.x;
        lastTouchPixelCoordinates[1] = point.y;

        final AsyncHttpClient client = new AsyncHttpClient();
        final String url = Globals.SERVER_BASE_API_URL + COORDINATE_FETCH_URL_ENDPOINT +
                "?x_p=" + lastTouchPixelCoordinates[0] + "&y_p=" + lastTouchPixelCoordinates[1] +
                "&id=" + curr_image_id;

        client.get(url, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject responseBody) {
                // Successfully got a response
                try {
                    lastTouchRealCoordinates[0] = (float)(double) responseBody.get("x");
                    lastTouchRealCoordinates[1] = (float)(double) responseBody.get("y");
                } catch (JSONException e) {
                    Log.e(TAG, "Unexpected JSON Exception.", e);
                }

                // Show selected coordinates on screen and/or log.
                Log.d(TAG, "X-Coordinate (Pixel): " + lastTouchPixelCoordinates[0]);
                Log.d(TAG, "Y-Coordinate (Pixel): " + lastTouchPixelCoordinates[1]);

                Log.d(TAG, "X-Coordinate (Real): " + lastTouchRealCoordinates[0]);
                Log.d(TAG, "Y-Coordinate (Real): " + lastTouchRealCoordinates[1]);

                // Change the map view's dot (i.e. assign these real coordinates to map view).
                Intent send_to_map = new Intent(SEND_COORD_TO_MAP_BROADCAST);
                send_to_map.putExtra("x", lastTouchRealCoordinates[0]);
                send_to_map.putExtra("y", lastTouchRealCoordinates[1]);
                context.sendBroadcast(send_to_map);

                if(enlargedCameraViewCoordinateTextView != null) {
                    enlargedCameraViewCoordinateTextView.setText("(" + lastTouchRealCoordinates[0] + ", " + lastTouchRealCoordinates[1] + ")");
                }

                invalidate();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable error, JSONObject responseBody)
            {
                // Request failed
                setImagePresent(false);
                View root_view = ((Activity)context).getWindow().getDecorView().findViewById(android.R.id.content);

                if (statusCode == 503) {
                    Globals.showSnackbar(root_view, "Matlab unavailable on server. View not retrieved.");
                } else {
                    Globals.showSnackbar(root_view, "Unable to retrieve real coordinates. (Status code: " + statusCode + ")");
                }
            }
        });

        return true;
    }

    protected void updateCameraView(float x_c, float y_c, float x_n, float y_n) {
        final float[] C = new float[3];
        C[0] = x_c;
        C[1] = y_c;
        C[2] = 1.56f;   // My height in meters
        final float[] N = new float[3];
        N[0] = x_n;
        N[1] = y_n;
        N[2] = 1.56f;   // My height in meters

        if(!Globals.usingCameraViewImageFetch) return;

        final AsyncHttpClient client = new AsyncHttpClient();
        final String url = Globals.SERVER_BASE_API_URL + CAMERA_FETCH_URL_ENDPOINT +
                "?x_c=" + C[0] + "&y_c=" + C[1] + "&z_c=" + C[2] +
                "&x_n=" + N[0] + "&y_n=" + N[1] + "&z_n=" + N[2];

        client.get(url, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject responseBody) {
                // Successfully got a response
                try {
                    curr_image_id = (String)responseBody.get("id");
                    curr_image_url = (String)responseBody.get("url");
                } catch (JSONException e) {
                    Log.e(TAG, "Unexpected JSON Exception.", e);
                    return;
                }

                Intent send_image = new Intent(SEND_IMAGE_URL_BROADCAST);
                send_image.putExtra("url", curr_image_url);
                context.sendBroadcast(send_image);
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable error, JSONObject responseBody)
            {
                // Request failed
                setImagePresent(false);
                View root_view = ((Activity)context).getWindow().getDecorView().findViewById(android.R.id.content);

                if (statusCode == 503) {
                    Globals.showSnackbar(root_view, "Matlab unavailable on server. View not retrieved.");
                } else {
                    Globals.showSnackbar(root_view, "Unable to retrieve view. (Status code: " + statusCode + ")");
                }
            }
        });
    }


    private void dumpEvent(MotionEvent event) {
        String names[] = { "DOWN" , "UP" , "MOVE" , "CANCEL" , "OUTSIDE" ,
                "POINTER_DOWN" , "POINTER_UP" , "7?" , "8?" , "9?" };
        StringBuilder sb = new StringBuilder();
        int action = event.getAction();
        int actionCode = action & MotionEvent.ACTION_MASK;
        sb.append("event ACTION_" ).append(names[actionCode]);
        if (actionCode == MotionEvent.ACTION_POINTER_DOWN
                || actionCode == MotionEvent.ACTION_POINTER_UP) {
            sb.append("(pid " ).append(
                    action >> MotionEvent.ACTION_POINTER_ID_SHIFT);
            sb.append(")" );
        }
        sb.append("[" );
        for (int i = 0; i < event.getPointerCount(); i++) {
            sb.append("#" ).append(i);
            sb.append("(pid " ).append(event.getPointerId(i));
            sb.append(")=" ).append((int) event.getX(i));
            sb.append("," ).append((int) event.getY(i));
            if (i + 1 < event.getPointerCount())
                sb.append(";" );
        }
        sb.append("]" );
        Log.d(TAG, sb.toString());
    }

}