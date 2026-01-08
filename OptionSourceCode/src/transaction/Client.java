package transaction;

import java.io.FileInputStream;
import java.rmi.Naming;
import java.util.Properties;

/** 
 * A toy client of the Distributed Travel Reservation System.
 * 
 */

public class Client {
    private static void printCaseHeader(int index, String title) {
        System.out.println("CASE " + index + ": " + title);
    }

    private static void require(boolean cond, String msg) {
        if (!cond) {
            throw new RuntimeException("TEST FAILED: " + msg);
        }
    }

    private static boolean hasArg(String[] args, String key) {
        if (args == null) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            if (key.equalsIgnoreCase(args[i])) {
                return true;
            }
        }
        return false;
    }
    
    public static void main(String args[]) {
        Properties prop = new Properties();
        try
        {
            prop.load(new FileInputStream("conf/ddb.conf"));
        }
        catch (Exception e1)
        {
            e1.printStackTrace();
            return;
        }
        String rmiPort = prop.getProperty("wc.port");
        if (rmiPort == null)
        {
            rmiPort = "";
        }
        else if (!rmiPort.equals(""))
        {
            rmiPort = "//:" + rmiPort + "/";
        }

        WorkflowController wc = null;
        try
        {
            wc = (WorkflowController) Naming.lookup(rmiPort + WorkflowController.RMIName);
            System.out.println("Bound to WC");
        }
        catch (Exception e)
        {
            System.err.println("Cannot bind to WC:" + e);
            System.exit(1);
        }

        try {
            // Case 1: basic add/query/commit
            printCaseHeader(1, "basic add and query");
            int xid = wc.start();
            require(wc.addFlight(xid, "347", 230, 999), "addFlight failed");
            require(wc.queryFlight(xid, "347") == 230, "queryFlight mismatch");
            require(wc.commit(xid), "commit failed");
            System.out.println("CASE 1 PASSED");

            // Case 2: reserve flight and availability change
            printCaseHeader(2, "reserve flight and availability");
            int xid2 = wc.start();
            require(wc.newCustomer(xid2, "Alice"), "newCustomer failed");
            require(wc.addFlight(xid2, "MU100", 2, 500), "addFlight MU100 failed");
            require(wc.reserveFlight(xid2, "Alice", "MU100"), "reserveFlight failed");
            require(wc.queryFlight(xid2, "MU100") == 1, "availability mismatch");
            require(wc.commit(xid2), "commit failed");
            System.out.println("CASE 2 PASSED");

            // Case 3: delete rooms insufficient
            printCaseHeader(3, "delete rooms insufficient");
            int xid3 = wc.start();
            require(wc.addRooms(xid3, "SFO", 2, 300), "addRooms failed");
            require(!wc.deleteRooms(xid3, "SFO", 5), "deleteRooms should fail");
            wc.abort(xid3);
            System.out.println("CASE 3 PASSED");

            // Case 4: delete customer releases resource
            printCaseHeader(4, "delete customer releases resource");
            int xid4 = wc.start();
            require(wc.newCustomer(xid4, "Bob"), "newCustomer Bob failed");
            require(wc.addCars(xid4, "PA", 1, 50), "addCars failed");
            require(wc.reserveCar(xid4, "Bob", "PA"), "reserveCar failed");
            require(wc.deleteCustomer(xid4, "Bob"), "deleteCustomer failed");
            require(wc.queryCars(xid4, "PA") == 1, "car availability mismatch after deleteCustomer");
            require(wc.commit(xid4), "commit failed");
            System.out.println("CASE 4 PASSED");

            if (hasArg(args, "case5")) {
                // Case 5: 2PC failure before prepare (RMFlights dies)
                printCaseHeader(5, "2PC failure before prepare (RMFlights)");
                wc.dieRMBeforePrepare("RMFlights");
                int xid5 = wc.start();
                require(wc.addFlight(xid5, "F500", 1, 100), "addFlight failed");
                try {
                    wc.commit(xid5);
                    throw new RuntimeException("commit should have aborted");
                } catch (TransactionAbortedException e) {
                    System.out.println("CASE 5 PASSED");
                }
            }

            if (hasArg(args, "case6")) {
                // Case 6: TM failure before commit
                printCaseHeader(6, "TM failure before commit");
                wc.dieTMBeforeCommit();
                int xid6 = wc.start();
                require(wc.addRooms(xid6, "NYC", 1, 200), "addRooms failed");
                try {
                    wc.commit(xid6);
                    throw new RuntimeException("commit should have failed");
                } catch (Exception e) {
                    System.out.println("CASE 6 PASSED");
                }
            }

            System.out.println("ALL REQUESTED CASES PASSED.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("TEST FAILED: " + e.getMessage());
            System.exit(1);
        }

    }
}
