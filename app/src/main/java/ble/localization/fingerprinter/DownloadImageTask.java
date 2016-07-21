package ble.localization.fingerprinter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.davemorrissey.labs.subscaleview.ImageSource;

import java.io.InputStream;

/**
 * Created by vishnunair on 7/21/16.
 */
public class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
    private CameraView cView;

    public DownloadImageTask(CameraView cView) {
        this.cView = cView;
    }

    protected Bitmap doInBackground(String... urls) {
        String urldisplay = urls[0];
        Bitmap decoded_stream = null;
        try {
            InputStream in = new java.net.URL(urldisplay).openStream();
            decoded_stream = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
        }

        return decoded_stream;
    }

    protected void onPostExecute(Bitmap result) {
        cView.setImage(ImageSource.bitmap(result));
        cView.setImagePresent(true);
    }
}