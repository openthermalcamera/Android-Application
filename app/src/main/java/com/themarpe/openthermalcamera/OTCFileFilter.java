package com.themarpe.openthermalcamera;

import java.io.File;

public class OTCFileFilter {

    private final String[] okFileExtensions = new String[] {"otc"};

    public boolean accept(File file) {
        for (String extension : okFileExtensions) {
            if (file.getName().toLowerCase().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

}
