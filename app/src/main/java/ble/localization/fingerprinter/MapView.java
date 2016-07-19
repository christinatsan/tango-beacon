package ble.localization.fingerprinter;

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

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.Arrays;

/**
 * Created by vishnunair on 7/1/16.
 */
public class MapView extends SubsamplingScaleImageView implements View.OnTouchListener {

    private static final String TAG = "MapView";
    public static final String COORDINATE_TEXT_UPDATE_BROADCAST = "ble.localization.fingerprinter.COORDINATES_CHANGED";
    public static final String PROVIDE_C_AND_N_VALUES = "ble.localization.fingerprinter.SEND_C_AND_N";
    public static final String SELECT_NEXT_COORDINATE_REQUEST = "ble.localization.fingerprinter.REQUEST_NEXT_COORD_SELECTION";
    private static final int defaultCoord = -1;
    private static final int actionToBeHandled = MotionEvent.ACTION_UP;

    protected Context context;
    private boolean touchAllowed;

    private float[] values = new float[9];
    Matrix imageMatrix = new Matrix();
    public float[] thisTouchCoordinates = new float[2];
    public float[] lastTouchCoordinates = new float[2];

    Paint paint = new Paint();


    public MapView(Context context, AttributeSet attr) {
        super(context, attr);
        this.context = context;
        Arrays.fill(thisTouchCoordinates, defaultCoord);
        Arrays.fill(lastTouchCoordinates, defaultCoord);
        touchAllowed = true;
    }

    public void setTouchAllowedBool(boolean isAllowed) {
        this.touchAllowed = isAllowed;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(thisTouchCoordinates[0] == defaultCoord && thisTouchCoordinates[1] == defaultCoord) return;

        float density = getResources().getDisplayMetrics().densityDpi;
        int strokeWidth = (int)(density/60f);

        PointF view_coords = sourceToViewCoord(thisTouchCoordinates[0], thisTouchCoordinates[1]);

        float radius = (getScale() * getSWidth()) * 0.01f;

        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(strokeWidth * 2);
        paint.setColor(Color.BLUE);
        canvas.drawCircle(view_coords.x, view_coords.y, radius, paint);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.argb(255, 51, 181, 229));
        canvas.drawCircle(view_coords.x, view_coords.y, radius, paint);

        if(!(lastTouchCoordinates[0] == defaultCoord && lastTouchCoordinates[1] == defaultCoord)) {
            PointF camera_coords = sourceToViewCoord(lastTouchCoordinates[0], lastTouchCoordinates[1]);

            paint.setColor(Color.RED);
            canvas.drawCircle(camera_coords.x, camera_coords.y, radius, paint);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(Color.RED);
            canvas.drawCircle(camera_coords.x, camera_coords.y, radius, paint);
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (((event.getAction() & MotionEvent.ACTION_MASK) != actionToBeHandled) || !touchAllowed) {
            return super.onTouchEvent(event);
        }

        // We're only handling MotionEvent.ACTION_UP here.
        dumpEvent(event);
        imageMatrix = getMatrix();
        imageMatrix.getValues(values);

        float relativeX = MathFunctions.floatRound(((event.getX() - values[2]) / values[0]), 2);
        float relativeY = MathFunctions.floatRound(((event.getY() - values[5]) / values[4]), 2);
        PointF point = viewToSourceCoord(relativeX, relativeY);

        lastTouchCoordinates[0] = thisTouchCoordinates[0];
        lastTouchCoordinates[1] = thisTouchCoordinates[1];

        thisTouchCoordinates[0] = point.x;
        thisTouchCoordinates[1] = point.y;

        // Show selected coordinates on screen and/or log.
        Log.d(TAG, "X-Coordinate: " + thisTouchCoordinates[0]);
        Log.d(TAG, "Y-Coordinate: " + thisTouchCoordinates[1]);

        // Stop if the last touch coordinates are still the defaults.
        if(lastTouchCoordinates[0] == defaultCoord && lastTouchCoordinates[1] == defaultCoord) {
            Intent request_next_coordinates = new Intent(SELECT_NEXT_COORDINATE_REQUEST);
            context.sendBroadcast(request_next_coordinates);
            return true;
        }

        // Tell the MainActivity that we changed the coordinates.
        Intent in = new Intent(COORDINATE_TEXT_UPDATE_BROADCAST);
        context.sendBroadcast(in);

        // Indirectly provide the C abd N coordinates to the map view.
        Intent in2 = new Intent(PROVIDE_C_AND_N_VALUES);
        in2.putExtra("x_c", lastTouchCoordinates[0]);
        in2.putExtra("y_c", lastTouchCoordinates[1]);
        in2.putExtra("x_n", thisTouchCoordinates[0]);
        in2.putExtra("y_n", thisTouchCoordinates[1]);
        context.sendBroadcast(in2);

        invalidate();

        return true;
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