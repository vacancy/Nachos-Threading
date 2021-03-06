package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority
     *            <tt>true</tt> if this queue should transfer priority from
     *            waiting threads to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum && priority <= getPriorityMaximum());

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == getPriorityMaximum())
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * A <tt>PQTestNoBlockLock</tt> is a synchronization primitive that has two states,
     * <i>busy</i> and <i>free</i>. There are only two operations allowed on a lock:
     *
     * <ul>
     * <li><tt>acquire()</tt>: try to get the lock. return true if <i>free</i> and
     * then set it to <i>busy</i>. return false if <i>busy</i>.
     * <li><tt>release()</tt>: set the lock to be <i>free</i>, waking up one waiting
     * thread if possible.
     * </ul>
     */
    public static class PQTestNoBlockLock {
        /**
         * Allocate a new lock. The lock will initially be <i>free</i>.
         */
        public PQTestNoBlockLock() {
        }

        /**
         * Atomically acquire this lock. The current thread must not already hold
         * this lock.
         *
         * @return true if the successfully acquire the lock.
         */
        public boolean acquire() {
            Lib.assertTrue(!isHeldByCurrentThread());

            boolean intStatus = Machine.interrupt().disable();
            KThread thread = KThread.currentThread();

            if (lockHolder != null) {
                Machine.interrupt().restore(intStatus);
                return false;
            } else {
                waitQueue.acquire(thread);
                lockHolder = thread;
            }

            Lib.assertTrue(lockHolder == thread);

            Machine.interrupt().restore(intStatus);

            return true;
        }

        /**
         * Atomically release this lock, allowing other threads to acquire it.
         */
        public void release() {
            Lib.assertTrue(isHeldByCurrentThread());

            boolean intStatus = Machine.interrupt().disable();

            if ((lockHolder = waitQueue.nextThread()) != null)
                lockHolder.ready();

            Machine.interrupt().restore(intStatus);
        }

        /**
         * Test if the current thread holds this lock.
         *
         * @return true if the current thread holds this lock.
         */
        public boolean isHeldByCurrentThread() {
            return (lockHolder == KThread.currentThread());
        }

        private KThread lockHolder = null;
        private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);
    }

    private static class PingTest implements Runnable {
        PingTest(PQTestNoBlockLock ping, Lock pong) {
            this.ping = ping;
            this.pong = pong;
        }

        public void run() {
            boolean flag = true;
            while (flag){
                pong.acquire();
                if (ping.acquire()){
                    ping.release();
                    flag = false;
                }
                pong.release();
            }
        }

        private PQTestNoBlockLock ping;
        private Lock pong;
    }

    private static void selfTestgetEffectivePriority(String name){
        boolean intStatus = Machine.interrupt().disable();
        System.out.println("*** " + name + " priority " +
            ((PriorityScheduler)ThreadedKernel.scheduler).getEffectivePriority(KThread.currentThread()));
        Machine.interrupt().restore(intStatus);
    }

    /**
     * Test if this module is working.
     */
    public static void selfTest() {
        System.out.println("[test:PriorityScheduler] self test started");

        PQTestNoBlockLock ping = new PQTestNoBlockLock();
        Lock pong = new Lock();

        ping.acquire();
        KThread p0 = new KThread(new PingTest(ping, pong)).setName("ping0");
        KThread p1 = new KThread(new PingTest(ping, pong)).setName("ping1");
        ((PriorityScheduler)ThreadedKernel.scheduler).decreasePriority();
        selfTestgetEffectivePriority("main @checkpoint 0");
        p0.fork();
        p1.fork();
        System.out.println("*** thread p0 and p1 forked");
        selfTestgetEffectivePriority("main @checkpoint 1");
        //KThread.yield();
        pong.acquire();
        selfTestgetEffectivePriority("main @checkpoint 2");
        pong.release();
        ping.release();
        p0.join();
        p1.join();
        System.out.println("*** thread p0 and p1 joined");
        selfTestgetEffectivePriority("main @checkpoint 3");
        ((PriorityScheduler)ThreadedKernel.scheduler).increasePriority();
        System.out.println("[test:PriorityScheduler] self test passed");
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;
    public static int getPriorityMaximum(){
        return priorityMaximum;
    }

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread
     *            the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            
            ThreadState state = getThreadState(thread);
            ThreadPriorityRecord record = new ThreadPriorityRecord(state, state.getEffectivePriority());
            int currentMax = getMaxPriority();

            queue.add(record);
            stateToRecord.put(record.state, record);

            if (currentMax < record.getPriority()) {
                if (acquiredState != null) {
                    acquiredState.updateEffectivePriority();
                }
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
                queue.remove(record);
                stateToRecord.remove(state);
            }
            
            this.acquiredState = state;
            state.acquire(this);
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());

            ThreadState nextThread = pickNextThread();
            if (nextThread != null) {
                acquire(nextThread.getThread());
                return nextThread.getThread();
            }

            if (acquiredState != null) {
                acquiredState.release(this);
                acquiredState = null;
            }
            return null;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would return.
         */
        protected ThreadState pickNextThread() {
            // ThreadPriorityRecord []tmp = queue.toArray(new ThreadPriorityRecord [0]);
            // System.out.println(this);
            // for (ThreadPriorityRecord r : tmp) {
            //     System.out.println("  gg " + r.getThread() + " " + r.getPriority() + " " + r.getTime());
            // }

            if (!queue.isEmpty()) {
                ThreadPriorityRecord record = queue.poll();
                return record.getState();
            }

            return null;
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }

        public int getMaxPriority() {
            boolean intStatus = Machine.interrupt().disable();

            ThreadPriorityRecord peek = queue.peek();
            if (peek == null) {
                return 0;
            }

            Machine.interrupt().restore(intStatus);
            return peek.getPriority();
        }

        public void updateEffectivePriority(ThreadState state) {
            boolean intStatus = Machine.interrupt().disable();

            ThreadPriorityRecord record = stateToRecord.get(state);
            queue.remove(record);
            record.setPriority(state.effectivePriority);
            queue.add(record);

            Machine.interrupt().restore(intStatus);
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        protected java.util.PriorityQueue<ThreadPriorityRecord> queue = 
            new java.util.PriorityQueue<ThreadPriorityRecord>();
        protected ThreadState acquiredState;
        protected HashMap<ThreadState, ThreadPriorityRecord> stateToRecord = 
            new HashMap<ThreadState, ThreadPriorityRecord>();
    }

    protected class ThreadPriorityRecord implements Comparable<ThreadPriorityRecord> {
        public ThreadPriorityRecord(ThreadState state, int priority) {
            this(state, priority, Machine.timer().getTime());
        }

        public ThreadPriorityRecord(ThreadState state, int priority, long time) {
            this.state = state;
            this.priority = priority;
            this.time = time;
        }

        @Override
        public int compareTo(ThreadPriorityRecord other) {
            if (this.getPriority() < other.getPriority()) {
                return 1;
            } else if (this.getPriority() == other.getPriority()) {
                // the threads that was first added into the queue have higher priority.
                if (this.time > other.time) {
                    return 1;
                } else if (this.time == other.time) {
                    return 0;
                }
                return -1;
            }
            return -1;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof ThreadPriorityRecord)) {
                return false;
            }
            ThreadPriorityRecord rhs = (ThreadPriorityRecord) other;
            return getThread().compareTo(rhs.getThread()) == 0;
        }

        public ThreadState getState() {
            return state;
        }

        public KThread getThread() {
            return state.getThread();
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
            this.time = Machine.timer().getTime();
        }

        public long getTime() {
            return this.time;
        }

        private ThreadState state;
        private int priority;
        private long time;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue it's
     * waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread
         *            the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;

            setPriority(priorityDefault);
            setEffectivePriority(priorityDefault);
        }

        public KThread getThread() {
            return thread;
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            // implement me
            return effectivePriority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority
         *            the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;

            this.priority = priority;
            
            updateEffectivePriority();
        }

        protected void setEffectivePriority(int effectivePriority) {
            this.effectivePriority = effectivePriority;
        }

        public void updateEffectivePriority() {
            int max = getPriority();
            for (PriorityQueue q : acquiredQueues) {
                if (q.getMaxPriority() > max) {
                    max = q.getMaxPriority();
                }
            }

            if (getEffectivePriority() != max) {
                setEffectivePriority(max);
                if (waitingQueue != null) {
                    waitingQueue.updateEffectivePriority(this);
                }
            }
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the resource
         * guarded by <tt>waitQueue</tt>. This method is only called if the
         * associated thread cannot immediately obtain access.
         *
         * @param waitQueue
         *            the queue that the associated thread is now waiting on.
         *
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            Lib.assertTrue(waitingQueue == null);
            waitingQueue = waitQueue;
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            if (waitingQueue != null) {
                Lib.assertTrue(waitingQueue == waitQueue);
                waitingQueue = null;
            }
            
            acquiredQueues.add(waitQueue);
            updateEffectivePriority();
        }

        public void release(PriorityQueue waitQueue) {
            acquiredQueues.remove(waitQueue);
            updateEffectivePriority();
        }

        /** The thread with which this object is associated. */
        protected KThread thread;
        /** The priority of the associated thread. */
        protected int priority;
        /** The effective prioirty of the associated thread.
         * Note that since the effective prioity is cached, It will be updated
         * only if there is a need to update it. */
        protected int effectivePriority;

        protected HashSet<PriorityQueue> acquiredQueues = new HashSet<PriorityQueue>();
        protected PriorityQueue waitingQueue;
    }
}
