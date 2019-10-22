package com.themarpe.openthermalcamera;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import io.fabric.sdk.android.Fabric;
import io.fabric.sdk.android.InitializationCallback;

public class CoreApp extends Application {


    private static Thread.UncaughtExceptionHandler mDefaultUEH = null;
    private Thread.UncaughtExceptionHandler mCaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {

        @Override
        public void uncaughtException(Thread t, Throwable throwable) {
            //If this exception comes from CameraView, ignore, disable camera and make a toast
            if(throwable.getStackTrace()[0].getClassName().equals("android.hardware.Camera")){
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = preferences.edit();
                try {
                    editor.putBoolean("overlay_enabled", false);
                    editor.putBoolean("cameraview_crashed", true);
                    editor.commit();
                } catch (Throwable everything){
                    Log.w("ExceptionHandler", "putting boolean to preferences crashed... " + everything.getMessage());
                }
                Log.w("ExceptionHandler", "CameraView error caught and disabled RGB overlay: " + throwable.getMessage());

                //try to rerun mainactivity
                Intent mStartActivity = new Intent(CoreApp.this, MainActivity.class);
                int mPendingIntentId = 123456;
                PendingIntent mPendingIntent = PendingIntent.getActivity(CoreApp.this, mPendingIntentId, mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) CoreApp.this.getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 500, mPendingIntent);
            }

            //let crashlytics handle the exception after that
            mDefaultUEH.uncaughtException(t, throwable);

        }

    };

    @Override
    public void onCreate() {
        super.onCreate();

        mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(mCaughtExceptionHandler);

        //handle CameraView crash and still report to crashlytics
        CrashlyticsCore core = new CrashlyticsCore.Builder()
                //.disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(new Fabric.Builder(this).kits(new Crashlytics.Builder()
                .core(core)
                .build())
                .initializationCallback(new InitializationCallback<Fabric>() {
                    @Override
                    public void success(Fabric fabric) {
                        //Thread.setDefaultUncaughtExceptionHandler(mCaughtExceptionHandler);
                        if(mDefaultUEH != Thread.getDefaultUncaughtExceptionHandler()){
                            mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
                            Thread.setDefaultUncaughtExceptionHandler(mCaughtExceptionHandler);
                        }
                    }
                    @Override
                    public void failure(Exception e) {

                    }
                })
                .build());

    }
}
