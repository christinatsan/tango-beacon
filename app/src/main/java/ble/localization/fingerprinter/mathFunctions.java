package ble.localization.fingerprinter;

import org.apache.commons.lang3.ArrayUtils;

import java.math.BigDecimal;
import java.util.ArrayList;

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;

/**
 * Created by vishnunair on 7/1/16.
 */
public class MathFunctions {

    public static float floatRound(float d, int decimalPlace) {
        return (float)(Math.round(d * Math.pow(10, decimalPlace)) / Math.pow(10, decimalPlace));
    }

    public static double doubleRound(double d, int decimalPlace) {
        return Math.round(d * Math.pow(10, decimalPlace)) / Math.pow(10, decimalPlace);
    }

    public static double trimmedMean(final ArrayList<Double> arr_list, final int percent) {
        double[] arr = ArrayUtils.toPrimitive(arr_list.toArray(new Double[arr_list.size()]));

        final int n = arr.length;
        final int k = (int)Math.round(n * (percent / 100.0) / 2.0);
        final DoubleArrayList list = new DoubleArrayList(arr);
        list.sort();

        return Descriptive.trimmedMean(list, Descriptive.mean(list), k, k);
    }

}
