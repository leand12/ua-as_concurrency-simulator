package HC.Monitors;

public interface IEVH_Nurse {
    /**
     * Assign a DoS to a patient.
     *
     * @param idx   the index from the room
     */
    void evaluatePatient(int idx);
}
