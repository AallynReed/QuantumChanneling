package com.quantumchanneling.channel;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** An int whose changes made inside a transaction roll back when that transaction aborts. */
public final class JournaledInt extends SnapshotJournal<Integer> {
    private int value;

    public int get() { return value; }

    public void add(int delta, TransactionContext tx) {
        updateSnapshots(tx);
        value += delta;
    }

    /** Outside any transaction only — used for per-tick resets. */
    public void reset() { value = 0; }

    @Override
    protected Integer createSnapshot() { return value; }

    @Override
    protected void revertToSnapshot(Integer snapshot) { value = snapshot; }
}
