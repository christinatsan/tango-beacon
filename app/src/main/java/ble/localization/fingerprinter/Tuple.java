package ble.localization.fingerprinter;

/**
 * An organized representation of a tuple/coordinate.
 * Separated into own file so it could also be used in the Animal class.
 */
final class Tuple {
    /**
     * The x-value.
     */
    int x;

    /**
     * The y-value.
     */
    int y;

    /**
     * Constructs the tuple object.
     * @param x The x-value.
     * @param y The y-value.
     */
    Tuple(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets a new value for the tuple object.
     * @param x The x-value.
     * @param y The y-value.
     */
    void set(int x, int y){
        this.x = x;
        this.y = y;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    /**
     * Checks whether this Tuple and another Object are equal.
     * @param o The other object.
     * @return True if the objects are equal. False if not.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tuple tuple = (Tuple) o;

        return x == tuple.x && y == tuple.y;
    }

    /**
     * Returns a hash code value for the object. Generally, if two Tuples are equal according to the equals(Object)
     * method, then they must have the same hashCode.
     * @return The hashCode of `this` instance of the object.
     */
    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return result;
    }
}