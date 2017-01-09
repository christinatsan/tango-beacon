package ble.localization.manager;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        final Button fp_go = (Button) findViewById(R.id.f_go);
        final Button l_go = (Button) findViewById(R.id.l_go);
        final Button n_go = (Button) findViewById(R.id.n_go);
        final Button ap_go = (Button) findViewById(R.id.ap_go);

        assert (fp_go != null);
        assert (l_go != null);
        assert (n_go != null);
        assert (ap_go != null);

        fp_go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(v.getContext(), FingerprinterActivity.class));
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
    }
}
