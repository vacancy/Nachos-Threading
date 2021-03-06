package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;
import java.util.Comparator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    private static class KThreadWaitingPair {
        KThreadWaitingPair(KThread thread, long finishTime) {
            this.thread = thread;
            this.finishTime = finishTime;
        }

        KThread getThread() {
            return thread;
        }

        long getFinishTime() {
            return finishTime;
        }

        private KThread thread;
        private long finishTime;
    }

    private static class KThreadWaitingPairComparator implements Comparator<KThreadWaitingPair> {
        @Override
        public int compare(KThreadWaitingPair x, KThreadWaitingPair y) {
            long delta = x.getFinishTime() - y.getFinishTime();
            
            return delta == 0 ? 0 : (delta > 0 ? -1 : 1);
        }
    }

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p>
     * <b>Note</b>: Nachos will not function correctly with more than one alarm.
     */
    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread that
     * should be run.
     */
    public void timerInterrupt() {
        boolean intStatus = Machine.interrupt().disable();

        long currentTime = Machine.timer().getTime();
        while (true) {
            KThreadWaitingPair top = waitQueue.peek();
            if (top != null && top.getFinishTime() <= currentTime) {
                waitQueue.poll().getThread().ready();                    
            } else {
                break;
            }
        }

        Machine.interrupt().restore(intStatus);
        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
     * in the timer interrupt handler. The thread must be woken up (placed in
     * the scheduler ready set) during the first timer interrupt where
     *
     * <p>
     * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
     *
     * @param x
     *            the minimum number of clock ticks to wait.
     *
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        // for now, cheat just to get something working (busy waiting is bad)
        long wakeTime = Machine.timer().getTime() + x;
        KThreadWaitingPair pair = new KThreadWaitingPair(KThread.currentThread(), wakeTime);
    
        boolean intStatus = Machine.interrupt().disable();

        waitQueue.add(pair);
        KThread.sleep();

        Machine.interrupt().restore(intStatus);
    }

    KThreadWaitingPairComparator pqComparator = new KThreadWaitingPairComparator();
    PriorityQueue<KThreadWaitingPair> waitQueue = new PriorityQueue<KThreadWaitingPair>(pqComparator);

    private static class WaitingTest implements Runnable {
        public WaitingTest(Alarm alarm, long waitTime) {
            this.alarm = alarm;
            this.waitTime = waitTime;
        }

        public void run() {
            for (int i = 0; i < 5; ++i) {
                long startTime = Machine.timer().getTime();
                alarm.waitUntil(waitTime);
                long stopTime = Machine.timer().getTime();
                Lib.assertTrue(startTime + waitTime <= stopTime);
            }
        }

        private Alarm alarm;
        private long waitTime;
    }

    private static void doSingleWaitingTest() {
        System.out.println("[test:Alarm] single waiting test started");

        Alarm a = new Alarm();
        KThread t = new KThread(new WaitingTest(a, 500));
        t.fork();
        t.join();

        System.out.println("[test:Alarm] single waiting test passed");
    }

    private static void doMultipleWaitingTest() {
        System.out.println("[test:Alarm] multiple waiting test started");

        Alarm a = new Alarm();
        KThread []pool = new KThread[5];
        for (int i = 0; i < 5; ++i) {
            pool[i] = new KThread(new WaitingTest(a, 500 + i * 500));
            pool[i].fork();
        }
        for (int i = 0; i < 5; ++i) {
            pool[i].join();
        }

        System.out.println("[test:Alarm] multiple waiting test passed");
    }

    public static void selfTest() {
        doSingleWaitingTest();
        doMultipleWaitingTest();
    }
}

