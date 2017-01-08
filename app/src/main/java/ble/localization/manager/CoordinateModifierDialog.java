package ble.localization.manager;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import static ble.localization.manager.CameraView.SEND_COORD_TO_MAP_BROADCAST;

public class CoordinateModifierDialog extends Dialog {

    private static String TAG = "CoordinateModiferDialog";

    private Context context;

    public CoordinateModifierDialog(final Context context, float x, float y) {
        super(context);
        this.context = context;

        this.setContentView(R.layout.dialog_coordinatemodifier);
        this.setTitle("Modify Current Coordinates");
        this.setCancelable(true);
        this.setCanceledOnTouchOutside(true);

        Button goButton = (Button) findViewById(R.id.cmd_ModifyButton);
        Button closeButton = (Button) findViewById(R.id.cmd_closeButton);

        final EditText xBox = (EditText) findViewById(R.id.xcinput);
        final EditText yBox = (EditText) findViewById(R.id.ycinput);
        xBox.setText(Float.toString(x), TextView.BufferType.EDITABLE);
        yBox.setText(Float.toString(y), TextView.BufferType.EDITABLE);

        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String x = xBox.getText().toString();
                String y = yBox.getText().toString();
                if(x.length() == 0 || y.length() == 0) {
                    return;
                }

                double xc = Double.parseDouble(x);
                double yc = Double.parseDouble(y);

                if(xc < 0 || yc < 0) {
                    return;
                }

                // Send to Map view.
                Intent send_to_map = new Intent(SEND_COORD_TO_MAP_BROADCAST);
                send_to_map.putExtra("x", xc);
                send_to_map.putExtra("y", yc);
                context.sendBroadcast(send_to_map);

                dismiss();
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }
}
