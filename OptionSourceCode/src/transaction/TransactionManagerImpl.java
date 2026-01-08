package transaction;

import java.io.FileInputStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 
 * Transaction Manager for the Distributed Travel Reservation System.
 */

public class TransactionManagerImpl
    extends java.rmi.server.UnicastRemoteObject
    implements TransactionManager {

    private static final String DIE_NO = "NoDie";
    private static final String DIE_BEFORE_COMMIT = "BeforeCommit";
    private static final String DIE_AFTER_COMMIT = "AfterCommit";

    private int xidCounter = 1;
    private final Map transactions = new HashMap();
    private String dieTime = DIE_NO;

    private static class TransactionRecord {
        static final int ACTIVE = 1;
        static final int PREPARING = 2;
        static final int COMMITTED = 3;
        static final int ABORTED = 4;

        int state = ACTIVE;
        final Set participants = new HashSet();
    }

    public static void main(String args[]) {
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream("conf/ddb.conf"));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        String rmiPort = prop.getProperty("tm.port");
        try {
            Registry registry = LocateRegistry.createRegistry(Integer.parseInt(rmiPort));
            TransactionManagerImpl obj = new TransactionManagerImpl();
            registry.rebind(TransactionManager.RMIName, obj);
            System.out.println("TM bound");
        } catch (Exception e) {
            System.err.println("TM not bound:" + e);
            System.exit(1);
        }
    }

    public TransactionManagerImpl() throws RemoteException {
    }

    public synchronized int start() throws RemoteException {
        int xid = xidCounter++;
        transactions.put(new Integer(xid), new TransactionRecord());
        return xid;
    }

    public void ping() throws RemoteException {
    }

    public synchronized void enlist(int xid, ResourceManager rm) throws RemoteException {
        TransactionRecord record = (TransactionRecord) transactions.get(new Integer(xid));
        if (record == null) {
            record = new TransactionRecord();
            transactions.put(new Integer(xid), record);
        }
        record.participants.add(rm);
    }

    public boolean commit(int xid)
        throws RemoteException,
               TransactionAbortedException,
               InvalidTransactionException {
        TransactionRecord record;
        Set participants;

        synchronized (this) {
            record = (TransactionRecord) transactions.get(new Integer(xid));
            if (record == null || record.state == TransactionRecord.ABORTED || record.state == TransactionRecord.COMMITTED) {
                throw new InvalidTransactionException(xid, "Xid is not active.");
            }
            if (DIE_BEFORE_COMMIT.equals(dieTime)) {
                dieNow();
            }
            record.state = TransactionRecord.PREPARING;
            participants = new HashSet(record.participants);
        }

        boolean prepared = true;
        for (Iterator iter = participants.iterator(); iter.hasNext();) {
            ResourceManager rm = (ResourceManager) iter.next();
            try {
                if (!rm.prepare(xid)) {
                    prepared = false;
                }
            } catch (Exception e) {
                prepared = false;
            }
        }

        if (!prepared) {
            abortInternal(xid, participants);
            synchronized (this) {
                if (record != null) {
                    record.state = TransactionRecord.ABORTED;
                    transactions.remove(new Integer(xid));
                }
            }
            throw new TransactionAbortedException(xid, "Prepare failed.");
        }

        for (Iterator iter = participants.iterator(); iter.hasNext();) {
            ResourceManager rm = (ResourceManager) iter.next();
            try {
                rm.commit(xid);
            } catch (Exception e) {
                // Best-effort commit; decision is already made.
            }
        }

        synchronized (this) {
            record.state = TransactionRecord.COMMITTED;
            transactions.remove(new Integer(xid));
        }

        if (DIE_AFTER_COMMIT.equals(dieTime)) {
            dieNow();
        }

        return true;
    }

    public void abort(int xid) throws RemoteException, InvalidTransactionException {
        TransactionRecord record;
        Set participants;

        synchronized (this) {
            record = (TransactionRecord) transactions.get(new Integer(xid));
            if (record == null) {
                throw new InvalidTransactionException(xid, "Xid is not active.");
            }
            participants = new HashSet(record.participants);
        }

        abortInternal(xid, participants);

        synchronized (this) {
            record.state = TransactionRecord.ABORTED;
            transactions.remove(new Integer(xid));
        }
    }

    private void abortInternal(int xid, Set participants) {
        for (Iterator iter = participants.iterator(); iter.hasNext();) {
            ResourceManager rm = (ResourceManager) iter.next();
            try {
                rm.abort(xid);
            } catch (Exception e) {
                // Best-effort abort.
            }
        }
    }

    public void setDieTime(String time) throws RemoteException {
        if (time == null) {
            dieTime = DIE_NO;
            return;
        }
        dieTime = time;
    }

    public boolean dieNow() throws RemoteException {
        System.exit(1);
        return true; // Unreachable but required by compiler.
    }

}
