package com.yenen.ahmet.textreaderlibrary.util;

import android.content.Context;
import android.os.Build;

import com.google.android.gms.common.GoogleApiAvailability;
import com.huawei.hms.api.HuaweiApiAvailability;

public class PlatformUtil {

    public final static int HUAWEI = 0;
    public final static int GOOGLE = 1;

    public static int getPlatformType(final Context context) {
        if (isGmsAvailable(context)) {
            return GOOGLE;
        }

        if (isHmsAvailable(context) && Build.MANUFACTURER.equals("HUAWEI")) {
            return HUAWEI;
        }

        return -1;
    }

    private static boolean isHmsAvailable(final Context context) {
        int result = HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context);
        return result == com.huawei.hms.api.ConnectionResult.SUCCESS;
    }

    private static boolean isGmsAvailable(final Context context) {
        int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        return result == com.google.android.gms.common.ConnectionResult.SUCCESS;
    }
}
