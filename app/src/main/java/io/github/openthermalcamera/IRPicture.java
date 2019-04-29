package io.github.openthermalcamera;

import android.graphics.Bitmap;
import android.graphics.Point;

import io.github.openthermalcamera.Palette.RainbowPalette;
import io.github.openthermalcamera.Palette.ThermalPalette;

public class IRPicture {

    private ThermalPalette thermalPalette;
    private double[][] tempData;
    Bitmap irBitmap;

    protected boolean customTemperatureRange = false;

    protected double customMinTemperature;
    protected double customMaxTemperature;

    private int width, height;
    private double minTemp, maxTemp, maxTempSearchArea;
    private int searchAreaSize=3;
    public void setSeachAreaSize(int size){searchAreaSize=size;}

    protected boolean dynamicRange;

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
        maxTempPixel = new Point(maxTempPixel);

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

        //convert temperatures
        for(int y = 0; y < OTC.IR_HEIGHT; y++){
            for(int x = 0 ; x<OTC.IR_WIDTH; x++){

                if(dynamicRange) {
                    irBitmap.setPixel(x, y, thermalPalette.temperatureToColor(getTemperatureAt(x, y), minTemp, maxTemp));
                } else if(customTemperatureRange) {
                    irBitmap.setPixel(x, y, thermalPalette.temperatureToColor(getTemperatureAt(x, y), customMinTemperature, customMaxTemperature));
                } else {
                    irBitmap.setPixel(x, y, thermalPalette.temperatureToColor(getTemperatureAt(x, y)));
                }

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

    public ThermalPalette getThermalPalette(){
        return thermalPalette;
    }

    public void setThermalPalette(ThermalPalette tp){
        thermalPalette = tp;
    }

    public void setDynamicRange(boolean enabled){
        dynamicRange = enabled;
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
