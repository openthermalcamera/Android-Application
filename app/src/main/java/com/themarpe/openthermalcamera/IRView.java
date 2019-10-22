package com.themarpe.openthermalcamera;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.widget.AppCompatImageView;

public class IRView extends AppCompatImageView {

    private static final String TAG = "IRView";


    //take a picture effect
    Rect effectRectangle;
    final int effectDurationMillisecond = 300;
    int effectFlashColor = Color.argb(0, 0, 0, 0);
    Paint effectPaint = new Paint();
    ObjectAnimator effectFlashAnimation;


    //SearchArea
    private boolean searchAreaEnabled=true;
    public void setSearchAreaEnabled(boolean enabled){
        searchAreaEnabled = enabled;
    }
    private Paint searchAreaPaint;
    private int searchAreaSize=3;
    public void setSearchAreaSize(int size){searchAreaSize=size;}


    private float maxMarkerScale = 0.05f;
    private Paint maxMarkerPaint;
    private boolean maxMarkerEnabled = false;
    private PointF maxTemperaturePixelIndex = new PointF(0,0);

    private Matrix irViewMatrix = new Matrix();
    private IRPicture irPicture = null;

    float[] xyMarkerVector = new float[]{0.5f, 0.5f};

    public Matrix getIrViewMatrix(){
        return irViewMatrix;
    }

    public void setIRPicture(IRPicture irPicture){
        this.irPicture = irPicture;
    }

    public IRPicture getIRPicture(){
        return irPicture;
    }

    public void setMaxTemperaturePixelIndex(PointF pixel){
        maxTemperaturePixelIndex = pixel;
    }

    public void setMaxMarkerEnabled(boolean enabled){
        maxMarkerEnabled = enabled;
    }


    boolean filterBitmap = false;
    PaintFlagsDrawFilter filterPaint = new PaintFlagsDrawFilter(0, Paint.FILTER_BITMAP_FLAG);
    PaintFlagsDrawFilter noFilterPaint = new PaintFlagsDrawFilter(Paint.FILTER_BITMAP_FLAG, 0);

    public void setImageFilter(boolean filter){
        filterBitmap = filter;
    }

    public boolean getImageFilter(){
        return filterBitmap;
    }

    public IRView(Context context) {
        super(context);
        init();
        Log.d(TAG, "maxMarkerPaint is initialized");
    }

    public IRView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        Log.d(TAG, "maxMarkerPaint is initialized");
    }

    public IRView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
        Log.d(TAG, "maxMarkerPaint is initialized, height is "+getHeight());
    }
    private void init(){
        maxMarkerPaint = new Paint();
        maxMarkerPaint.setStrokeWidth(2.0f);
        maxMarkerPaint.setColor(Color.BLACK);

        searchAreaPaint = new Paint();
        searchAreaPaint.setStrokeWidth(2.0f);
        searchAreaPaint.setColor(Color.BLACK);
        searchAreaPaint.setStyle(Paint.Style.STROKE);


        effectRectangle = new Rect(0,0, getWidth(), getHeight());
        effectPaint.setColor(effectFlashColor);
        effectPaint.setAlpha(0);

        effectFlashAnimation = ObjectAnimator.ofInt(effectPaint,"alpha", 0, 180, 0);
        effectFlashAnimation.setDuration(effectDurationMillisecond);
        effectFlashAnimation.addUpdateListener((ValueAnimator ani) -> invalidate());

    }


    public void update() {

        if(irPicture == null) return;

        setImageBitmap(irPicture.getBitmap());

        //get max temp
        if(!searchAreaEnabled){ //Display max temp from IR picture
            xyMarkerVector[0] = irPicture.getMaxTemperaturePixel().x;
            xyMarkerVector[1] = irPicture.getMaxTemperaturePixel().y;
        }
        else{ //Display max temp from IR picture INSIDE the searchArea!
            xyMarkerVector[0] = irPicture.getMaxTemperaturePixelInSearchArea().x;
            xyMarkerVector[1] = irPicture.getMaxTemperaturePixelInSearchArea().y;
        }

        //move to middle of the pixel
        xyMarkerVector[0] += 0.5f;
        xyMarkerVector[1] += 0.5f;

        //rotate
        //rotate and scale image and marker
        irViewMatrix.reset();

        //rotate
        irViewMatrix.preRotate(90f, 0, 0);
        //move
        irViewMatrix.postTranslate(OTC.IR_HEIGHT, 0);
        //scale
        irViewMatrix.postScale(((float) getWidth())/OTC.IR_HEIGHT, ((float) getHeight()) / OTC.IR_WIDTH, 0,0);

        //apply to marker
        irViewMatrix.mapPoints(xyMarkerVector);

        setScaleType(ImageView.ScaleType.MATRIX);   //required
        //apply to ir image
        setImageMatrix(irViewMatrix);

        //new frame available, invalidate irView
        invalidate();
    }



    public void startFlashAnimation(){
        effectRectangle.set(0,0, getWidth(), getHeight());
        effectFlashAnimation.end();
        effectFlashAnimation.start();
    }

    private boolean isFlashAnimationInProgress(){
        if(effectFlashAnimation == null){
            return false;
        } else {
            return effectFlashAnimation.isRunning();
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {

        if(filterBitmap){
            canvas.setDrawFilter(filterPaint);
        } else {
            canvas.setDrawFilter(noFilterPaint);
        }

        super.onDraw(canvas);

        canvas.setDrawFilter(null);

        if(maxMarkerEnabled) {

            float markerSize = getHeight() * maxMarkerScale;
            float x = xyMarkerVector[0];
            float y = xyMarkerVector[1];

            //draw max temp pointer
            canvas.drawLine(x, y - markerSize, x, y - markerSize / 4.0f, maxMarkerPaint);
            canvas.drawLine(x, y + markerSize, x, y + markerSize / 4.0f, maxMarkerPaint);
            canvas.drawLine(x - markerSize, y, x - markerSize / 4.0f, y, maxMarkerPaint);
            canvas.drawLine(x + markerSize, y, x + markerSize / 4.0f, y, maxMarkerPaint);

        }
        if(searchAreaEnabled){
            float yy = getHeight()/ OTC.IR_WIDTH;
            float xx = getWidth()/OTC.IR_HEIGHT;
            canvas.drawRect(xx*(OTC.IR_HEIGHT/2-searchAreaSize),yy*(OTC.IR_WIDTH/2-searchAreaSize),xx*(OTC.IR_HEIGHT/2+searchAreaSize), yy*(OTC.IR_WIDTH/2+searchAreaSize), searchAreaPaint);
        }

        //take a picture effect
        if(isFlashAnimationInProgress()){
            canvas.drawRect(effectRectangle, effectPaint);
        }

    }
}
