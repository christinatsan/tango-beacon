package ble.localization.fingerprinter;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

/**
 * Created by vishnunair on 7/21/16.
 */
public class EnlargedCameraView extends Dialog {

    private Context context;
    private String image_url;
    private CameraView cView;

    public EnlargedCameraView(final Context context, String img_url) {
        super(context);
        this.context = context;
        image_url = img_url;
        // this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(R.layout.cameraview_enlarged);
        cView = (CameraView) findViewById(R.id.enlargedCameraView);
        this.setTitle("Enlarged Camera View");
        this.setCancelable(false);
        this.setCanceledOnTouchOutside(false);

        Button fButton = (Button) findViewById(R.id.ecvFingerprintButton);
        Button cButton = (Button) findViewById(R.id.closeButton);

        fButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                // Send intent
                final Intent beginFingerprinting = new Intent(MainActivity.BEGIN_FINGERPINTING_BROADCAST);
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
