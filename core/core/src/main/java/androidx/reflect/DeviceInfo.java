
package androidx.reflect;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Locale;


public class DeviceInfo {
    private static Boolean isSammy = null;

    //custom added
    //It would be better if we can check if device OS is OneUI without using context.
    public static boolean isSamsung() {
        if (isSammy == null){
            isSammy =  Build.MANUFACTURER.toLowerCase(Locale.ENGLISH).equals("samsung");
        }
        return isSammy;
    }
}
