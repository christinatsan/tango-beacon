package ble.localization.manager;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Created by vishnunair on 7/21/16.
 */
public class EnlargedCameraView extends Dialog {

    private static final String TAG = "EnlargedCameraView";

    private Context context;
    private String image_url;
    private CameraView cView;

    public EnlargedCameraView(final Context context, String img_url) {
        super(context);
        this.context = context;
        image_url = img_url;
        // this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(R.layout.dialog_cameraview_enlarged);
        cView = (CameraView) findViewById(R.id.enlargedCameraView);
        this.setTitle("Enlarged Camera View");
        this.setCancelable(false);
        this.setCanceledOnTouchOutside(false);

        Button fButton = (Button) findViewById(R.id.ecvFingerprintButton);
        Button cButton = (Button) findViewById(R.id.closeButton);
        TextView coordText = (TextView) findViewById(R.id.realCoordinateText);

        cView.setECVCoordinateTextView(coordText);

        fButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                // Send intent
                final Intent beginFingerprinting = new Intent(FingerprinterActivity.BEGIN_FINGERPINTING_BROADCAST);
                context.sendBroadcast(beginFingerprinting);
            }
        });

        cButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    public void downloadImage() {
        new DownloadImageTask(cView).execute(image_url);
    }
}
