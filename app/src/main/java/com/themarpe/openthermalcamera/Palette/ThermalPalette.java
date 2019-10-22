package com.themarpe.openthermalcamera.Palette;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.ColorInt;

public abstract class ThermalPalette {

    public static ThermalPalette getCurrentSelectedPalette(Context ctx) {

        String currentPalette = PreferenceManager.getDefaultSharedPreferences(ctx).getString("thermal_palette", "RainbowPalette");

        //Try to get ThermalPalette from value...
        String className = ThermalPalette.class.getPackage().getName() + "." + currentPalette;
        try {
            return (ThermalPalette) Class.forName(className).newInstance();
        } catch(Exception ex){
            Log.d("ThermalPalette", "Problem with getting current selected palette: " + ex.getMessage());
            return new RainbowPalette();
        }

    }

    abstract public double getDefaultMinTemperature();
    abstract public double getDefaultMaxTemperature();

    @ColorInt
    public int temperatureToColor(double temperature) {
        return temperatureToColor(temperature, getDefaultMinTemperature(), getDefaultMaxTemperature());
    }

    public abstract int temperatureToColor(double temperature, double minTemperature, double maxTemperature);

}
