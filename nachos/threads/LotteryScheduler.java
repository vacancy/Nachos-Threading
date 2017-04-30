package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }

    /**
     * Allocate a new lottery thread queue.
     *
     * @param transferPriority
     *            <tt>true</tt> if this queue should transfer tickets from
     *            waiting threads to the owning thread.
     * @return a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new LotteryQueue(transferPriority);
    }

    public static int getPriorityMaximum(){
        return Integer.MAX_VALUE;
    }

    public static int add(long x, long y){
        return (int)Math.min(getPriorityMaximum(), x + y);
    }

    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new LotteryThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    protected class LotteryQueue extends PriorityScheduler.PriorityQueue {
        LotteryQueue(boolean transferPriority) {
            super(transferPriority);
            sumPriority = 0;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            
            ThreadState state = getThreadState(thread);
            ThreadPriorityRecord record = new ThreadPriorityRecord(state, state.getEffectivePriority());

            queue.add(record);
            sumPriority = add(sumPriority, record.getPriority());
            stateToRecord.put(record.getState(), record);

            if (acquiredState != null) {
                acquiredState.updateEffectivePriority();
            }

            state.waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());

            ThreadState state = getThreadState(thread);

            if (acquiredState != null) {
                acquiredState.release(this);
            }

            if (stateToRecord.containsKey(state)) {
                ThreadPriorityRecord record = stateToRecord.get(state);
                boolean success = queue.remove(record);
                if (success){
                    sumPriority = add(sumPriority, -record.getPriority());
                }
                stateToRecord.remove(state);
            }
            
            this.acquiredState = state;
            state.acquire(this);
        }

        protected ThreadState pickNextThread() {
            if (!queue.isEmpty()) {
                ThreadPriorityRecord[] array = queue.toArray(new ThreadPriorityRecord[0]);
                int sum = 0;
                for (ThreadPriorityRecord i : array) {
                    sum = add(sum, i.getPriority() + 1);
                }
                int random = rng.nextInt(sum);
                ThreadPriorityRecord record = null;
                for (ThreadPriorityRecord i : array) {
                    random -= i.getPriority() + 1;
                    if (random < 0) {
                        record = i;
                    }
                }
                Lib.assertTrue(record != null);
                sumPriority = add(sumPriority, -record.getPriority());
                queue.remove(record);

                return record.getState();
            }
            return null;
        }

        public int getSumPriority() {
            return sumPriority;
        }

        public int getNaiveSumPriority() {
            ThreadPriorityRecord[] array = queue.toArray(new ThreadPriorityRecord[0]);
            int sum = 0;
            for (ThreadPriorityRecord i : array) {
                sum = add(sum, i.getPriority());
            }
            return sum;
        }

        public void printQueue() {
            ThreadPriorityRecord[] array = queue.toArray(new ThreadPriorityRecord[0]);
            for (ThreadPriorityRecord i : array) {
                System.out.println("priority " + i.getPriority());
            }
        }

        public void updateEffectivePriority(ThreadState state) {
            boolean intStatus = Machine.interrupt().disable();

            ThreadPriorityRecord record = stateToRecord.get(state);

            sumPriority = add(sumPriority, state.effectivePriority);
            boolean success = queue.remove(record);
            if (success) {
                sumPriority = add(sumPriority, - record.getPriority());                 
            }
            record.setPriority(state.effectivePriority);
            queue.add(record);

            Machine.interrupt().restore(intStatus);
        }

        protected int sumPriority = 0;
        protected Random rng = new Random();
    }

    protected class LotteryThreadState extends PriorityScheduler.ThreadState {
        public LotteryThreadState(KThread thread) {
            super(thread);
        }

        public void updateEffectivePriority() {
            int sum = getPriority();
            for (PriorityQueue q : acquiredQueues) {
                sum = add(sum, ((LotteryQueue)q).getSumPriority());
            }

            if (getEffectivePriority() != sum) {
                setEffectivePriority(sum);
                if (waitingQueue != null) {
                    waitingQueue.updateEffectivePriority(this);
                }
            }
        }
    }
}
