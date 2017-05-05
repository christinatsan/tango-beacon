package ble.localization.manager;

import org.apache.commons.lang3.ArrayUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;

/**
 * Some general math functions.
 */
final class MathFunctions {

    /**
     * Rounds a floating point to the desired decimal place.
     * @param d The number.
     * @param decimalPlace The desired decimal place.
     * @return The rounded number.
     */
    static float floatRound(float d, int decimalPlace) {
        return (float)(Math.round(d * Math.pow(10, decimalPlace)) / Math.pow(10, decimalPlace));
    }

    /**
     * Rounds a double to the desired decimal place.
     * @param value The number.
     * @param places The desired decimal place.
     * @return The rounded number.
     */
    static double doubleRound(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    /**
     * Calculates a trimmed mean of a list of numbers.
     * @param arr_list The list of numbers.
     * @param percent The persent cutoff.
     * @return The result.
     */
    static double trimmedMean(final ArrayList<Double> arr_list, final int percent) {
        double[] arr = ArrayUtils.toPrimitive(arr_list.toArray(new Double[arr_list.size()]));

        final int n = arr.length;
        final int k = (int)Math.round(n * (percent / 100.0) / 2.0);
        final DoubleArrayList list = new DoubleArrayList(arr);
        list.sort();

        return Descriptive.trimmedMean(list, Descriptive.mean(list), k, k);
    }

    static double getEulerAngleZ(float[] quaternion)
    {
        float x = quaternion[0];
        float y = quaternion[1];
        float z = quaternion[2];
        float w = quaternion[3];

        // yaw (z-axis rotation)
        double t1 = 2.0 * (w*z+x*y);
        double t2 = 1.0 - 2.0 * (y*y+z*z);

        // this will match the rotation in a cartestian coordinate system
        return (Math.toDegrees(Math.atan2(t1, t2)) + 450) % 360;
    }

    static float roundToNearestHalf(float f) {
        return ((float)Math.round(f*2))/2;
    }

}
