package com.themarpe.openthermalcamera;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.themarpe.openthermalcamera.BuildConfig;
import com.themarpe.openthermalcamera.R;


public class GalleryActivity extends AppCompatActivity {

    private static final String TAG = "GalleryActivity";
    
    /*
        This is going to be a horizontal scroll list
        When scrolling to a next item, the now missing rightmost item will be buffered
        The list of items is going to be scanned from sdcard/pictures/openthermalcamera
     */
    SimpleDateFormat filenameDateFormat;
    ImageAdapter imageAdapter;
    RecyclerView recyclerView;
    File folderToScan;
    FileFilter imageFilter;
    File[] imageFiles = new File[0];
    TextView txtDatetime;
    int currentSnapPosition = 0;
    RelativeLayout noPicturesFound;
    FrameLayout frameLayout;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        //get framelayout
        frameLayout = findViewById(R.id.frameLayout);

        //get layoutNoPicturesFound
        noPicturesFound = findViewById(R.id.layoutNoPicturesFound);

        txtDatetime = findViewById(R.id.txtDatetime);
        imageFilter = new ImageFileFilter();
        folderToScan = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "/OpenThermalCamera");


        //get recycler view
        //create N item horizontal scroll list
        imageAdapter = new ImageAdapter();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setAdapter(imageAdapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);

        PagerSnapHelper pagerSnapHelper = new PagerSnapHelper();
        pagerSnapHelper.attachToRecyclerView(recyclerView);

        filenameDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

        recyclerView.addOnScrollListener(new SnapOnScrollListener(pagerSnapHelper, SnapOnScrollListener.Behavior.NOTIFY_ON_SCROLL_STATE_IDLE, position -> {
            //position has changed
            //set text to timestamp
            setDatetime(position);
            currentSnapPosition = position;
        }));

        recyclerView.setLayoutManager(layoutManager);

        //back button
        findViewById(R.id.btnBack).setOnClickListener((View v) -> finish());

        //delete button
        findViewById(R.id.btnDelete).setOnClickListener((View v) -> deleteCurrentImage());

        //share button
        findViewById(R.id.btnShare).setOnClickListener((View v) -> {

            final Uri theUri = FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    imageFiles[currentSnapPosition]);

            Intent shareIntent = ShareCompat.IntentBuilder.from(this)
                    .setType("image/png")
                    .setStream(theUri)
                    .getIntent();
            shareIntent.setData(theUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share image:"));

        });

        checkIfAnyImagesAvailable();

    }

    private void deleteCurrentImage(){
        new AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Are you sure to delete this image?")
            .setPositiveButton("Delete", (DialogInterface di, int which) -> {
                //delete currentSnapPosition image
                //get File
                boolean wasImgFileDeleted = false;
                try {
                    //get otc file aswell
                    String otcFilename = imageFiles[currentSnapPosition].getName().split("\\.")[0] + ".otc";

                    File parent = imageFiles[currentSnapPosition].getParentFile();
                    File otcFile = new File(parent, otcFilename);
                    File imgFile = imageFiles[currentSnapPosition];

                    //try and delete otc and img files
                    otcFile.delete();
                    wasImgFileDeleted = imgFile.delete();

                } catch (Exception ex){
                    Toast.makeText(this, "Unexpected error while deleting image", Toast.LENGTH_SHORT);
                    Log.d(TAG, "Error while trying to delete img and otc files: " + ex.getLocalizedMessage());
                }

                //notify imageadapter of this change
                if(wasImgFileDeleted){
                    imageAdapter.notifyItemRemoved(currentSnapPosition);
                }

                di.dismiss();
            })
            .setNegativeButton("Cancel", (DialogInterface di, int which) -> {
                di.dismiss();
            })
            .create().show();
    }

    private void setDatetime(int position){
        Log.d(TAG, "Setting datetime of position: " + position);
        if(position != RecyclerView.NO_POSITION && imageFiles != null && position < imageFiles.length ){
            //try to parse the timestamp
            try {
                String filename = imageFiles[position].getName();
                filename = filename.replaceFirst("IMG_", "");
                String datetime = filename.split("\\.")[0];

                Date date = filenameDateFormat.parse(datetime);

                SimpleDateFormat humanFrendlyDatetimeFormat = new SimpleDateFormat("HH:mm MMMM dd, yyyy", Locale.getDefault());
                String displayDatetime = humanFrendlyDatetimeFormat.format(date);

                txtDatetime.setText(displayDatetime);

            } catch (Exception a){
                txtDatetime.setText("Date and time unknown.");
                Log.d(TAG, "Exception when parsing datetime: " + a.getLocalizedMessage());
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(folderToScan.isDirectory()){
            File[] tmp = folderToScan.listFiles(imageFilter);
            //compare if change has happened and notify recyclerview
            if(!Arrays.equals(tmp, imageFiles)){
                imageFiles = tmp;

                //sort images by datetime (newest first)
                Arrays.sort(imageFiles, (File a, File b) -> b.getName().compareTo(a.getName()));

                imageAdapter.notifyDataSetChanged();
            }
        }

        checkIfAnyImagesAvailable();

        setDatetime(0);

    }

    private void checkIfAnyImagesAvailable(){
        //check if dataset is empty
        if(imageFiles.length == 0){
            noPicturesFound.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            frameLayout.bringChildToFront(noPicturesFound);

            //disable all buttons

            findViewById(R.id.btnInspect).setAlpha(0.2f);
            findViewById(R.id.btnInspect).setEnabled(false);
            findViewById(R.id.btnShare).setAlpha(0.2f);
            findViewById(R.id.btnShare).setEnabled(false);
            findViewById(R.id.btnDelete).setAlpha(0.2f);
            findViewById(R.id.btnDelete).setEnabled(false);

        } else {
            noPicturesFound.setVisibility(View.INVISIBLE);
            recyclerView.setVisibility(View.VISIBLE);
            frameLayout.bringChildToFront(recyclerView);

            //enable all buttons
            findViewById(R.id.btnInspect).setAlpha(1.0f);
            findViewById(R.id.btnInspect).setEnabled(true);
            findViewById(R.id.btnShare).setAlpha(1.0f);
            findViewById(R.id.btnShare).setEnabled(true);
            findViewById(R.id.btnDelete).setAlpha(1.0f);
            findViewById(R.id.btnDelete).setEnabled(true);

        }

    }


    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder>{

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout parentLayout = new LinearLayout(GalleryActivity.this);

            ImageView imageView = new ImageView(GalleryActivity.this);

            //create image of same size as recycler view
            View recyclerView = findViewById(R.id.recyclerView);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setTag("image");

            //give it margin
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(recyclerView.getWidth(), recyclerView.getHeight());
            layoutParams.setMargins(20,0,20,0);

            parentLayout.addView(imageView, layoutParams);

            return new ViewHolder(parentLayout);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            //load the image
            holder.image.setImageBitmap(BitmapFactory.decodeFile(imageFiles[position].getAbsolutePath()));
        }

        @Override
        public int getItemCount() {
            return imageFiles.length;
        }

        class ViewHolder extends RecyclerView.ViewHolder{
            private ImageView image;
            public ViewHolder(View linearLayoutView) {
                super(linearLayoutView);
                image = (ImageView) linearLayoutView.findViewWithTag("image");
            }
        }
    }



}
