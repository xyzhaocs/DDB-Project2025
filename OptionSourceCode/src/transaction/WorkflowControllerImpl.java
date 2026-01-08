package transaction;

import java.io.FileInputStream;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import lockmgr.DeadlockException;

/** 
 * Workflow Controller for the Distributed Travel Reservation System.
 */

public class WorkflowControllerImpl
    extends java.rmi.server.UnicastRemoteObject
    implements WorkflowController {

    private static final String TABLE_FLIGHTS = "Flights";
    private static final String TABLE_HOTELS = "Hotels";
    private static final String TABLE_CARS = "Cars";
    private static final String TABLE_CUSTOMERS = "Customers";
    private static final String TABLE_RESERVATIONS = "Reservations";

    protected ResourceManager rmFlights = null;
    protected ResourceManager rmRooms = null;
    protected ResourceManager rmCars = null;
    protected ResourceManager rmCustomers = null;
    protected TransactionManager tm = null;

    public static void main(String args[]) {
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("conf/ddb.conf"));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        String rmiPort = prop.getProperty("wc.port");
        try {
            Registry registry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
            WorkflowControllerImpl obj = new WorkflowControllerImpl();
            registry.rebind(WorkflowController.RMIName, obj);
            System.out.println("WC bound");
        } catch (Exception e) {
            System.err.println("WC not bound:" + e);
            System.exit(1);
        }
    }

    public WorkflowControllerImpl() throws RemoteException {
        while (!reconnect()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }
    }

    // TRANSACTION INTERFACE
    public int start() throws RemoteException {
        ensureConnected();
        return tm.start();
    }

    public boolean commit(int xid)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        ensureConnected();
        return tm.commit(xid);
    }

    public void abort(int xid)
        throws RemoteException,
               InvalidTransactionException {
        ensureConnected();
        tm.abort(xid);
    }

    // ADMINISTRATIVE INTERFACE
    public boolean addFlight(int xid, String flightNum, int numSeats, int price)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (flightNum == null || numSeats < 0) {
            return false;
        }
        ensureConnected();

        try {
            Flight flight = (Flight) rmFlights.query(xid, TABLE_FLIGHTS, flightNum);
            if (flight == null || flight.isDeleted()) {
                int actualPrice = price < 0 ? 0 : price;
                Flight newFlight = new Flight(flightNum, numSeats, numSeats, actualPrice);
                return rmFlights.insert(xid, TABLE_FLIGHTS, newFlight);
            }
            Flight updated = (Flight) flight.clone();
            updated.setNumSeats(flight.getNumSeats() + numSeats);
            updated.setNumAvail(flight.getNumAvail() + numSeats);
            if (price >= 0) {
                updated.setPrice(price);
            }
            return rmFlights.update(xid, TABLE_FLIGHTS, flightNum, updated);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in addFlight", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in addFlight", e);
        }
        return false;
    }

    public boolean deleteFlight(int xid, String flightNum)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (flightNum == null) {
            return false;
        }
        ensureConnected();

        try {
            Flight flight = (Flight) rmFlights.query(xid, TABLE_FLIGHTS, flightNum);
            if (flight == null || flight.isDeleted()) {
                return false;
            }
            if (hasReservation(xid, Reservation.RESERVATION_TYPE_FLIGHT, flightNum)) {
                return false;
            }
            return rmFlights.delete(xid, TABLE_FLIGHTS, flightNum);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in deleteFlight", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in deleteFlight", e);
        }
        return false;
    }

    public boolean addRooms(int xid, String location, int numRooms, int price)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null || numRooms < 0) {
            return false;
        }
        ensureConnected();

        try {
            Hotel hotel = (Hotel) rmRooms.query(xid, TABLE_HOTELS, location);
            if (hotel == null || hotel.isDeleted()) {
                int actualPrice = price < 0 ? 0 : price;
                Hotel newHotel = new Hotel(location, numRooms, numRooms, actualPrice);
                return rmRooms.insert(xid, TABLE_HOTELS, newHotel);
            }
            Hotel updated = (Hotel) hotel.clone();
            updated.setNumRooms(hotel.getNumRooms() + numRooms);
            updated.setNumAvail(hotel.getNumAvail() + numRooms);
            if (price >= 0) {
                updated.setPrice(price);
            }
            return rmRooms.update(xid, TABLE_HOTELS, location, updated);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in addRooms", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in addRooms", e);
        }
        return false;
    }

    public boolean deleteRooms(int xid, String location, int numRooms)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null || numRooms < 0) {
            return false;
        }
        ensureConnected();

        try {
            Hotel hotel = (Hotel) rmRooms.query(xid, TABLE_HOTELS, location);
            if (hotel == null || hotel.isDeleted()) {
                return false;
            }
            if (hotel.getNumAvail() < numRooms) {
                return false;
            }
            int newTotal = hotel.getNumRooms() - numRooms;
            int newAvail = hotel.getNumAvail() - numRooms;
            if (newTotal == 0) {
                return rmRooms.delete(xid, TABLE_HOTELS, location);
            }
            Hotel updated = (Hotel) hotel.clone();
            updated.setNumRooms(newTotal);
            updated.setNumAvail(newAvail);
            return rmRooms.update(xid, TABLE_HOTELS, location, updated);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in deleteRooms", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in deleteRooms", e);
        }
        return false;
    }

    public boolean addCars(int xid, String location, int numCars, int price)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null || numCars < 0) {
            return false;
        }
        ensureConnected();

        try {
            Car car = (Car) rmCars.query(xid, TABLE_CARS, location);
            if (car == null || car.isDeleted()) {
                int actualPrice = price < 0 ? 0 : price;
                Car newCar = new Car(location, numCars, numCars, actualPrice);
                return rmCars.insert(xid, TABLE_CARS, newCar);
            }
            Car updated = (Car) car.clone();
            updated.setNumCars(car.getNumCars() + numCars);
            updated.setNumAvail(car.getNumAvail() + numCars);
            if (price >= 0) {
                updated.setPrice(price);
            }
            return rmCars.update(xid, TABLE_CARS, location, updated);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in addCars", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in addCars", e);
        }
        return false;
    }

    public boolean deleteCars(int xid, String location, int numCars)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null || numCars < 0) {
            return false;
        }
        ensureConnected();

        try {
            Car car = (Car) rmCars.query(xid, TABLE_CARS, location);
            if (car == null || car.isDeleted()) {
                return false;
            }
            if (car.getNumAvail() < numCars) {
                return false;
            }
            int newTotal = car.getNumCars() - numCars;
            int newAvail = car.getNumAvail() - numCars;
            if (newTotal == 0) {
                return rmCars.delete(xid, TABLE_CARS, location);
            }
            Car updated = (Car) car.clone();
            updated.setNumCars(newTotal);
            updated.setNumAvail(newAvail);
            return rmCars.update(xid, TABLE_CARS, location, updated);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in deleteCars", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in deleteCars", e);
        }
        return false;
    }

    public boolean newCustomer(int xid, String custName)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        ensureConnected();

        try {
            Customer cust = (Customer) rmCustomers.query(xid, TABLE_CUSTOMERS, custName);
            if (cust != null && !cust.isDeleted()) {
                return true;
            }
            Customer newCust = new Customer(custName);
            return rmCustomers.insert(xid, TABLE_CUSTOMERS, newCust);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in newCustomer", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in newCustomer", e);
        }
        return false;
    }

    public boolean deleteCustomer(int xid, String custName)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        ensureConnected();

        try {
            Customer cust = (Customer) rmCustomers.query(xid, TABLE_CUSTOMERS, custName);
            if (cust == null || cust.isDeleted()) {
                return false;
            }

            Collection reservations = rmCustomers.query(xid, TABLE_RESERVATIONS, Reservation.INDEX_CUSTNAME, custName);
            for (Iterator iter = reservations.iterator(); iter.hasNext();) {
                Reservation resv = (Reservation) iter.next();
                if (resv.isDeleted()) {
                    continue;
                }
                String resvKey = resv.getResvKey();
                if (resv.getResvType() == Reservation.RESERVATION_TYPE_FLIGHT) {
                    Flight flight = (Flight) rmFlights.query(xid, TABLE_FLIGHTS, resvKey);
                    if (flight != null && !flight.isDeleted()) {
                        Flight updated = (Flight) flight.clone();
                        updated.setNumAvail(flight.getNumAvail() + 1);
                        rmFlights.update(xid, TABLE_FLIGHTS, resvKey, updated);
                    }
                } else if (resv.getResvType() == Reservation.RESERVATION_TYPE_CAR) {
                    Car car = (Car) rmCars.query(xid, TABLE_CARS, resvKey);
                    if (car != null && !car.isDeleted()) {
                        Car updated = (Car) car.clone();
                        updated.setNumAvail(car.getNumAvail() + 1);
                        rmCars.update(xid, TABLE_CARS, resvKey, updated);
                    }
                } else if (resv.getResvType() == Reservation.RESERVATION_TYPE_HOTEL) {
                    Hotel hotel = (Hotel) rmRooms.query(xid, TABLE_HOTELS, resvKey);
                    if (hotel != null && !hotel.isDeleted()) {
                        Hotel updated = (Hotel) hotel.clone();
                        updated.setNumAvail(hotel.getNumAvail() + 1);
                        rmRooms.update(xid, TABLE_HOTELS, resvKey, updated);
                    }
                }
                rmCustomers.delete(xid, TABLE_RESERVATIONS, resv.getKey());
            }

            return rmCustomers.delete(xid, TABLE_CUSTOMERS, custName);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in deleteCustomer", e);
        } catch (InvalidIndexException e) {
            abortAndThrow(xid, "Index failure in deleteCustomer", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in deleteCustomer", e);
        }
        return false;
    }

    // QUERY INTERFACE
    public int queryFlight(int xid, String flightNum)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (flightNum == null) {
            return 0;
        }
        ensureConnected();

        try {
            Flight flight = (Flight) rmFlights.query(xid, TABLE_FLIGHTS, flightNum);
            if (flight == null || flight.isDeleted()) {
                return 0;
            }
            return flight.getNumAvail();
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in queryFlight", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in queryFlight", e);
        }
        return 0;
    }

    public int queryFlightPrice(int xid, String flightNum)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (flightNum == null) {
            return 0;
        }
        ensureConnected();

        try {
            Flight flight = (Flight) rmFlights.query(xid, TABLE_FLIGHTS, flightNum);
            if (flight == null || flight.isDeleted()) {
                return 0;
            }
            return flight.getPrice();
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in queryFlightPrice", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in queryFlightPrice", e);
        }
        return 0;
    }

    public int queryRooms(int xid, String location)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null) {
            return 0;
        }
        ensureConnected();

        try {
            Hotel hotel = (Hotel) rmRooms.query(xid, TABLE_HOTELS, location);
            if (hotel == null || hotel.isDeleted()) {
                return 0;
            }
            return hotel.getNumAvail();
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in queryRooms", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in queryRooms", e);
        }
        return 0;
    }

    public int queryRoomsPrice(int xid, String location)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null) {
            return 0;
        }
        ensureConnected();

        try {
            Hotel hotel = (Hotel) rmRooms.query(xid, TABLE_HOTELS, location);
            if (hotel == null || hotel.isDeleted()) {
                return 0;
            }
            return hotel.getPrice();
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in queryRoomsPrice", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in queryRoomsPrice", e);
        }
        return 0;
    }

    public int queryCars(int xid, String location)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null) {
            return 0;
        }
        ensureConnected();

        try {
            Car car = (Car) rmCars.query(xid, TABLE_CARS, location);
            if (car == null || car.isDeleted()) {
                return 0;
            }
            return car.getNumAvail();
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in queryCars", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in queryCars", e);
        }
        return 0;
    }

    public int queryCarsPrice(int xid, String location)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (location == null) {
            return 0;
        }
        ensureConnected();

        try {
            Car car = (Car) rmCars.query(xid, TABLE_CARS, location);
            if (car == null || car.isDeleted()) {
                return 0;
            }
            return car.getPrice();
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in queryCarsPrice", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in queryCarsPrice", e);
        }
        return 0;
    }

    public int queryCustomerBill(int xid, String custName)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (custName == null) {
            return 0;
        }
        ensureConnected();

        int total = 0;
        try {
            Collection reservations = rmCustomers.query(xid, TABLE_RESERVATIONS, Reservation.INDEX_CUSTNAME, custName);
            for (Iterator iter = reservations.iterator(); iter.hasNext();) {
                Reservation resv = (Reservation) iter.next();
                if (resv.getResvType() == Reservation.RESERVATION_TYPE_FLIGHT) {
                    Flight flight = (Flight) rmFlights.query(xid, TABLE_FLIGHTS, resv.getResvKey());
                    if (flight != null && !flight.isDeleted()) {
                        total += flight.getPrice();
                    }
                } else if (resv.getResvType() == Reservation.RESERVATION_TYPE_CAR) {
                    Car car = (Car) rmCars.query(xid, TABLE_CARS, resv.getResvKey());
                    if (car != null && !car.isDeleted()) {
                        total += car.getPrice();
                    }
                } else if (resv.getResvType() == Reservation.RESERVATION_TYPE_HOTEL) {
                    Hotel hotel = (Hotel) rmRooms.query(xid, TABLE_HOTELS, resv.getResvKey());
                    if (hotel != null && !hotel.isDeleted()) {
                        total += hotel.getPrice();
                    }
                }
            }
            return total;
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in queryCustomerBill", e);
        } catch (InvalidIndexException e) {
            abortAndThrow(xid, "Index failure in queryCustomerBill", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in queryCustomerBill", e);
        }
        return 0;
    }

    // RESERVATION INTERFACE
    public boolean reserveFlight(int xid, String custName, String flightNum)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (custName == null || flightNum == null) {
            return false;
        }
        ensureConnected();

        try {
            if (!ensureCustomerExists(xid, custName)) {
                return false;
            }
            Flight flight = (Flight) rmFlights.query(xid, TABLE_FLIGHTS, flightNum);
            if (flight == null || flight.isDeleted() || flight.getNumAvail() <= 0) {
                return false;
            }
            ReservationKey key = new ReservationKey(custName, Reservation.RESERVATION_TYPE_FLIGHT, flightNum);
            Reservation existing = (Reservation) rmCustomers.query(xid, TABLE_RESERVATIONS, key);
            if (existing != null && !existing.isDeleted()) {
                return false;
            }

            Flight updated = (Flight) flight.clone();
            updated.setNumAvail(flight.getNumAvail() - 1);
            rmFlights.update(xid, TABLE_FLIGHTS, flightNum, updated);

            Reservation resv = new Reservation(custName, Reservation.RESERVATION_TYPE_FLIGHT, flightNum);
            return rmCustomers.insert(xid, TABLE_RESERVATIONS, resv);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in reserveFlight", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in reserveFlight", e);
        }
        return false;
    }

    public boolean reserveCar(int xid, String custName, String location)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (custName == null || location == null) {
            return false;
        }
        ensureConnected();

        try {
            if (!ensureCustomerExists(xid, custName)) {
                return false;
            }
            Car car = (Car) rmCars.query(xid, TABLE_CARS, location);
            if (car == null || car.isDeleted() || car.getNumAvail() <= 0) {
                return false;
            }
            ReservationKey key = new ReservationKey(custName, Reservation.RESERVATION_TYPE_CAR, location);
            Reservation existing = (Reservation) rmCustomers.query(xid, TABLE_RESERVATIONS, key);
            if (existing != null && !existing.isDeleted()) {
                return false;
            }

            Car updated = (Car) car.clone();
            updated.setNumAvail(car.getNumAvail() - 1);
            rmCars.update(xid, TABLE_CARS, location, updated);

            Reservation resv = new Reservation(custName, Reservation.RESERVATION_TYPE_CAR, location);
            return rmCustomers.insert(xid, TABLE_RESERVATIONS, resv);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in reserveCar", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in reserveCar", e);
        }
        return false;
    }

    public boolean reserveRoom(int xid, String custName, String location)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (custName == null || location == null) {
            return false;
        }
        ensureConnected();

        try {
            if (!ensureCustomerExists(xid, custName)) {
                return false;
            }
            Hotel hotel = (Hotel) rmRooms.query(xid, TABLE_HOTELS, location);
            if (hotel == null || hotel.isDeleted() || hotel.getNumAvail() <= 0) {
                return false;
            }
            ReservationKey key = new ReservationKey(custName, Reservation.RESERVATION_TYPE_HOTEL, location);
            Reservation existing = (Reservation) rmCustomers.query(xid, TABLE_RESERVATIONS, key);
            if (existing != null && !existing.isDeleted()) {
                return false;
            }

            Hotel updated = (Hotel) hotel.clone();
            updated.setNumAvail(hotel.getNumAvail() - 1);
            rmRooms.update(xid, TABLE_HOTELS, location, updated);

            Reservation resv = new Reservation(custName, Reservation.RESERVATION_TYPE_HOTEL, location);
            return rmCustomers.insert(xid, TABLE_RESERVATIONS, resv);
        } catch (DeadlockException e) {
            abortAndThrow(xid, "Deadlock in reserveRoom", e);
        } catch (RemoteException e) {
            abortAndThrow(xid, "RM failure in reserveRoom", e);
        }
        return false;
    }

    public boolean reserveItinerary(int xid, String custName, List flightNumList, String location, boolean needCar, boolean needRoom)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        if (custName == null) {
            return false;
        }
        ensureConnected();

        if (flightNumList != null) {
            for (Iterator iter = flightNumList.iterator(); iter.hasNext();) {
                String flightNum = (String) iter.next();
                if (!reserveFlight(xid, custName, flightNum)) {
                    return false;
                }
            }
        }
        if (needCar) {
            if (!reserveCar(xid, custName, location)) {
                return false;
            }
        }
        if (needRoom) {
            if (!reserveRoom(xid, custName, location)) {
                return false;
            }
        }
        return true;
    }

    // TECHNICAL/TESTING INTERFACE
    public boolean reconnect() throws RemoteException {
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("conf/ddb.conf"));
        } catch (Exception e1) {
            e1.printStackTrace();
            return false;
        }

        try {
            rmFlights = (ResourceManager) Naming.lookup("//:" + prop.getProperty("rm." + ResourceManager.RMINameFlights + ".port") + "/" + ResourceManager.RMINameFlights);
            System.out.println("WC bound to RMFlights");
            rmRooms = (ResourceManager) Naming.lookup("//:" + prop.getProperty("rm." + ResourceManager.RMINameRooms + ".port") + "/" + ResourceManager.RMINameRooms);
            System.out.println("WC bound to RMRooms");
            rmCars = (ResourceManager) Naming.lookup("//:" + prop.getProperty("rm." + ResourceManager.RMINameCars + ".port") + "/" + ResourceManager.RMINameCars);
            System.out.println("WC bound to RMCars");
            rmCustomers = (ResourceManager) Naming.lookup("//:" + prop.getProperty("rm." + ResourceManager.RMINameCustomers + ".port") + "/" + ResourceManager.RMINameCustomers);
            System.out.println("WC bound to RMCustomers");
            tm = (TransactionManager) Naming.lookup("//:" + prop.getProperty("tm.port") + "/" + TransactionManager.RMIName);
            System.out.println("WC bound to TM");
        } catch (Exception e) {
            System.err.println("WC cannot bind to some component:" + e);
            return false;
        }

        try {
            if (rmFlights.reconnect() && rmRooms.reconnect() && rmCars.reconnect() && rmCustomers.reconnect()) {
                return true;
            }
        } catch (Exception e) {
            System.err.println("Some RM cannot reconnect:" + e);
            return false;
        }

        return false;
    }

    public boolean dieNow(String who) throws RemoteException {
        if (who.equals(TransactionManager.RMIName) || who.equals("ALL")) {
            try {
                tm.dieNow();
            } catch (RemoteException e) {
            }
        }
        if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
            try {
                rmFlights.dieNow();
            } catch (RemoteException e) {
            }
        }
        if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
            try {
                rmRooms.dieNow();
            } catch (RemoteException e) {
            }
        }
        if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
            try {
                rmCars.dieNow();
            } catch (RemoteException e) {
            }
        }
        if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
            try {
                rmCustomers.dieNow();
            } catch (RemoteException e) {
            }
        }
        if (who.equals(WorkflowController.RMIName) || who.equals("ALL")) {
            System.exit(1);
        }
        return true;
    }

    public boolean dieRMAfterEnlist(String who) throws RemoteException {
        return setRMDietime(who, "AfterEnlist");
    }

    public boolean dieRMBeforePrepare(String who) throws RemoteException {
        return setRMDietime(who, "BeforePrepare");
    }

    public boolean dieRMAfterPrepare(String who) throws RemoteException {
        return setRMDietime(who, "AfterPrepare");
    }

    public boolean dieTMBeforeCommit() throws RemoteException {
        tm.setDieTime("BeforeCommit");
        return true;
    }

    public boolean dieTMAfterCommit() throws RemoteException {
        tm.setDieTime("AfterCommit");
        return true;
    }

    public boolean dieRMBeforeCommit(String who) throws RemoteException {
        return setRMDietime(who, "BeforeCommit");
    }

    public boolean dieRMBeforeAbort(String who) throws RemoteException {
        return setRMDietime(who, "BeforeAbort");
    }

    private void ensureConnected() throws RemoteException {
        if (tm == null || rmFlights == null || rmRooms == null || rmCars == null || rmCustomers == null) {
            if (!reconnect()) {
                throw new RemoteException("WC cannot reconnect to components");
            }
        }
    }

    private void abortAndThrow(int xid, String msg, Exception e) throws TransactionAbortedException {
        try {
            if (tm != null) {
                tm.abort(xid);
            }
        } catch (Exception ignored) {
        }
        throw new TransactionAbortedException(xid, msg + ": " + e.getMessage());
    }

    private boolean ensureCustomerExists(int xid, String custName) throws DeadlockException, InvalidTransactionException, RemoteException {
        Customer cust = (Customer) rmCustomers.query(xid, TABLE_CUSTOMERS, custName);
        return cust != null && !cust.isDeleted();
    }

    private boolean hasReservation(int xid, int resvType, String resvKey)
        throws DeadlockException, InvalidTransactionException, RemoteException {
        Collection reservations = rmCustomers.query(xid, TABLE_RESERVATIONS);
        for (Iterator iter = reservations.iterator(); iter.hasNext();) {
            Reservation resv = (Reservation) iter.next();
            if (resv.getResvType() == resvType && resv.getResvKey().equals(resvKey)) {
                return true;
            }
        }
        return false;
    }

    private boolean setRMDietime(String who, String time) throws RemoteException {
        if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
            rmFlights.setDieTime(time);
        }
        if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
            rmRooms.setDieTime(time);
        }
        if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
            rmCars.setDieTime(time);
        }
        if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
            rmCustomers.setDieTime(time);
        }
        return true;
    }
}
