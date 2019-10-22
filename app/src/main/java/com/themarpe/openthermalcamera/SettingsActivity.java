package com.themarpe.openthermalcamera;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.themarpe.openthermalcamera.R;

public class SettingsActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();

        //add on back button press
        findViewById(R.id.btnBack).setOnClickListener(View -> finish());

    }



}
