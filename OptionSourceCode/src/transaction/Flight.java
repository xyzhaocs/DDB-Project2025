package transaction;

/**
 * Flight table row.
 */
public class Flight implements ResourceItem {
    public static final String INDEX_FLIGHT_NUM = "flightNum";

    private String flightNum;
    private int price;
    private int numSeats;
    private int numAvail;
    private boolean isDeleted = false;

    public Flight(String flightNum, int numSeats, int numAvail, int price) {
        this.flightNum = flightNum;
        this.numSeats = numSeats;
        this.numAvail = numAvail;
        this.price = price;
    }

    public String[] getColumnNames() {
        return new String[] { "flightNum", "price", "numSeats", "numAvail" };
    }

    public String[] getColumnValues() {
        return new String[] { flightNum, "" + price, "" + numSeats, "" + numAvail };
    }

    public Object getIndex(String indexName) throws InvalidIndexException {
        if (INDEX_FLIGHT_NUM.equals(indexName)) {
            return flightNum;
        }
        throw new InvalidIndexException(indexName);
    }

    public Object getKey() {
        return flightNum;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void delete() {
        isDeleted = true;
    }

    public Object clone() {
        Flight copy = new Flight(flightNum, numSeats, numAvail, price);
        copy.isDeleted = isDeleted;
        return copy;
    }

    public String getFlightNum() {
        return flightNum;
    }

    public int getPrice() {
        return price;
    }

    public int getNumSeats() {
        return numSeats;
    }

    public int getNumAvail() {
        return numAvail;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public void setNumSeats(int numSeats) {
        this.numSeats = numSeats;
    }

    public void setNumAvail(int numAvail) {
        this.numAvail = numAvail;
    }
}
