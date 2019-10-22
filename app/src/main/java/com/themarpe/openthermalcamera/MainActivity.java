package com.themarpe.openthermalcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.otaliastudios.cameraview.Audio;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Gesture;
import com.otaliastudios.cameraview.GestureAction;
import com.otaliastudios.cameraview.PictureResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.themarpe.openthermalcamera.Palette.ThermalPalette;

import com.themarpe.openthermalcamera.R;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    IRView irView;
    IRPicture irPicture = null;
    CameraView camera;

    ImageView imgTempSpectrum;

    ImageView imgInsertOtc = null;
    AnimationDrawable insertOtcAni = null;

    TextView textMinIrTemp;
    TextView textMaxIrTemp;
    TextView textAvgIrTemp;

    OTC otc = null;

    ArrayList<View> viewsToRotate = new ArrayList<>();

    LayoutRotateOnOrientation layoutRotateOnOrientation = null;


    @Override
    protected void onDestroy() {
        super.onDestroy();

        layoutRotateOnOrientation.disable();
        layoutRotateOnOrientation = null;

    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Get shared preferences (Settings)
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

        //Toast if runtime exception happened
        if(sharedPreferences.getBoolean("cameraview_crashed", false)){
            Toast.makeText(this, "CameraView error caught, disabled RGB overlay", Toast.LENGTH_SHORT).show();
            sharedPreferences.edit().remove("cameraview_crashed").apply();
        }

        //CAMERA
        camera = findViewById(R.id.camera);
        //first set cameraview enabled/disabled then adjust settings
        boolean overlay_enabled = sharedPreferences.getBoolean("overlay_enabled", false);
        setCameraViewEnabled(overlay_enabled);
        camera.setAudio(Audio.OFF);
        //camera.mapGesture(Gesture.PINCH, GestureAction.ZOOM); // Pinch to zoom!
        camera.mapGesture(Gesture.TAP, GestureAction.FOCUS_WITH_MARKER); // Tap to focus!
        camera.addCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(PictureResult pic) {
                super.onPictureTaken(pic);
                saveTakenPicture(takeIrPicture(), pic, layoutRotateOnOrientation.getCurrentOrientation());
            }
        });

        //This activity should keep the screen on!
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //request for permissions
        ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},1);

        //irView (extended ImageView)
        irView = findViewById(R.id.irView);
        irPicture = new IRPicture(OTC.IR_WIDTH, OTC.IR_HEIGHT);
        irView.setIRPicture(irPicture);

        //rotate layout listener
        layoutRotateOnOrientation = new LayoutRotateOnOrientation();

        //get min max avg temp labels
        textMinIrTemp = findViewById(R.id.txtTempMin);
        textMaxIrTemp = findViewById(R.id.txtTempMax);
        textAvgIrTemp = findViewById(R.id.txtTempMid);

        // get imgTempSpectrum
        imgTempSpectrum = findViewById(R.id.imgTempSpectrum);
        imgTempSpectrum.setImageBitmap(irPicture.getSpectrumBitmap());

        //get all views to rotate
        getAllRotatableViews(viewsToRotate, (ViewGroup) findViewById(R.id.layoutActivityMain));

        // insert otc animation
        imgInsertOtc = findViewById(R.id.imgInsertOtc);
        insertOtcAni = (AnimationDrawable) imgInsertOtc.getDrawable();
        insertOtcAni.setVisible(false, true);

        //OTC
        otc = new OTC(this, new OTCStateListener());
        otc.setResponseListener(new OTC.ResponseListener(){

            @Override
            public void onResponsePre(Protocol.RspStruct rsp) {
                //do nothing
            }

            @Override
            public void onResponsePost(Protocol.RspStruct rsp) {
                if(rsp.responseCode == Protocol.RSP_GET_FRAME_DATA){

                    //set temperatures and irView
                    irPicture.updateTemperatureData(otc.getIrTemp());
                    irView.update();

                    //new min max avg temps available, update
                    DecimalFormat df = new DecimalFormat("#.0");
                    textMinIrTemp.setText(df.format(otc.getMinIrTemp()));
                    textMaxIrTemp.setText(df.format(otc.getMaxIrTemp()));
                    textAvgIrTemp.setText(df.format((otc.getMaxIrTemp() + otc.getMinIrTemp()) / 2.0 ));

                    //Display temp spectrum according to template
                    imgTempSpectrum.setImageBitmap(irView.getIRPicture().getSpectrumBitmap());
                    imgTempSpectrum.invalidate();

                }
            }

        });


        //take picture button
        findViewById(R.id.btnTakePicture).setOnClickListener((View v) -> {

                //flash animation
                irView.startFlashAnimation();

                //check if permissions
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Permission is not granted
                    Toast.makeText(MainActivity.this, "No permission to take picture", Toast.LENGTH_SHORT);
                    return;
                }

                //Check if RGB+IR or IR only
                if(sharedPreferences.getBoolean("overlay_enabled", false)){
                    //RGB+IR
                    camera.takePictureSnapshot();
                } else {
                    saveTakenPicture(takeIrPicture(), null, layoutRotateOnOrientation.getCurrentOrientation());
                }

        });

        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
            }
        });

        findViewById(R.id.btnGallery).setOnClickListener((View v) -> {
            Intent galleryIntent = new Intent(MainActivity.this, GalleryActivity.class);
            startActivity(galleryIntent);
        });

    }


    void setCameraViewEnabled(boolean enabled){

        if(enabled){
            camera.setLifecycleOwner(this);
            camera.setVisibility(View.VISIBLE);
        } else {
            camera.setVisibility(View.GONE);
        }
    }


    IRPicture takeIrPicture(){
        return new IRPicture(irView.getIRPicture());
    }

    void saveTakenPicture(IRPicture irPicture, PictureResult rgbPictureResult, int orientation){

        try {

            Bitmap fusedImage = Bitmap.createBitmap(irView.getWidth(), irView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas temporaryCanvas = new Canvas(fusedImage);
            Paint rgbPaint = new Paint();
            Paint irPaint = new Paint();
            irPaint.setAlpha(irView.getImageAlpha());
            Matrix irTransformationMatrix = irView.getIrViewMatrix();
            Matrix rgbTransformationMatrix = new Matrix();

            JSONObject otc = new JSONObject();

            otc.put("orientation", orientation);

            otc.put("fusion", false);
            if (rgbPictureResult != null) {

                //prepare and correctly rotate RGB image

                //fuse with IR image
                Bitmap rgbBitmap = BitmapFactory.decodeByteArray(rgbPictureResult.getData(), 0, rgbPictureResult.getData().length);

                //rotate accordingly to rotation
                //portrait
                int finalRotationInDegrees = 0;

                float w = rgbBitmap.getWidth();
                float h = rgbBitmap.getHeight();

                //not needed (snapshot is of same size as irView)
                float sx = 1.0f;
                float sy = 1.0f;

                //then rotate in middle
                //rgbTransformationMatrix.postTranslate(-w/2.0f, -h/2.0f);
                //if width > height -> landscape
                // else portrait
                if(w > h){
                    //scale
                    rgbTransformationMatrix.preScale(sy, sx,0,0);
                    rgbTransformationMatrix.postRotate(360 - orientation, w/2.0f,h/2.0f);
                } else {
                    //scale
                    rgbTransformationMatrix.preScale(sx, sy,0,0);
                    rgbTransformationMatrix.postRotate(orientation, w/2.0f,h/2.0f);
                }
                rgbTransformationMatrix.postTranslate((fusedImage.getWidth() - w) / 2.0f , (fusedImage.getHeight() - h)/2.0f);


                //paint
                temporaryCanvas.drawBitmap(rgbBitmap, rgbTransformationMatrix, rgbPaint);

                Log.d(TAG, "Orientation = " + orientation + " rgbPictureResult.size() = " + rgbPictureResult.getSize() +" rgbPictureResult.getRotation() = " + rgbPictureResult.getRotation() + ", rgbBitmap w,h = " + rgbBitmap.getWidth() + "," + rgbBitmap.getHeight());

                ByteArrayOutputStream rgbJpegCompressed = new ByteArrayOutputStream();
                fusedImage.compress(Bitmap.CompressFormat.JPEG, 90, rgbJpegCompressed);

                //save to JSON
                otc.put("fusion", true);

                JSONObject rgbPicture = new JSONObject();
                rgbPicture.put("width", rgbPictureResult.getSize().getWidth());
                rgbPicture.put("height", rgbPictureResult.getSize().getHeight());

                rgbPicture.put("format", "jpg:base64");
                rgbPicture.put("data", Base64.encodeToString(rgbJpegCompressed.toByteArray(), Base64.NO_WRAP));

                otc.put("rgb_picture", rgbPicture);

            }

            if (irPicture != null) {
                JSONObject ir = new JSONObject();

                ir.put("width", irPicture.getWidth());
                ir.put("height", irPicture.getHeight());
                ir.put("temperature", new JSONArray(irPicture.getTemperatureData()));
                ir.put("dynamic_range", irPicture.dynamicRange);
                ir.put("custom_max_temperature", irPicture.customMaxTemperature);
                ir.put("custom_min_temperature", irPicture.customMinTemperature);
                ir.put("custom_temp_range", irPicture.customTemperatureRange);
                ir.put("thermal_palette", irPicture.getThermalPalette().getClass().getName());

                ByteArrayOutputStream pngCompressed = new ByteArrayOutputStream();
                irPicture.getBitmap().compress(Bitmap.CompressFormat.PNG, 100, pngCompressed);
                ir.put("format", "png:base64");
                ir.put("data", Base64.encodeToString(pngCompressed.toByteArray(), Base64.NO_WRAP));

                if(irView.filterBitmap){
                    temporaryCanvas.setDrawFilter(irView.filterPaint);
                } else {
                    temporaryCanvas.setDrawFilter(irView.noFilterPaint);
                }
                temporaryCanvas.drawBitmap(irPicture.getBitmap(), irTransformationMatrix, irPaint);

                otc.put("ir_picture", ir);

            }

            //rotate final image according to orientation parameter (portrait / landscape)
            Bitmap targetBitmap;
            if(orientation == 90 || orientation == 270){
                targetBitmap = Bitmap.createBitmap(irView.getHeight(), irView.getWidth(), Bitmap.Config.ARGB_8888);
            } else {
                targetBitmap = Bitmap.createBitmap(irView.getWidth(), irView.getHeight(), Bitmap.Config.ARGB_8888);
            }
            Canvas targetCanvas = new Canvas(targetBitmap);

            Matrix finalTransformation = new Matrix();
            //then rotate in middle
            finalTransformation.postRotate(orientation, fusedImage.getWidth()/2.0f,fusedImage.getHeight()/2.0f);
            finalTransformation.postTranslate((targetBitmap.getWidth() - fusedImage.getWidth())/2.0f, (targetBitmap.getHeight() - fusedImage.getHeight())/2.0f);

            targetCanvas.drawBitmap(fusedImage, finalTransformation, new Paint());


            //TODO saving pictures


            //get timestamp
            Date timestamp = Calendar.getInstance().getTime();
            SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

            File folderToSave = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "/OpenThermalCamera/");
            //create dir if it doesn't exist
            folderToSave.mkdirs();


            try{

                File targetBitmapFile = new File(folderToSave, "IMG_" + tsFormat.format(timestamp) + ".jpg");
                targetBitmapFile.setWritable(true);
                FileOutputStream fo = new FileOutputStream(targetBitmapFile);

                //save targetBitmap as png
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fo);

                fo.close();

                Log.d(TAG, "Saved targetBitmap as: " + targetBitmapFile.getAbsolutePath());

            } catch (IOException ioException){
                Log.d(TAG, "IOException while trying to write target bitmap: " + ioException.getLocalizedMessage());
            }

            try{
                File otcFile = new File(folderToSave, "IMG_" + tsFormat.format(timestamp) + ".otc");
                otcFile.setWritable(true);
                FileOutputStream fos = new FileOutputStream(otcFile);
                OutputStreamWriter out = new OutputStreamWriter(fos);
                out.write(otc.toString(4));

                out.close();
                fos.close();

                Log.d(TAG, "Saved otc file as: " + otcFile.getAbsolutePath());

            } catch (IOException ioException){
                Log.d(TAG, "IOException while trying to write otc file: " + ioException.getLocalizedMessage());
            }

            //notify to scan the folder
            //media scanner connection
            MediaScannerConnection.scanFile(MainActivity.this, new String[]{folderToSave.getAbsolutePath()}, null, null);

        } catch (JSONException jsonException){
            Log.d(TAG, "JSONException: " + jsonException.getMessage());
        }
    }

    class OTCStateListener implements OTC.StateListener {

        @Override
        public void onStateChanged(OTC.OTCState otcState, OTC.UsbState usbState) {
            Log.d("MainActivity", "State changed: otc = " + otcState.name() + ", usb = " + usbState.name());
            //start animation if OTC is unplugged
            if(usbState == OTC.UsbState.CONNECTED){
                stopOtcInsertAnimation();
            } else {
                startOtcInsertAnimation();
            }

            if(otcState == OTC.OTCState.READY){
                // OTC is ready
                stopOtcInsertAnimation();
                onResume();
            }
        }
    }

    void startOtcInsertAnimation() {
        //usb disconnected
        // hide IR and RGB views
        // display animation
        // disable picture button

        camera.setVisibility(View.GONE);
        irView.setVisibility(View.GONE);

        insertOtcAni.setVisible(true, true);
        imgInsertOtc.setVisibility(View.VISIBLE);

        // Start the animation (looped playback by default).
        insertOtcAni.start();

        //disable picture button
        findViewById(R.id.btnTakePicture).setAlpha(0.2f);
        findViewById(R.id.btnTakePicture).setEnabled(false);
    }

    void stopOtcInsertAnimation(){

        insertOtcAni.setVisible(false, true);
        imgInsertOtc.setVisibility(View.INVISIBLE);

        //bring back IR and RGB views
        //Will be brought back after OTC initializes and calls onResume

        //ReEnable take picture button
        findViewById(R.id.btnTakePicture).setAlpha(1.0f);
        findViewById(R.id.btnTakePicture).setEnabled(true);

    }


    @Override
    protected void onStart() {
        super.onStart();
        //start usb service (if not started)
        otc.startUsbService();

    }

    @Override
    protected void onStop() {
        super.onStop();

        //stop usb service
        otc.stopUsbService();
    }



    @Override
    protected void onResume() {
        super.onResume();

        //Layout rotation listener
        layoutRotateOnOrientation.enable();

        //OTC continue
        otc.resumeOTC();
        //update otc with ?new? settings
        otc.sendSettings(otc.getSettingsFromSharedPreferences());

        //check all settings
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean overlay_enabled = sharedPreferences.getBoolean("overlay_enabled", false);
        boolean filter_enabled = sharedPreferences.getBoolean("filter_enabled", false);

        int emissivityPercent = 90;
        try {
            emissivityPercent = sharedPreferences.getInt("emissivity", 90);
        } catch(Exception ex){
            try {
                emissivityPercent = Integer.parseInt(sharedPreferences.getString("emissivity", "90"));
            }catch (Exception ex1){}
        }
        double emissivity = emissivityPercent / 100.0; //percent to absolute
        //set emissivity
        otc.setEmissivity(emissivity);

        //set current palette
        ThermalPalette selectedThermalPalette = ThermalPalette.getCurrentSelectedPalette(this);
        Log.d(TAG, "Setting thermal palette: " + selectedThermalPalette.toString());
        irView.getIRPicture().setThermalPalette(selectedThermalPalette);

        //set dynamic range
        boolean dynamic_range_enabled = sharedPreferences.getBoolean("dynamic_range_enabled", false);
        Log.d(TAG, "Dynamic range enabled: " + dynamic_range_enabled);
        irView.getIRPicture().setDynamicRange(dynamic_range_enabled);

        //set dynamic range min difference
        float dynamic_range_min_difference = sharedPreferences.getInt("dynamic_range_min_difference", 0);
        Log.d(TAG, "Dynamic range min difference: " + dynamic_range_min_difference);
        irView.getIRPicture().setDynamicRangeMinDifference(dynamic_range_min_difference);

        //set custom range
        irView.getIRPicture().setCustomTemperatureRange(sharedPreferences.getBoolean("custom_range_enabled", false));
        irView.getIRPicture().setCustomMinTemperature(sharedPreferences.getInt("custom_range_min", -5));
        irView.getIRPicture().setCustomMaxTemperature(sharedPreferences.getInt("custom_range_max", 50));

        //enabled/disable max temp marker
        boolean maxTempMarkerEnabled = sharedPreferences.getBoolean("max_temperature_marker_enabled", false);
        irView.setMaxMarkerEnabled(maxTempMarkerEnabled);


        //searchArea enabled/disabled and searchAreaSize
        irView.setSearchAreaEnabled(sharedPreferences.getBoolean("search_area_enabled", false));
        int searchAreaSize = sharedPreferences.getInt("search_area_size", 3);
        irView.setSearchAreaSize(searchAreaSize);
        irPicture.setSeachAreaSize(searchAreaSize);

        //act on settings:
        //enable / disable camera
        setCameraViewEnabled(overlay_enabled);
        //if overlay not enabled, hide camera layout
        Log.d(TAG, "overlay_enabled: " + overlay_enabled);
        if(!overlay_enabled){
            camera.setVisibility(View.GONE);
            irView.setVisibility(View.VISIBLE);
            irView.setImageAlpha(255);
        } else {
            //read overlay alpha value
            camera.setVisibility(View.VISIBLE);
            irView.setVisibility(View.VISIBLE);
            irView.setImageAlpha(sharedPreferences.getInt("ir_alpha_value", 150));
        }

        //filter bitmap?
        irView.setImageFilter(filter_enabled);


        // check USB state and if USB not connected, display "insert OTC" animation
        //debug
        Log.d(TAG, "USB state: " + otc.getUsbState() + ", OTC state: " + otc.getOTCState());
        if(otc.getUsbState() == OTC.UsbState.DISCONNECTED && otc.getOTCState() == OTC.OTCState.NOT_READY){
            startOtcInsertAnimation();
        }
        if(otc.getUsbState() == OTC.UsbState.CONNECTED && otc.getOTCState() == OTC.OTCState.READY) {
            stopOtcInsertAnimation();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        layoutRotateOnOrientation.disable();

        otc.pauseOTC();

    }

    /* Orientation change, rotate all icons, texts and buttons */

    private int getRotationInDegrees(int rotationConstant){
        int currentRotationInDegrees = 0;
        switch(rotationConstant){
            case Surface.ROTATION_0 :
                currentRotationInDegrees = 0;
                break;

            case Surface.ROTATION_90:
                currentRotationInDegrees = 90;
                break;

            case Surface.ROTATION_180:
                currentRotationInDegrees = 180;
                break;

            case Surface.ROTATION_270:
                currentRotationInDegrees = 270;
                break;
        }

        return currentRotationInDegrees;
    }


    public void getAllRotatableViews(ArrayList<View> toAdd, ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            if (child instanceof ViewGroup) {
                getAllRotatableViews(toAdd, (ViewGroup) child);
            } else {
                if (child != null) {
                    // DO SOMETHING WITH VIEW
                    if(child instanceof ImageButton || child instanceof TextView){
                        toAdd.add(child);
                    }
                }
            }
        }
    }


    private class LayoutRotateOnOrientation extends OrientationEventListener {


        public int getCurrentOrientation(){
            return current_orientation;
        }

        private int previousRotationInDegrees = 0;
        private int current_orientation = 0;
        private int diff = 0;

        Display display = null;
        LayoutRotateOnOrientation(){
            super(MainActivity.this, SensorManager.SENSOR_DELAY_UI);
            display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        }

        public void uiRotate(int angle){

            for (View v : viewsToRotate) {

                RotateAnimation rotate = new RotateAnimation(previousRotationInDegrees, angle,
                        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                        0.5f);

                rotate.setDuration(1000);
                rotate.setRepeatCount(0);
                rotate.setInterpolator(new LinearInterpolator());
                rotate.setFillAfter(true);

                v.startAnimation(rotate);
            }

            previousRotationInDegrees = angle;

        }



        @Override
        public void onOrientationChanged(int orientation) {

            //Algorithm adopted from Open Camera project by Mark Harman

            if( orientation != OrientationEventListener.ORIENTATION_UNKNOWN ) {

                diff = Math.abs(orientation - current_orientation);
                if( diff > 180 ) {
                    diff = 360 - diff;
                }

                // threshold
                if( diff > 60 ) {
                    orientation = ((orientation + 45) / 90 * 90) % 360;

                    if( orientation != current_orientation ) {
                        current_orientation = orientation;

                        Log.d(TAG, "current_orientation is now: " + current_orientation);

                        int rotation = MainActivity.this.getWindowManager().getDefaultDisplay().getRotation();
                        int degrees = 0;
                        switch (rotation) {
                            case Surface.ROTATION_0: degrees = 0; break;
                            case Surface.ROTATION_90: degrees = 90; break;
                            case Surface.ROTATION_180: degrees = 180; break;
                            case Surface.ROTATION_270: degrees = 270; break;
                            default:
                                break;
                        }

                        int relative_orientation = (current_orientation + degrees) % 360;

                        Log.d(TAG, "    current_orientation = " + current_orientation);
                        Log.d(TAG, "    degrees = " + degrees);
                        Log.d(TAG, "    relative_orientation = " + relative_orientation);

                        final int ui_rotation = (360 - relative_orientation) % 360;
                        uiRotate(ui_rotation);

                    }
                }

            }

        }
    }



}
