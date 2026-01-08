package transaction;

/**
 * Hotel table row.
 */
public class Hotel implements ResourceItem {
    public static final String INDEX_LOCATION = "location";

    private String location;
    private int price;
    private int numRooms;
    private int numAvail;
    private boolean isDeleted = false;

    public Hotel(String location, int numRooms, int numAvail, int price) {
        this.location = location;
        this.numRooms = numRooms;
        this.numAvail = numAvail;
        this.price = price;
    }

    public String[] getColumnNames() {
        return new String[] { "location", "price", "numRooms", "numAvail" };
    }

    public String[] getColumnValues() {
        return new String[] { location, "" + price, "" + numRooms, "" + numAvail };
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
        Hotel copy = new Hotel(location, numRooms, numAvail, price);
        copy.isDeleted = isDeleted;
        return copy;
    }

    public String getLocation() {
        return location;
    }

    public int getPrice() {
        return price;
    }

    public int getNumRooms() {
        return numRooms;
    }

    public int getNumAvail() {
        return numAvail;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public void setNumRooms(int numRooms) {
        this.numRooms = numRooms;
    }

    public void setNumAvail(int numAvail) {
        this.numAvail = numAvail;
    }
}
