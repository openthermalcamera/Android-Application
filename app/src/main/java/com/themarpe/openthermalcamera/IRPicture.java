package com.themarpe.openthermalcamera;

import android.graphics.Bitmap;
import android.graphics.Point;

import com.themarpe.openthermalcamera.Palette.RainbowPalette;
import com.themarpe.openthermalcamera.Palette.ThermalPalette;

public class IRPicture {

    private ThermalPalette thermalPalette;
    private double[][] tempData;
    Bitmap irBitmap;


    private static final int SPECTRUM_RESOLUTION = 256;
    Bitmap spectrumBitmap;

    protected boolean customTemperatureRange = false;

    protected double customMinTemperature;
    protected double customMaxTemperature;

    private int width, height;
    private double minTemp, maxTemp, maxTempSearchArea;
    private int searchAreaSize=3;
    public void setSeachAreaSize(int size){searchAreaSize=size;}

    protected boolean dynamicRange;
    protected float dynamicRangeMinDifference = 0;

    private Point maxTempPixel = new Point(0,0);
    private Point maxTempPixelInSearchArea = new Point(0,0);

    IRPicture(IRPicture toCopy){

        this(toCopy.width, toCopy.height);
        tempData = toCopy.tempData.clone();
        thermalPalette = toCopy.thermalPalette;
        irBitmap = Bitmap.createBitmap(toCopy.irBitmap);
        customMaxTemperature = toCopy.customMaxTemperature;
        customMinTemperature = toCopy.customMinTemperature;
        minTemp = toCopy.minTemp;
        maxTemp = toCopy.maxTemp;
        dynamicRange = toCopy.dynamicRange;
        dynamicRangeMinDifference = toCopy.dynamicRangeMinDifference;
        maxTempPixel = new Point(maxTempPixel);

        spectrumBitmap = Bitmap.createBitmap(toCopy.spectrumBitmap);

    }

    IRPicture(int width, int height){
        this(width, height, new RainbowPalette());
    }

    IRPicture(int width, int height, ThermalPalette thermalPalette){

        this.width = width;
        this.height = height;
        tempData = new double[height][width];
        this.thermalPalette = thermalPalette;
        irBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        spectrumBitmap = Bitmap.createBitmap(SPECTRUM_RESOLUTION, 1, Bitmap.Config.ARGB_8888);
    }

    public void updateTemperatureData(double[][] temperatureData){
        minTemp = temperatureData[0][0];
        maxTemp = temperatureData[0][0];

        maxTempPixel.set(0,0); //TODO: why???
        for(int i = 0; i<temperatureData.length; i++){
            for(int j = 0; j<temperatureData[i].length; j++){

                if(temperatureData[i][j] < minTemp){
                    minTemp = temperatureData[i][j];
                }
                if(temperatureData[i][j] > maxTemp){
                    maxTemp = temperatureData[i][j];
                    maxTempPixel.set(j, i);
                }
            }
        }
        tempData = temperatureData;

        //Find max temp pixel in searchArea
        maxTempSearchArea=-40;
        int xMid = temperatureData.length/2;
        for(int i = xMid-searchAreaSize; i< xMid + searchAreaSize; i++){
            int yMid = temperatureData[i].length/2;

            for(int j = yMid-searchAreaSize; j<yMid+searchAreaSize; j++){

                if(temperatureData[i][j] > maxTempSearchArea){
                    maxTempSearchArea = temperatureData[i][j];
                    maxTempPixelInSearchArea.set(j, i);
                }
            }
        }

        // min difference setting
        double tmpMinTemp = minTemp, tmpMaxTemp = maxTemp;
        double avgTemp = (minTemp+maxTemp) / 2.0;
        if(dynamicRangeMinDifference > 0 && dynamicRangeMinDifference > (maxTemp - minTemp) ){
            tmpMinTemp = avgTemp - (dynamicRangeMinDifference / 2.0);
            tmpMaxTemp = avgTemp + (dynamicRangeMinDifference / 2.0);
        }

        //convert temperatures
        for(int y = 0; y < OTC.IR_HEIGHT; y++){
            for(int x = 0 ; x<OTC.IR_WIDTH; x++){

                if(dynamicRange) {
                    irBitmap.setPixel(x, y, thermalPalette.temperatureToColor(getTemperatureAt(x, y), tmpMinTemp, tmpMaxTemp));
                } else if(customTemperatureRange) {
                    irBitmap.setPixel(x, y, thermalPalette.temperatureToColor(getTemperatureAt(x, y), customMinTemperature, customMaxTemperature));
                } else {
                    irBitmap.setPixel(x, y, thermalPalette.temperatureToColor(getTemperatureAt(x, y)));
                }

            }
        }

        //create spectrum
        for(int i = 0; i < SPECTRUM_RESOLUTION; i++){
            double curTemp = minTemp + (maxTemp - minTemp) * (i / (double)SPECTRUM_RESOLUTION );

            if(dynamicRange) {
                spectrumBitmap.setPixel(i, 0, thermalPalette.temperatureToColor(curTemp, tmpMinTemp, tmpMaxTemp));
            } else if(customTemperatureRange) {
                spectrumBitmap.setPixel(i, 0, thermalPalette.temperatureToColor(curTemp, customMinTemperature, customMaxTemperature));
            } else {
                spectrumBitmap.setPixel(i, 0, thermalPalette.temperatureToColor(curTemp));
            }
        }

    }

    public Point getMaxTemperaturePixel(){        return maxTempPixel;    }
    public Point getMaxTemperaturePixelInSearchArea(){return maxTempPixelInSearchArea;}

    public double getTemperatureAt(int x, int y){
        return tempData[y][x];
    }

    public Bitmap getBitmap(){
        return irBitmap;
    }

    public Bitmap getSpectrumBitmap(){
        return spectrumBitmap;
    }

    public ThermalPalette getThermalPalette(){
        return thermalPalette;
    }

    public void setThermalPalette(ThermalPalette tp){
        thermalPalette = tp;
    }

    public void setDynamicRange(boolean enabled){
        dynamicRange = enabled;
    }

    public void setDynamicRangeMinDifference(float minDifference){
        dynamicRangeMinDifference = minDifference;
    }

    public void setCustomTemperatureRange(boolean enabled){
        customTemperatureRange = enabled;
    }

    public void setCustomMinTemperature(double min){
        customMinTemperature = min;
    }

    public void setCustomMaxTemperature(double max){
        customMaxTemperature = max;
    }

    public int getWidth(){
        return width;
    }

    public int getHeight(){
        return height;
    }

    public double[][] getTemperatureData(){
        return tempData;
    }

}
