package ble.localization.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

class PreviousReadingHolder {

    static final int NUMBER_OF_POSITIONS_TO_HOLD = 10;
    private LinkedHashMap<Integer, ArrayList<Integer>> readings;
    private int curr_number_of_values;

    PreviousReadingHolder() {
        readings = new LinkedHashMap<>();
        curr_number_of_values = 0;
    }

    void add(HashMap<Integer, Integer> new_vals) {

        // check to see if we've hit max number of positions
        if(this.isFilled()) {
            // if yes, remove the last readings in all hash-maps...
            for(ArrayList<Integer> r : readings.values()) {
                r.remove(r.size() - 1);
            }
            // ...and decrement the current number of values by 1
            curr_number_of_values -= 1;
        }

        // checks to see if all majors in member hashmap are in parameter hash-map
        // add missing ones with an rssi of 0
        for(Integer existing_major : readings.keySet()) {
            Set<Integer> param_keyset = new_vals.keySet();

            if(!param_keyset.contains(existing_major)) {
                new_vals.put(existing_major, 0);
            }
        }

        // go major by major in parameter, check to see if major is in member hash-map.
        for(Integer new_val_major : new_vals.keySet()) {
            Set<Integer> member_keyset = readings.keySet();

            if(!member_keyset.contains(new_val_major)) {
                readings.put(new_val_major, new ArrayList<Integer>());

                // If not, then add key to member map with as many zeroes as the current members
                // have values.
                ArrayList<Integer> curr_AL = readings.get(new_val_major);
                for(int i = 0; i < curr_number_of_values; i++) {
                    curr_AL.add(0);
                }
            }

            // If not (or after previous), add new reading at index 0 of corresponding ArrayList.
            ArrayList<Integer> current_AL = readings.get(new_val_major);
            current_AL.add(0, new_vals.get(new_val_major));
        }

        // Finish by incrementing number of values.
        curr_number_of_values += 1;

    }

    void clear() {
        readings.clear();
        curr_number_of_values = 0;
    }

    LinkedHashMap<Integer, ArrayList<Integer>> getData() {
        return readings;
    }

    int currentSize() {
        return curr_number_of_values;
    }

    boolean isFilled() {
        return (currentSize() >= NUMBER_OF_POSITIONS_TO_HOLD);
    }

    boolean isEmpty() {
        return currentSize() == 0;
    }

}
