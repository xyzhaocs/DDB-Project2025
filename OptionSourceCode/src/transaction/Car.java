package transaction;

/**
 * Car table row.
 */
public class Car implements ResourceItem {
    public static final String INDEX_LOCATION = "location";

    private String location;
    private int price;
    private int numCars;
    private int numAvail;
    private boolean isDeleted = false;

    public Car(String location, int numCars, int numAvail, int price) {
        this.location = location;
        this.numCars = numCars;
        this.numAvail = numAvail;
        this.price = price;
    }

    public String[] getColumnNames() {
        return new String[] { "location", "price", "numCars", "numAvail" };
    }

    public String[] getColumnValues() {
        return new String[] { location, "" + price, "" + numCars, "" + numAvail };
    }

    public Object getIndex(String indexName) throws InvalidIndexException {
        if (INDEX_LOCATION.equals(indexName)) {
            return location;
        }
        throw new InvalidIndexException(indexName);
    }

    public Object getKey() {
        return location;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void delete() {
        isDeleted = true;
    }

    public Object clone() {
        Car copy = new Car(location, numCars, numAvail, price);
        copy.isDeleted = isDeleted;
        return copy;
    }

    public String getLocation() {
        return location;
    }

    public int getPrice() {
        return price;
    }

    public int getNumCars() {
        return numCars;
    }

    public int getNumAvail() {
        return numAvail;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public void setNumCars(int numCars) {
        this.numCars = numCars;
    }

    public void setNumAvail(int numAvail) {
        this.numAvail = numAvail;
    }
}
