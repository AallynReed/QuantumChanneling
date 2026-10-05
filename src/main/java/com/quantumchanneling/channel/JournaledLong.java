package com.quantumchanneling.channel;

import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A long whose changes made inside a transaction roll back when that transaction aborts. The
 * commit hook runs once the outermost transaction commits with a changed value.
 */
public final class JournaledLong extends SnapshotJournal<Long> {
    private final Runnable onCommit;
    private long value;

    public JournaledLong(Runnable onCommit) {
        this.onCommit = onCommit;
    }

    public long get() { return value; }

    public void add(long delta, TransactionContext tx) {
        updateSnapshots(tx);
        value += delta;
    }

    /** Outside any transaction only — used when loading saved state. */
    public void set(long v) { value = v; }

    @Override
    protected Long createSnapshot() { return value; }

    @Override
    protected void revertToSnapshot(Long snapshot) { value = snapshot; }

    @Override
    protected void onRootCommit(Long original) {
        if (original != value) onCommit.run();
    }
}
