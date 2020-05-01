package com.example.croam;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECEIVE_SMS;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.SEND_SMS;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.Notification.EXTRA_NOTIFICATION_ID;
import static android.support.constraint.Constraints.TAG;

public class CRoamService extends Service {

    public static String croam_server_url="http://192.168.43.125:3000";
    public static boolean screenOff1=false;
    public static boolean screenOff2=false;
    public static boolean screenOn1=false;
    public static boolean screenOn2=false;
    public static double lat;
    public static double lng;
    public static String allmobilenumberofpolice="01126597272";
    public static String url=null;
    public static String phone;
    private Thread recordingThread;
    private Thread recognitionThread;

    private static final String LOG_TAG = "AudioRecordTest";
    private static final String OUTPUT_SCORES_NAME = "output0";
    private static final String INPUT_DATA_NAME = "sequential_1_input";
    public final static String DEBUG_TAG = "MainActivity";
    private static final String clientID = "c26974dfd247815";
    private static final String MODEL_FILENAME = "file:///android_asset/tf_help_model10dB.pb";
//private static final String MODEL_FILENAME = "file:///android_asset/help_model.pb";
//private static final String MODEL_FILENAME = "file:///android_asset/tf_help_model2_after_removing_noise.pb";
    public static final String MyPREFERENCES = "MyPrefs_Anjaneya" ;

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 3000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);

    boolean shouldContinueRecognition = true;
    boolean shouldContinue = true;
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;

    private final ReentrantLock recordingBufferLock = new ReentrantLock();
    private TensorFlowInferenceInterface inferenceInterface;

    private static int recognitionResultLength = 5;
    private boolean[] recognitionResult = new boolean[recognitionResultLength];

    public String AddressURL;
    private Camera camera;
    private int cameraId = 0;

    public static DBHandler db;
    public static int noofemergencycontacts=0;
    public native float[] nativeMFCC(double[] buffer);

    public MyBroadCastReciever myBroadcastReciever;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private LocationCallback mLocationCallback;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private Location mLocation;
    public static double threshold = 0.8;

    public CRoamService() {
        super();
    }
    @Override
    public void onCreate() {

        db=new DBHandler(getApplicationContext());
        noofemergencycontacts=db.noofemergencycontacts();
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILENAME);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        startLocationUpdates();
        getLastLocation();

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        myBroadcastReciever=new MyBroadCastReciever(this);
        registerReceiver((myBroadcastReciever),filter);

        if (!getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Toast.makeText(this, "No camera on this device", Toast.LENGTH_LONG)
                    .show();
        } else {
            cameraId = findCamera();
            if (cameraId < 0) {
                Toast.makeText(this, "No front facing camera found."+ cameraId,
                        Toast.LENGTH_LONG).show();
                Log.v(DEBUG_TAG, "Camera not OK");
            } else {

//                if(camera!=null)
                if(camera==null)camera = Camera.open(cameraId);
                else{

                }
//                    Log.v(DEBUG_TAG, "Camera ID: " + camera.toString());
            }

        }
    }
    private void startLocationUpdates() {
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                Looper.getMainLooper());
    }
    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLocation = task.getResult();
                                lat = mLocation.getLatitude();
                                lng = mLocation.getLongitude();
                                AddressURL = "http://www.google.com/maps/place/" + lat + "," + lng;
                            } else {
                                Log.w(TAG, "Failed to get location.");
                            }
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }
    private void onNewLocation(Location location) {
        Log.i(TAG, "New location: " + location);
        mLocation = location;
        lat = location.getLatitude();
        lng = location.getLongitude();
        AddressURL = "http://www.google.com/maps/place/" + lat + "," + lng;

        // Notify anyone listening for broadcasts about the new location.
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service started", Toast.LENGTH_SHORT).show();
        createNotificationChannel();
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.putExtra("OPENED_FROM_NOTIFICATION",true);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent openPendingIntent = PendingIntent.getActivity(this,
                0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Help Detection is On")
                .setContentText("Service is running now")
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(openPendingIntent)
                .build();
        startForeground(1, notification);


        if(shouldContinueRecognition){
            startRecording();
            startRecognition();

        }


        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecognition();
        stopRecording();
        unregisterReceiver(myBroadcastReciever);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }


    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        //  Log.v(LOG_TAG,"");
        // Estimate the buffer size we'll need for this device.
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            Log.v(LOG_TAG, "Recording state: " + record.getState());
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Start recording");

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            int maxLength = recordingBuffer.length;
            int newRecordingOffset = recordingOffset + numberRead;
            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
            int firstCopyLength = numberRead - secondCopyLength;
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
                recordingOffset = newRecordingOffset % maxLength;
            } finally {
                recordingBufferLock.unlock();
            }
        }

        record.stop();
        record.release();
    }
    private void recognize() {
        Log.v(LOG_TAG, "Start recognition");
        short[] inputBuffer = new short[RECORDING_LENGTH];
        double[] doubleInputBuffer = new double[RECORDING_LENGTH];
        final float[] outputScores = new float[1];
        String[] outputScoresNames = new String[]{OUTPUT_SCORES_NAME};
        int k=0;
        int temp=0;
        while(shouldContinueRecognition) {
            k++;
            temp=k%50;
            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                System.arraycopy(recordingBuffer, 0, inputBuffer, 0, maxLength);
            } finally {
                recordingBufferLock.unlock();
            }

            // We need to feed in float values between -1.0 and 1.0, so divide the
            // signed 16-bit inputs.
            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                doubleInputBuffer[i] = inputBuffer[i] / 32767.0;
            }

            //MFCC java library.
            MFCC mfccConvert = new MFCC();
//            float[] mfccInput = mfccConvert.process(doubleInputBuffer);
            float[] mfccInput = nativeMFCC(doubleInputBuffer);
            if(temp==0)Log.v(LOG_TAG, k+" MFCC Input====> " + Arrays.toString(doubleInputBuffer));

            // Run the model.
            inferenceInterface.feed(INPUT_DATA_NAME, mfccInput, 1, 376, 40);
            inferenceInterface.run(outputScoresNames);
            inferenceInterface.fetch(OUTPUT_SCORES_NAME, outputScores);
//            Log.v(LOG_TAG, "OUTPUT======> " + Arrays.toString(outputScores));

            boolean isRecognised = outputScores[0] > threshold;
           // Log.d(TAG, "Output Score Of recognition: "+Arrays.toString(outputScores));
            if(isRecognised) {
                Log.d(TAG, "recognized: "+Arrays.toString(outputScores));
                //  getCurrentLocation();

                boolean isPreviousRecognised = false;
                for(boolean i : recognitionResult) {
                    isPreviousRecognised |= i;
                }
                if(isPreviousRecognised) {
                } else {
//                    Log.d(TAG, "recognized: "+Arrays.toString(outputScores));

                    onDetectingHelp();
//                    sendMyLocation();
//                    takePhoto();
//                    runOnUiThread(new Runnable() {
//                        public void run()
//                        {
//                            Toast.makeText(getBaseContext(), "detected help", Toast.LENGTH_SHORT).show();
//                            //Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show();
//                        }
//                    });
                }

            }
            for(int i = 0; i < recognitionResultLength - 1; i++) {
                recognitionResult[i] = recognitionResult[i+1];
            }
            recognitionResult[recognitionResultLength-1] = isRecognised;

            //Output the result.
            //        String result = "";
            //        for (int i = 0;i<outputScores.length;i++) {
            //            if (outputScores[i] == 0)
            //                break;
            //            result += map[(int) outputScores[i]];
            //        }
            //        final String r = result;
            //        this.runOnUiThread(new Runnable() {
            //            @Override
            //            public void run() {
            //                outputText.setText(r);
            //            }
            //        });

            //        Log.v(LOG_TAG, "End recognition: " +result);
        }
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }
    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecording() {
        if (recordingThread == null) {
            return;
        }
        shouldContinue = false;
        recordingThread = null;
    }
    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }

    public void onDetectingHelp(){

        Log.d(TAG, "onDetectingHelp: Detected Help");
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        "Help Detected",
                        Toast.LENGTH_SHORT).show();
            }
        });
//        Toast.makeText(this, "Help Detected", Toast.LENGTH_SHORT).show();
        getLastLocation();
        doThis();
    }
    public void doThis(){
        sendMyLocation();
        takePhoto();
    }

    public void sendMyLocation(){
        String message = "Please Help Me. My Location is: "+AddressURL;
        message+="\n"+ "Nearby Police Station Contacts: "+"\n"+ allmobilenumberofpolice;
        System.out.println("SEND LOCATION");
        System.out.println(message);
        final String str=message;
        sendSMS(message);

    }
    public static void sendSMS(String message) {
        noofemergencycontacts=db.noofemergencycontacts();
        System.out.println("Number of emergency contacts "+noofemergencycontacts);
        for(int i=1; i<=noofemergencycontacts; i++) {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(db.getphone(i), null, message, null, null);
            Log.v(DEBUG_TAG, "SMS SENT TO "+db.getphone(i));
        }
    }

    private int findCamera() {
        int cameraId = -1;
        // Search for the front facing camera
        int numberOfCameras = Camera.getNumberOfCameras();
        Log.d(DEBUG_TAG, "No of Cameras found"+numberOfCameras);
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            Log.d(DEBUG_TAG, "Camera found");
            cameraId = i;
            break;

        }
        Log.d("CAMERA", "findCamera: "+cameraId);
        return cameraId;
    }

    public void takePhoto(){
        if(camera == null) Log.v(DEBUG_TAG, "Camera object is null");
        else{
            SurfaceTexture st = new SurfaceTexture(MODE_PRIVATE);
            try{
                camera.setPreviewTexture(st);
                camera.startPreview();
                camera.takePicture(null, null,
                        new PhotoHandler(getApplicationContext()));
            }catch (Exception e){
                Log.d(TAG, "takePhoto: "+e);
            }
        }

    }

    public static String getImgurContent(String filePath) throws Exception {


/*
        URL url;
        url = new URL("https://api.imgur.com/3/image");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

//        String data = URLEncoder.encode("image", "UTF-8") + "="
//                + URLEncoder.encode(IMAGE_URL, "UTF-8");
        Bitmap bm = BitmapFactory.decodeFile(filePath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
        String data = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);


        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Client-ID " + clientID);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");

        conn.connect();
        StringBuilder stb = new StringBuilder();
        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
        wr.write(data);
        wr.flush();

        // Get the response
        BufferedReader rd = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            stb.append(line).append("\n");
        }
        wr.close();
        rd.close();


*/

        StringBuilder stb=new StringBuilder();
        String res=null;

        try{
            String charset = "UTF-8";
            File uploadFile1 = new File(filePath);
            String requestURL = croam_server_url+"/api/upload/";
            MultipartUtility multipart = new MultipartUtility(requestURL, charset);
            multipart.addFormField("name", "Test");
            multipart.addFormField("phone", "12345");
            multipart.addFormField("uid", "uiduid");
            multipart.addFormField("latitude", "11511");
            multipart.addFormField("longitude", "1234");
            multipart.addFormField("imagefolg", "12345");


            multipart.addFilePart("uploadedfile", uploadFile1);
            List<String> response = multipart.finish();

            Log.v("rht", "SERVER REPLIED:");

            for (String line : response) {
                Log.v("rht", "Line : "+line);
                stb.append(line).append("\n");
                Log.v("dd",line);

            }
        }

        catch (Exception e){
            System.out.println("Error in photohandling"+e);
        }




        return stb.toString();
    }

    public static void sendImageLink(String imageURL) {
        sendSMS("Image is at: " + imageURL);
    }

    static {
        System.loadLibrary("native-mfcc-lib");
    }

}

class MyBroadCastReciever extends BroadcastReceiver {
    CRoamService activity;

    public MyBroadCastReciever(CRoamService activity) {
        this.activity=activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
                Log.d("HELP_HARDWARE_BTN", "BroadcastReceiver :"+CRoamService.screenOff1+CRoamService.screenOff2+CRoamService.screenOn1+CRoamService.screenOn2);

        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            //DO HERE
            if(CRoamService.screenOff1==false){
                (new CountDownTimer(1500, 500) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }
                    @Override
                    public void onFinish() {
                        CRoamService.screenOff1=false;
                        CRoamService.screenOff2=false;
                        CRoamService.screenOn1=false;
                        CRoamService.screenOn2=false;
                    }
                }).start();

                CRoamService.screenOff1=true;
            }
            else if(CRoamService.screenOff1==true&&CRoamService.screenOff2==false){
                CRoamService.screenOff2=true;
                Log.d("HELP_HARDWARE_BTN", "screenOff: Help detected");
                (activity).onDetectingHelp();
            }

        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            //DO HERE
            if(CRoamService.screenOn1==false){
                (new CountDownTimer(1500, 500) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                    }
                    @Override
                    public void onFinish() {
                        CRoamService.screenOff1=false;
                        CRoamService.screenOff2=false;
                        CRoamService.screenOn1=false;
                        CRoamService.screenOn2=false;
                    }
                }).start();

                CRoamService.screenOn1=true;
            }
            else if(CRoamService.screenOn1==true&&CRoamService.screenOn2==false){
                CRoamService.screenOn2=true;

                Log.d("HELP_HARDWARE_BTN", "screenOn: Help detected");
                activity.onDetectingHelp();
            }
        }
    }
}