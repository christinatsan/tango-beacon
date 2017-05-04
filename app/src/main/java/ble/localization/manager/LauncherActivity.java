package ble.localization.manager;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.google.atap.tangoservice.Tango;

import java.util.ArrayList;

public class LauncherActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_TANGO_PERMISSION = 0;
    public static final int REQUEST_CODE_ALL_PERMISSIONS = 123;

    Button l_go_tango;
    Button n_go_tango;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        final Button fp_go = (Button) findViewById(R.id.f_go);
        final Button fpr_go = (Button) findViewById(R.id.fr_go);
        final Button l_go = (Button) findViewById(R.id.l_go);
        final Button n_go = (Button) findViewById(R.id.n_go);
        final Button ap_go = (Button) findViewById(R.id.ap_go);
        l_go_tango = (Button) findViewById(R.id.l_go_tango);
        n_go_tango = (Button) findViewById(R.id.n_go_tango);

        // final Spinner url_selector = (Spinner) findViewById(R.id.url_selector);

        assert (fp_go != null);
        assert (fpr_go != null);
        assert (l_go != null);
        assert (n_go != null);
        assert (ap_go != null);
        assert (l_go_tango != null);
        assert (n_go_tango != null);

        fp_go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), FingerprinterActivity.class));
            }
        });

        fpr_go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), RawFingerprinterActivity.class));
            }
        });

        l_go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), LocatorActivity.class));
            }
        });

        n_go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), NavigatorActivity.class));
            }
        });

        ap_go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri url = Uri.parse(Globals.SERVER_BASE_URL);
                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, url);
                startActivity(launchBrowser);
            }
        });

        l_go_tango.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startAdfListViewIntent = new Intent(getApplicationContext(), AdfUuidListViewActivity.class);
                startAdfListViewIntent.putExtra(AdfUuidListViewActivity.USE_AREA_LEARNING, false);
                startAdfListViewIntent.putExtra(AdfUuidListViewActivity.LOAD_ADF, true);
                startAdfListViewIntent.putExtra(AdfUuidListViewActivity.LAUNCH_COMMAND, AdfUuidListViewActivity.START_LOCATOR);
                startActivity(startAdfListViewIntent);
            }
        });

        n_go_tango.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startAdfListViewIntent = new Intent(getApplicationContext(), AdfUuidListViewActivity.class);
                startAdfListViewIntent.putExtra(AdfUuidListViewActivity.USE_AREA_LEARNING, false);
                startAdfListViewIntent.putExtra(AdfUuidListViewActivity.LOAD_ADF, true);
                startAdfListViewIntent.putExtra(AdfUuidListViewActivity.LAUNCH_COMMAND, AdfUuidListViewActivity.START_NAVIGATOR);
                startActivity(startAdfListViewIntent);
            }
        });

        // permissions
        if (Globals.isTangoDevice(getApplicationContext())) {
            // if we're running on a Tango device...
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // if we're running M or higher, comply with the newer permissions model of explicitly requesting "dangerous" permissions
                ArrayList<String> permissions_needed = new ArrayList<>();
                permissions_needed.add(Manifest.permission.CAMERA);
                permissions_needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                permissions_needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                permissions_needed.add(Manifest.permission.READ_PHONE_STATE);

                requestPermissions(permissions_needed.toArray(new String[0]), REQUEST_CODE_ALL_PERMISSIONS);
            } else {
                // else assume we have all the permissions and just obtain the ADF permission by itself
                startActivityForResult(
                        Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE), 0);
            }
        } else {
            // if not a Tango device, disable all Tango features
            l_go_tango.setEnabled(false);
            n_go_tango.setEnabled(false);
        }

//        ArrayAdapter<String> url_adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, Globals.ALL_URLS);
//        url_selector.setAdapter(url_adapter);
//        url_selector.setSelection(Globals.DEFAULT_URL);
//        url_selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                Globals.SERVER_BASE_URL = Globals.ALL_URLS[position];
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parent) {
//
//            }
//        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ALL_PERMISSIONS:
                if (grantResults.length > 0) {
                    for (int i = 0; i < permissions.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            Globals.showSnackbar(findViewById(android.R.id.content),
                                    "The permission " + permissions[i] + " has not been granted. It must be granted to use Tango features.");
                            // disable Tango stuff if permission is denied
                            l_go_tango.setEnabled(false);
                            n_go_tango.setEnabled(false);
                        }
                    }
                }
                startActivityForResult(
                        Tango.getRequestPermissionIntent(Tango.PERMISSIONTYPE_ADF_LOAD_SAVE), 0);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The result of the permission activity.
        //
        // Note that when the permission activity is dismissed, the HelloAreaDescriptionActivity's
        // onResume() callback is called. As the TangoService is connected in the onResume()
        // function, we do not call connect here.
        //
        // Check which request we're responding to
        if (requestCode == REQUEST_CODE_TANGO_PERMISSION) {
            // Make sure the request was successful
            if (resultCode == RESULT_CANCELED) {
                Globals.showSnackbar(findViewById(android.R.id.content), getApplicationContext().getString(R.string.arealearning_permission));
                finish(); // TODO: Line needed?
            }
        }
    }
}
