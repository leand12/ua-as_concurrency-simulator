package HC.Monitors;

import HC.Data.EDoS;
import HC.Data.ERoom;
import HC.Entities.TPatient;
import HC.Logging.Logging;
import HC.Main.GUI;

import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static HC.Data.ERoom_CC.ETH;

/**
 * Evaluation Hall Monitor, where the DoS is evaluated for each patient.
 */
public class MEVH implements IEVH_Patient, IEVH_Nurse {
    private final ReentrantLock rl;
    private final Condition cNotFull;
    private final Condition[] cNotEvaluated;
    private final Logging log;
    private final GUI gui;
    private final TPatient[] rooms;     // represents the rooms EVR1, EVR2, EVR3 and EVR4
    private final boolean[] evaluated;
    private final int ttm;              // time to move
    private final int evt;              // evaluation time
    private final int maxPatients = 4;

    private int patientCount = 0;

    public MEVH(int evt, int ttm, Logging log, GUI gui) {
        this.log = log;
        this.gui = gui;
        this.ttm = ttm;
        this.evt = evt;

        rl = new ReentrantLock();
        cNotFull = rl.newCondition();
        cNotEvaluated = new Condition[maxPatients];
        for (var i = 0; i < cNotEvaluated.length; i++)
            cNotEvaluated[i] = rl.newCondition();
        rooms = new TPatient[maxPatients];
        evaluated = new boolean[maxPatients];
    }

    @Override
    public void enterPatient(TPatient patient) {
        try {
            rl.lock();
            while (isFull()) cNotFull.await();

            for (int i = 0; i < maxPatients; i++) {
                // patient enters room
                if (rooms[i] == null) {
                    patientCount++;
                    rooms[i] = patient;
                    patient.notifyExit(ETH);

                    var room = ERoom.valueOf("EVR" + (i + 1));
                    log.logPatient(room, patient);
                    gui.addPatient(room, patient);

                    cNotEvaluated[i].signal();
                    while (!evaluated[i]) cNotEvaluated[i].await();
                    evaluated[i] = false;

                    log.logPatient(room, patient);
                    gui.updateRoom(room);
                    rl.unlock();

                    // patient moves to WTH
                    Thread.sleep((int) Math.floor(Math.random() * ttm));

                    rl.lock();
                    this.rooms[i] = null;
                    patientCount--;
                    cNotFull.signal();
                    gui.removePatient(room, patient);
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rl.unlock();
        }
    }

    @Override
    public void evaluatePatient(int idx) {
        try {
            rl.lock();
            while (rooms[idx] == null || rooms[idx].getDos() != EDoS.NONE)
                cNotEvaluated[idx].await();
            rl.unlock();

            // evaluation time
            Thread.sleep((int) Math.floor(Math.random() * evt));
            EDoS dos = EDoS.values()[new Random().nextInt(EDoS.values().length - 1)];
            rooms[idx].setDos(dos);

            rl.lock();
            // allow Patient to move on
            evaluated[idx] = true;
            cNotEvaluated[idx].signal();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            rl.unlock();
        }
    }

    private boolean isFull() {
        return patientCount >= maxPatients;
    }
}
