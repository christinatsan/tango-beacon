package ble.localization.manager;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class AllowedErrorModifierDialog extends Dialog {

    private static final String TAG = "AllowedErrorModifierDialog";

    private Context context;

    public AllowedErrorModifierDialog(final Context context,
                                      final double feetPerStep,
                                      final double feetPerMeter,
                                      double currentAllowedError) {
        super(context);
        this.context = context;

        this.setContentView(R.layout.dialog_allowederrormodifier);
        this.setTitle("Modify Allowed Error");
        this.setCancelable(true);
        this.setCanceledOnTouchOutside(true);

        Button goButton = (Button) findViewById(R.id.cmd_ModifyButton);
        Button closeButton = (Button) findViewById(R.id.cmd_closeButton);

        final TextView metersLabel = (TextView) findViewById(R.id.meterslabel);
        final TextView stepsLabel = (TextView) findViewById(R.id.stepslabel);

        final EditText eBox = (EditText) findViewById(R.id.errorinput);
        eBox.setText(Double.toString(currentAllowedError));

        int meters_now = (int)(currentAllowedError/feetPerMeter);
        int steps_now = (int)(currentAllowedError/feetPerStep);

        metersLabel.setText("Error (in meters): " + Integer.toString(meters_now));
        stepsLabel.setText("Error (in steps): " + Integer.toString(steps_now));

        eBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                double input = 0;
                try {
                    input = Double.parseDouble(charSequence.toString());
                } catch (NumberFormatException e) {
                    return;
                }
                double meters_input = MathFunctions.doubleRound(input/feetPerMeter, 2);
                double steps_input = MathFunctions.doubleRound(input/feetPerStep, 2);

                metersLabel.setText("Error (in meters): " + Double.toString(meters_input));
                stepsLabel.setText("Error (in steps): " + Double.toString(steps_input));
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        goButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String e = eBox.getText().toString();
                double ed = Double.parseDouble(e);
                if(ed <= 0) return;

                // Send to Navigator.
                Intent send_to_navigator = new Intent(NavigatorActivity.ERROR_CHANGE_BROADCAST_ACTION);
                send_to_navigator.putExtra("desired_error", ed);
                context.sendBroadcast(send_to_navigator);

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
