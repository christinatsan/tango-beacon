package ble.localization.fingerprinter;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

/**
 * Created by vishnunair on 7/1/16.
 */
public class modifiedSubsamplingScaleImageView extends SubsamplingScaleImageView implements View.OnTouchListener {

    public static final String TAG = "ImageView";

    private float[] values = new float[9];
    Matrix imageMatrix = new Matrix();
    private float[] current_coordinates = new float[2];

    public modifiedSubsamplingScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if ((event.getAction() & MotionEvent.ACTION_MASK) != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }

        // Handle touch events here...
        dumpEvent(event);
        imageMatrix = getMatrix();
        imageMatrix.getValues(values);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_UP:
                float relativeX = mathFunctions.round(((event.getX() - values[2]) / values[0]), 2);
                float relativeY = mathFunctions.round(((event.getY() - values[5]) / values[4]), 2);
                PointF point = viewToSourceCoord(relativeX, relativeY);
                current_coordinates[0] = point.x;
                current_coordinates[1] = point.y;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                break;
        }

        // Show selected coordinates on screen and/or log.
        Log.d(TAG, "X-Coordinate: " + current_coordinates[0]);
        Log.d(TAG, "Y-Coordinate: " + current_coordinates[1]);

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
