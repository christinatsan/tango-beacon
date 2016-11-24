package ble.localization.fingerprinter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.davemorrissey.labs.subscaleview.ImageSource;

import java.io.InputStream;

/**
 * Downloads the new camera view image and sets it.
 */
class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {

    private static final String TAG = "DownloadImageTask";

    private CameraView cView;

    /**
     * Initializes the task.
     * @param cView The camera view.
     */
    DownloadImageTask(CameraView cView) {
        this.cView = cView;
    }

    /**
     * Downloads the image in a background process.
     * @param urls The URL of the image.
     * @return The decoded image.
     */
    protected Bitmap doInBackground(String... urls) {
        String urldisplay = urls[0];
        Bitmap decoded_stream = null;
        try {
            InputStream in = new java.net.URL(urldisplay).openStream();
            decoded_stream = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e(TAG, "Error!", e);
        }

        return decoded_stream;
    }

    /**
     * Sets the image in the camera view.
     * @param result The decoded image.
     */
    protected void onPostExecute(Bitmap result) {
        cView.setImage(ImageSource.bitmap(result));
        cView.setImagePresent(true);
    }
}