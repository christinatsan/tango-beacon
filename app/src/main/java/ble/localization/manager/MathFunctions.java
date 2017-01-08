package ble.localization.manager;

import org.apache.commons.lang3.ArrayUtils;

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
     * @param d The number.
     * @param decimalPlace The desired decimal place.
     * @return The rounded number.
     */
    static double doubleRound(double d, int decimalPlace) {
        return Math.round(d * Math.pow(10, decimalPlace)) / Math.pow(10, decimalPlace);
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

}
