package com.example.croam;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.SEND_SMS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import static com.example.croam.ServiceTrackerKt.getServiceState;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


public class MainActivity extends AppCompatActivity {
    public final static String DEBUG_TAG = "MainActivity";
    public static final int PERMISSION_REQUEST_CODE = 200;
    private static final String CONTACT1 = "contact1";
    private static final String CONTACT2 = "contact2";
    private static final String CONTACT3 = "contact3";
    private static final String CONTACT4 = "contact4";
    private static final String CONTACT5 = "contact5";
    public static String SERVER_URL = "http://192.168.43.35:3000";
    public static String policeDetails = "";
    public static String API_KEY = "AIzaSyB5A7N_tKnjwSdsmRinYaOVLbAOana_A9s";
    public static String latituteField = "";
    public static String longitudeField = "";
    public static String url = null;
    public static String phone;
    public static String[] PERMISSIONS =
            new String[]{ACCESS_FINE_LOCATION, CAMERA, READ_CONTACTS, RECEIVE_SMS, SEND_SMS,
                    WRITE_EXTERNAL_STORAGE, RECORD_AUDIO};
    public static int noofemergencycontacts = 0;
    public static double threshold = 0.8;
    static SharedPreferences prefs;
    static ArrayList<String> contactinfo = new ArrayList<>();
    private static String[] contacts = {CONTACT1, CONTACT2, CONTACT3, CONTACT4, CONTACT5};

    static {
        System.loadLibrary("native-mfcc-lib");
    }

    public boolean isOn;
    public BottomNavigationView navView;
    public float ml_output_score[] = new float[5];
    Fragment fragment = null;
    CRoamService croamService;
    BroadcastReceiver score_receiver = null;
    private Camera camera;
    private int cameraId = 0;
    private FusedLocationProviderClient clinent;
    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            Fragment fragment = null;
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    fragment = new Home();
                    break;
                case R.id.navigation_report:
//                    fragment = new Police();
//                    Bundle b = new Bundle();
//                    Log.d("LATTS", "onNavigationItemSelected: " + latituteField);
//                    Log.d("LATTS", "onNavigationItemSelected: " + longitudeField);
//                    b.putString("lat", latituteField);
//                    b.putString("long", longitudeField);
//                    fragment.setArguments(b);
                    fragment = new ReportFragment();

                    break;
                case R.id.navigation_contacts:
                    fragment = new Contact();
                    break;
                case R.id.navigation_news:
                    Intent viewIntent =
                            new Intent("android.intent.action.VIEW",
                                    Uri.parse("https://frontend-69.appspot.com/"));
                    startActivity(viewIntent);
                    break;
                case R.id.navigation_profile:
                    fragment = new Profile();
                    break;
            }
            return loadFragment(fragment);
        }
    };

    public static int emergencycontacts() {
        int x = 0;
        for (String c : contacts) {
            if (prefs.getString(c, null) != null) {
                ++x;
                contactinfo.add(x - 1, prefs.getString(c, null));
            }
        }
        return x;
    }

    private boolean loadFragment(Fragment fragment) {
        //switching fragment
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("OPENED_FROM_NOTIFICATION", false) && isMyServiceRunning(CRoamService.class)) {
//            Log.d(TAG, "onNewIntent: opened");
            isOn = true;
            loadFragment(new Home());
        }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        if (isMyServiceRunning(CRoamService.class)) {
            isOn = true;
        }
        actionOnService(Actions.START);
        prefs = PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext()); //Get the preferences
        SERVER_URL = prefs.getString("server_url", SERVER_URL);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        setContentView(R.layout.activity_main);
        navView = findViewById(R.id.nav_view);
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        loadFragment(new Home());


        // Emergency contact
        noofemergencycontacts = emergencycontacts();

        // Requesting Permissions
        requestAllPermissions();

        croamService = new CRoamService();
//        if ((ActivityCompat.checkSelfPermission(MainActivity.this, ACCESS_FINE_LOCATION) !=
//        PackageManager.PERMISSION_GRANTED)
//                && (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission
//                .ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
//
//            return;
//        }
        score_receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                float score[] = intent.getFloatArrayExtra("SCORE");
                Log.d("SCORE", "onReceive: " + score);
                if (score != null) {
                    for (int i = 0; i < 5; i++) {
                        ml_output_score[i] = score[i];
                    }

                } else {

                }

                // do something here.
            }
        };

        if (!getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Toast.makeText(this, "No camera on this device", Toast.LENGTH_LONG)
                    .show();
        } else {
            cameraId = findCamera();
            if (cameraId < 0) {
                Toast.makeText(this, "No front facing camera found." + cameraId,
                        Toast.LENGTH_LONG).show();
                Log.v(DEBUG_TAG, "Camera not OK");
            } else {

                if (camera != null) {
                    camera = Camera.open(cameraId);
                } else {
                    requestAllPermissions();
                }
//                    Log.v(DEBUG_TAG, "Camera ID: " + camera.toString());
            }

        }


    }

    //Permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Toast.makeText(MainActivity.this, "Permission Granted!", Toast
//                    .LENGTH_SHORT).show();
                } else {
//                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT)
//                    .show();
                }
        }
    }

    private void requestPermission(String permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this,
                new String[]{permissionName}, permissionRequestCode);
    }

    public boolean checkPermissions() {
        boolean ret = true;
        for (String permission : PERMISSIONS) {
            int permissionCheck = ActivityCompat.checkSelfPermission(
                    this, permission);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ret = false;
            }

        }
        return ret;
    }

    private void showPermission(String[] permissions, int requestCode) {
        for (String permission : permissions) {
            Log.d("PERMISSIONS", "showPermission: " + permission);
            int permissionCheck = ActivityCompat.checkSelfPermission(
                    this, permission);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    showExplanation("Permission Needed", "Rationale", permission, requestCode);
                } else {
                    requestPermission(permission, requestCode);
                }
            } else {
//            Toast.makeText(MainActivity.this, "Permission (already) Granted!", Toast
//            .LENGTH_SHORT).show();
            }
        }

    }

    private void showExplanation(String title, String message, final String permission,
            final int permissionRequestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        requestPermission(permission, permissionRequestCode);
                    }
                });
        builder.create().show();
    }

    public void requestAllPermissions() {
        new CountDownTimer(10000, 1000) {
            public void onFinish() {
                final LocationManager manager = (LocationManager) getSystemService(
                        Context.LOCATION_SERVICE);
                if (!manager.isProviderEnabled(
                        LocationManager.GPS_PROVIDER)) {//if gps if turned off then go to settings.
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            }

            public void onTick(long millisUntilFinished) {
                // millisUntilFinished    The amount of time until finished.
            }
        }.start();
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    private int findCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            Log.d(DEBUG_TAG, "Camera found");
            cameraId = i;
            break;

        }
        Log.d("CAMERA", "findCamera: " + cameraId);
        return cameraId;
    }

    @Override
    public void onBackPressed() {
//        Intent a = new Intent(Intent.ACTION_MAIN);
//        a.addCategory(Intent.CATEGORY_HOME);
//        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(a);
        new AlertDialog.Builder(this)
                .setTitle("Closing Application")
                .setMessage("Do you want to exit the Application and stop help services")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        stopCroamService();
                        finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();

    }

    //Action on pressing on/off switch
    public void onSwitchOn() {
        if (checkPermissions()) {
            if (camera == null) camera = Camera.open(cameraId);
            if (emergencycontacts() == 0) {
                fragment = new Contact();
                loadFragment(fragment);
            } else {
                isOn = true;
                startCroamService();

            }
        } else {

            requestAllPermissions();
        }
    }

    public void onSwitchOff() {
        isOn = false;
        stopCroamService();
    }

    public void startCroamService() {

        Intent serviceIntent = new Intent(this, CRoamService.class);
        serviceIntent.putExtra("inputExtra", "Service is running in");
//        ContextCompat.startForegroundService(this, serviceIntent);
        startService(serviceIntent);
        Toast.makeText(getBaseContext(), "Service Started", Toast.LENGTH_SHORT).show();
//        Log.v(TAG, "Service Started");
    }

    public void stopCroamService() {
        Intent serviceIntent = new Intent(this, CRoamService.class);
        stopService(serviceIntent);
        Toast.makeText(getBaseContext(), "Service Stopped", Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction("SEND_ML_OUTPUT");
        LocalBroadcastManager.getInstance(this).registerReceiver(score_receiver, filter);
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(score_receiver);
        super.onStop();
    }

    private void actionOnService(Actions action) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return;
        Log.e("here","here");
        Intent intent = new Intent(this, EndlessService.class);
        intent.setAction(action.name());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                log("Starting the service in >=26 Mode")
            startForegroundService(intent);
            return;
        }
        startService(intent);

    }
}
