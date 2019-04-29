package io.github.openthermalcamera;

import android.os.Bundle;
import android.util.Log;

import io.github.openthermalcamera.Palette.ThermalPalette;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SeekBarPreference;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_screen, rootKey);

        //Emissivity
        SeekBarPreference emissivity = findPreference("emissivity");

        //thermal palette selection
        ThermalPaletteChangeListener tpcl = new ThermalPaletteChangeListener();

        String currentValue = PreferenceManager.getDefaultSharedPreferences(getContext()).getString("thermal_palette","");
        findPreference("thermal_palette").setOnPreferenceChangeListener(tpcl);
        tpcl.onPreferenceChange(findPreference("thermal_palette"), currentValue);

        SeekBarPreference customRangeMin = (SeekBarPreference) findPreference("custom_range_min");
        SeekBarPreference customRangeMax = (SeekBarPreference) findPreference("custom_range_max");


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
