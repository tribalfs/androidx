package androidx.reflect;

import android.os.Build;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Field;

//custom added
public class DeviceInfo {
    private static Boolean isOneUI = null;

    /**
     * @return true if the device has OneUI system, false otherwise.
     */
    public static boolean isOneUI() {
        if (isOneUI == null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Field field = SeslBaseReflector.getDeclaredField(Build.VERSION.class, "SEM_PLATFORM_INT");
                if (field != null) {
                    if (SeslBaseReflector.get(null, field) instanceof Integer) {
                        isOneUI = (Integer) SeslBaseReflector.get(null, field) > 90_000;
                        return isOneUI;
                    }
                }
            }
            isOneUI = false;
        }
        return isOneUI;
    }

    /**
     * This method is now a misnomer of {@link #isOneUI()}.
     * @return true if the device has OneUI system, false otherwise.
     */
    @Deprecated
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static boolean isSamsung() {
        return isOneUI();
    }

}
