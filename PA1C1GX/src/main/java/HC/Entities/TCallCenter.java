package HC.Entities;

import HC.Data.ERoom_CC;
import HC.Monitors.*;

import java.util.HashMap;

import static HC.Data.ERoom_CC.*;


class Room {
    private Occupation both;
    private Occupation children;
    private Occupation adults;
    private int pendingCalls = 0;   // the calls from the CallCenter that were not completed
    private Room next;
    private final boolean needsCall;

    public Room(int occ, int maxOcc, boolean needsCall) {
        both = new Occupation(occ, maxOcc);
        this.needsCall = needsCall;
    }

    public Room(int childOcc, int childMaxOcc, int adultOcc, int adultMaxOcc, boolean needsCall) {
        children = new Occupation(childOcc, childMaxOcc);
        adults = new Occupation(adultOcc, adultMaxOcc);
        this.needsCall = needsCall;
    }

    private Occupation getAdults() {
        return both == null ? adults : both;
    }

    private Occupation getChildren() {
        return both == null ? children : both;
    }

    private int getMaxOcc() {
        return both == null ? adults.maxOcc + children.maxOcc : both.maxOcc;
    }

    private int getOcc() {
        return both == null ? adults.occ + children.occ : both.occ;
    }

    private boolean hasChildren() {
        return getAdults().occ > 0;
    }

    private boolean hasAdults() {
        return getChildren().occ > 0;
    }

    private boolean isFullOfChildren() {
        Occupation c = getChildren();
        return c.occ >= c.maxOcc;
    }

    private boolean isFullOfAdults() {
        Occupation a = getAdults();
        return a.occ >= a.maxOcc;
    }

    private boolean isFullOfPendingCalls() {
        return pendingCalls >= next.getMaxOcc() - next.getOcc();
    }

    public void setNext(Room next) {
        this.next = next;
    }

    public Room getNext() {
        return next;
    }

    public int canCallPatient() {
        if (next == null)
            throw new IllegalCallerException("Room does not has a next room to move patient.");
        if (isFullOfPendingCalls())
            return 0;
        if (hasAdults() && !next.isFullOfAdults())
            return 1;
        if (hasChildren() && !next.isFullOfChildren())
            return 2;
        return 0;
    }

    public void addPatient(TPatient patient) {
        Occupation o = patient.isAdult() ? getAdults() : getChildren();
        o.increment();
    }

    public void removePatient(TPatient patient) {
        Occupation o = patient.isAdult() ? getAdults() : getChildren();
        o.decrement();
        if (needsCall) {
            if (pendingCalls <= 0)
                throw new IllegalCallerException("Cannot decrement calls when it's empty.");
            pendingCalls--;
        }
    }

    public void callPatient() {
        if (pendingCalls >= getMaxOcc())
            throw new IllegalCallerException("Cannot increment calls when it's full.");
        pendingCalls++;
    }

    class Occupation {
        private final int maxOcc;
        private int occ;

        public Occupation(int occ, int maxOcc) {
            this.occ = occ;
            this.maxOcc = maxOcc;
        }

        public void increment() {
            if (occ >= maxOcc)
                throw new IllegalCallerException("Cannot increment occupation when it's full.");
            occ++;
        }

        public void decrement() {
            if (occ <= 0)
                throw new IllegalCallerException("Cannot decrement occupation when it's empty.");
            occ--;
        }
    }
}


public class TCallCenter extends Thread {
    private final ICCH_CallCenter cch;         // call center hall
    private final IETH_CallCenter eth;         // entrance hall
    private final IWTH_CallCenter wth;         // waiting hall
    private final IMDH_CallCenter mdh;         // medical hall
    private final IPYH_CallCenter pyh;         // payment hall

    private HashMap<ERoom_CC, Room> state = new HashMap<>();   // occupation state of the simulation
    private boolean auto = true;
    private boolean next = false;

    public TCallCenter(int NoS, int NoA, int NoC, ICCH_CallCenter cch, IETH_CallCenter eth, IWTH_CallCenter wth,
                       IMDH_CallCenter mdh, IPYH_CallCenter pyh) {
        this.cch = cch;
        this.eth = eth;
        this.wth = wth;
        this.mdh = mdh;
        this.pyh = pyh;

        int seats = NoS / 2;
        int total = NoA + NoC;

        Room reth = new Room(NoC, seats, NoA, seats, true);
        Room revh = new Room(0, 4, false);
        Room rwth = new Room(0, total, true);
        Room rwtri = new Room(0, seats, 0, seats, true);
        Room rmdw = new Room(0, 1, 0, 1, true);
        Room rmdri = new Room(0, 2, 0, 2, false);

        reth.setNext(revh);
        revh.setNext(rwth);
        rwth.setNext(rwtri);
        rwtri.setNext(rmdw);
        rmdw.setNext(rmdri);

        state.put(ETH, reth);
        state.put(EVH, revh);
        state.put(WTH, rwth);
        state.put(WTRi, rwtri);
        state.put(MDW, rmdw);
        state.put(MDRi, rmdri);
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
    }

    public void allowNextPatient() {
        this.next = true;
    }

    @Override
    public void run() {
        while (true) {
            // call patients
            if (state.get(ETH).canCallPatient() != 0 && (auto || next)) {
                state.get(ETH).callPatient();
                eth.callPatient();
                next = false;
            }
            if (state.get(WTH).canCallPatient() != 0 && (auto || next)) {
                state.get(WTH).callPatient();
                wth.callPatient();
                next = false;
            }
            if (state.get(WTRi).canCallPatient() != 0 && (auto || next)) {
                state.get(WTRi).callPatient();
                wth.callPatient2();
                next = false;
            }
            int callType = state.get(MDW).canCallPatient();
            if (callType != 0 && (auto || next)) {
                state.get(MDW).callPatient();
                mdh.callPatient(callType == 1);
                next = false;
            }

            // receive notification
            var notif = cch.getNotification();
//            System.out.println(notif);
            ERoom_CC roomType = notif.room;
            TPatient patient = notif.patient;

            // update state
            var room = state.get(roomType);
            room.removePatient(patient);
            room.getNext().addPatient(patient);
        }
    }
}
