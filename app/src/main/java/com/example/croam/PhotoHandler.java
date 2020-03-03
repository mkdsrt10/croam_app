package com.example.croam;

import android.content.Context;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PhotoHandler implements Camera.PictureCallback {

    // private static final String LOG_TAG = "ImageInBackground";
    private final Context context;

    public PhotoHandler(Context context) {
        this.context = context;

    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        File pictureFileDir = getDir();

        if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {

            Log.d(MainActivity.DEBUG_TAG, "Can't create directory to save image.");
            Toast.makeText(context, "Can't create directory to save image.",
                    Toast.LENGTH_LONG).show();
            return;

        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyymmddhhmmss");
        String date = dateFormat.format(new Date());
        String photoFile = "Picture_" + date + ".jpg";

        final String filename = pictureFileDir.getPath() + File.separator + photoFile;
        File pictureFile = new File(filename);
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(data);
            fos.close();
            Toast.makeText(context, "New Image saved:" + photoFile,
                    Toast.LENGTH_LONG).show();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String imageURL = "";
                    try {
                        System.out.println("Photohandler1");
                        String response = CRoamService.getImgurContent(filename);
                        System.out.println("Photohandler2");
                        JSONObject json = new JSONObject(response);
                        imageURL = json.getJSONObject("data").getString("link");

                        Log.v(MainActivity.DEBUG_TAG, "Image URL - " + imageURL);
                    } catch (Exception e) {
                        Log.e(MainActivity.DEBUG_TAG, "Error occured while uploading image -- " + e.getMessage());
                    }

                    if(!imageURL.isEmpty()) {
                        CRoamService.sendImageLink(imageURL);
                    }
                }
            }).start();

        } catch (Exception error) {
            Log.d(MainActivity.DEBUG_TAG, "File" + filename + "not saved: "
                    + error.getMessage());
            Toast.makeText(context, "Image could not be saved.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private File getDir() {
        File sdDir = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(sdDir, "CRoam");
    }


}