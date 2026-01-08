package transaction;

/**
 * Customer table row.
 */
public class Customer implements ResourceItem {
    public static final String INDEX_CUSTNAME = "custName";

    private String custName;
    private boolean isDeleted = false;

    public Customer(String custName) {
        this.custName = custName;
    }

    public String[] getColumnNames() {
        return new String[] { "custName" };
    }

    public String[] getColumnValues() {
        return new String[] { custName };
    }

    public Object getIndex(String indexName) throws InvalidIndexException {
        if (INDEX_CUSTNAME.equals(indexName)) {
            return custName;
        }
        throw new InvalidIndexException(indexName);
    }

    public Object getKey() {
        return custName;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void delete() {
        isDeleted = true;
    }

    public Object clone() {
        Customer copy = new Customer(custName);
        copy.isDeleted = isDeleted;
        return copy;
    }

    public String getCustName() {
        return custName;
    }
}
