package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param conditionLock
     *            the lock associated with this condition variable. The current
     *            thread must hold this lock whenever it uses <tt>sleep()</tt>,
     *            <tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
        this.conditionLock = conditionLock;
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The current
     * thread must hold the associated lock. The thread will automatically
     * reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        conditionLock.release();

        conditionLock.acquire();
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
    }

    private static class PingTest implements Runnable {
        PingTest(Lock pingLock, Condition2 ping, Lock pongLock, Condition2 pong) {
            this.pingLock = pingLock;
            this.ping = ping;
            this.pongLock = pongLock;
            this.pong = pong;
        }

        public void run() {
            for (int i = 0; i < 10; i++) {
                pingLock.acquire();
                ping.wakeAll();
                pongLock.acquire();

                pingLock.release();
                pong.sleep();
                pongLock.release();
            }
        }

        private Lock pingLock;
        private Lock pongLock;
        private Condition2 ping;
        private Condition2 pong;
    }

    /**
     * Test if this module is working.
     */
    public static void selfTest() {
        System.out.println("[test:Condition2] self test started");
        Lock pingLock = new Lock();
        Condition2 ping = new Condition2(pingLock);
        Lock pongLock = new Lock();
        Condition2 pong = new Condition2(pongLock);

        pingLock.acquire();
        KThread p = new KThread(new PingTest(pingLock, ping, pongLock, pong)).setName("ping");
        p.fork();

        for (int i = 0; i < 10; i++) {
            ping.sleep();
            pingLock.release();
            pongLock.acquire();
            pong.wakeAll();
            pingLock.acquire();
            pongLock.release();
        }
        pingLock.release();
        p.join();
        System.out.println("[test:Condition2] self test passed");
    }

    private Lock conditionLock;
}
