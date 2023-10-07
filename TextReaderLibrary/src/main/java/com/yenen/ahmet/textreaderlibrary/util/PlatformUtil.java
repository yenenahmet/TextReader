package com.yenen.ahmet.textreaderlibrary.util;

import android.content.Context;
import android.os.Build;

import com.google.android.gms.common.GoogleApiAvailability;

public class PlatformUtil {

    public final static int HUAWEI = 0;
    public final static int GOOGLE = 1;

    public static int getPlatformType(final Context context) {
        if (isGmsAvailable(context)) {
            return GOOGLE;
        }

        return -1;
    }

    private static boolean isGmsAvailable(final Context context) {
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        return result == com.google.android.gms.common.ConnectionResult.SUCCESS;
    }
}
