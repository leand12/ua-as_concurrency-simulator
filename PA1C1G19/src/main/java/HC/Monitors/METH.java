/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package HC.Monitors;

import HC.Data.ERoom;
import HC.Entities.TPatient;
import HC.Logging.Logging;
import HC.Main.GUI;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static HC.Data.ERoom.*;

/**
 * @author guids
 * Entrance Hall Monitor
 */
public class METH implements IETH_Patient, IETH_CallCenter {
    private final ReentrantLock rl;
    private Condition cNotBothEmpty;
    private Condition cNextETN;
    private final MFIFO adultFIFO;
    private final MFIFO childFIFO;
    private final Logging log;
    private final GUI gui;
    private final int NoS;

    private int ETN = 0; // Patient Number
    private int nextETN = 1;
    private int ttm;

    public METH(int NoS, int ttm, Logging log, GUI gui) {
        this.NoS = NoS / 2;
        this.ttm = ttm;
        this.log = log;
        this.gui = gui;

        rl = new ReentrantLock();
        cNotBothEmpty = rl.newCondition();
        cNextETN = rl.newCondition();
        adultFIFO = new MFIFO(rl, this.NoS);
        childFIFO = new MFIFO(rl, this.NoS);
    }

    /**
     * @return the FIFO that has the next priority patient
     */
    private MFIFO getPriorityFIFO(boolean isAdult) {
        try {
            rl.lock();
            while ((isAdult && adultFIFO.isEmpty()) || (!isAdult && childFIFO.isEmpty())) {
                cNotBothEmpty.await();
            }

            if (isAdult) {
                return adultFIFO;
            }
            return childFIFO;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            rl.unlock();
        }
    }

    @Override
    public void enterPatient(TPatient patient) {
        if (patient.isAdult()) {
            adultFIFO.put(patient, ET2);
        } else {
            childFIFO.put(patient, ET1);
        }
    }

    @Override
    public void callPatient(boolean isAdult) {
        getPriorityFIFO(isAdult).get();
    }

    class MFIFO {
        private final TPatient[] fifo;
        private final Condition[] cond;
        private final ReentrantLock rl;
        private final Condition cNotFull;
        private final Condition cNotEmpty;
        private final int size;
        private int idxPut = 0;
        private int idxGet = 0;
        private int count = 0;
        private final boolean permitted[];    // ensures a Patient keeps running if signal is performed before await

        public MFIFO(ReentrantLock rl, int size) {
            this.size = size;
            fifo = new TPatient[size];
            cond = new Condition[size];
            permitted = new boolean[size];

            this.rl = rl;
            cNotEmpty = rl.newCondition();
            cNotFull = rl.newCondition();
            for (var i = 0; i < cond.length; i++)
                cond[i] = rl.newCondition();
        }

        public void put(TPatient patient, ERoom room) {
            try {
                rl.lock();
                while (isFull()) cNotFull.await();
                count++;
                fifo[idxPut] = patient;
                int idx = idxPut;
                idxPut = (++idxPut) % size;
                cNotEmpty.signal();
                {
                    // assign ETN to patient
                    patient.setNN(++ETN);
                    cNotBothEmpty.signal();     // signal CallCenter

                    log.logPatient(ETH, patient);
                    gui.addPatient(ETH, patient);
                }
                rl.unlock();
                {
                    try {
                        Thread.sleep((int) Math.floor(Math.random() * ttm));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                rl.lock();
                // move from ETH to ETRi
                // ensure patients enter in ascending ETN, as the TTM is performed outside the lock
                while (patient.getNN() != nextETN) cNextETN.await();
                nextETN++;
                cNextETN.signalAll();
                {
                    log.logPatient(room, patient);
                    gui.addPatient(room, patient);
                }
                while (!permitted[idx]) cond[idx].await();
                permitted[idx] = false;
                cNotFull.signal(); // hmmmm
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                rl.unlock();
            }
        }

        public void get() {
            try {
                rl.lock();
                while (isEmpty()) cNotEmpty.await();
                count--;
                fifo[idxGet] = null;
                int idx = idxGet;
                idxGet = (++idxGet) % size;

                permitted[idx] = true;
                cond[idx].signal();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                rl.unlock();
            }
        }

        /* thread-unsafe access methods */
        /* should be called inside `rl` lock */

        public TPatient peek() {
            return fifo[idxGet];
        }

        public boolean isFull() {
            return count == size;
        }

        public boolean isEmpty() {
            return count == 0;
        }
    }
}

