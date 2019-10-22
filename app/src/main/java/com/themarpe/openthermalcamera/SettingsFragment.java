package com.themarpe.openthermalcamera;

import android.os.Bundle;
import android.util.Log;

import com.themarpe.openthermalcamera.Palette.ThermalPalette;

import com.themarpe.openthermalcamera.R;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;

public class SettingsFragment extends PreferenceFragmentCompat {

    OTC otcHandle;

    private static final String TAG = "SettingsFragment";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_screen, rootKey);

        otcHandle = new OTC(getContext(), null);

        //crashlytics test button

        /*
        findPreference("btn_crashlytics").setOnPreferenceClickListener(View -> {
            Crashlytics.getInstance().crash();
            return true;
        });

        findPreference("btn_bootloader").setOnPreferenceClickListener(View -> {
            otcHandle.jumpToBootloader();

            try {
                Thread.sleep(2000);
            } catch (Exception ex){

            }

            return true;
        });
        */


        //Emissivity
        SeekBarPreference emissivity = findPreference("emissivity");

        //thermal palette selection
        ThermalPaletteChangeListener tpcl = new ThermalPaletteChangeListener();

        String currentValue = PreferenceManager.getDefaultSharedPreferences(getContext()).getString("thermal_palette","");
        findPreference("thermal_palette").setOnPreferenceChangeListener(tpcl);
        tpcl.onPreferenceChange(findPreference("thermal_palette"), currentValue);

        SeekBarPreference customRangeMin = (SeekBarPreference) findPreference("custom_range_min");
        SeekBarPreference customRangeMax = (SeekBarPreference) findPreference("custom_range_max");


        //depencency on dynamic_range
        findPreference("dynamic_range_enabled").setOnPreferenceChangeListener((preference, newValue) -> {
            findPreference("dynamic_range_min_difference").setEnabled((boolean)newValue);
            return true;
        });


        //CUSTOM TEMPERATURE RANGE
        customRangeMin.setOnPreferenceChangeListener((preference, newValue) -> {
            int cur = (Integer) newValue;
            if(cur > customRangeMax.getValue()){
                customRangeMax.setValue(cur + 1);
            }
            return true;
        });

        customRangeMax.setOnPreferenceChangeListener(((preference, newValue) -> {
            int cur = (Integer) newValue;
            if(cur < customRangeMin.getValue()){
                customRangeMin.setValue(cur - 1);
            }
            return true;
        }));

    }

    class ThermalPaletteChangeListener implements Preference.OnPreferenceChangeListener {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            ListPreference thermalPeletteList = (ListPreference) preference;

            try{
                String val = (String) newValue;
                //Try to get ThermalPalette from value...
                String className = ThermalPalette.class.getPackage().getName()+"."+val;
                Log.d("SettingsFragment", "Trying to load className: " + className);
                ThermalPalette palette = (ThermalPalette) Class.forName(className).newInstance();

                double min = palette.getDefaultMinTemperature();
                double max = palette.getDefaultMaxTemperature();

                findPreference("thermal_palette_default_range_info").setTitle("Default Range is from " + min + " to " + max + ".");

                //current selected to summary
                CharSequence entry = thermalPeletteList.getEntries()[thermalPeletteList.findIndexOfValue(val)];
                Log.d("SettingsFragment", "Changing summary to: " + entry);
                ((ListPreference)preference).setSummary(entry);

            } catch (Exception ex) {
                ex.printStackTrace();
            }



            return true;

        }
    }


    private static void bindPreferenceSummaryToValue(Preference pref) {
        // Set the listener to watch for value changes.
        pref.setOnPreferenceChangeListener((preference1, newValue) -> {
            String newSummary = PreferenceManager.getDefaultSharedPreferences(preference1.getContext()).getString(preference1.getKey(), "");
            Log.d("SettingsFragment", "Changing summary to: " + newSummary);
            preference1.setSummary(newSummary);
            return true;
        });

    }

}
