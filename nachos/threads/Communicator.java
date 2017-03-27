package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param word
     *            the integer to transfer.
     */
    public void speak(int word) {
        mutex.acquire();

        while (hasCachedMessage) {
            // System.out.println(" **** sender: waiting cached message.");
            sleepingSpeakers.sleep();
            // System.out.println(" **** sender: awake from waiting cached message.");
        }
        hasCachedMessage = true;
        cachedMessage = word;
    
        sleepingListeners.wake();
        currentSpeaker.sleep();

        mutex.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return the
     * <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        mutex.acquire();

        while (!hasCachedMessage) {
            // System.out.println(" **** recver: waiting message.");
            sleepingListeners.sleep();
            // System.out.println(" **** recver: awake from waiting message.");
        }
        hasCachedMessage = false;
        int message = cachedMessage;
        
        currentSpeaker.wake();
        sleepingSpeakers.wake();

        mutex.release();
        return message;
    }

    private Lock mutex = new Lock();
    private Condition sleepingSpeakers = new Condition(mutex);
    private Condition sleepingListeners = new Condition(mutex);
    private Condition currentSpeaker = new Condition(mutex);

    private boolean hasCachedMessage;
    private int cachedMessage;

    private static class TestSender implements Runnable {
        public TestSender(Communicator c, int start) {
            this.c = c;
            this.start = start;
        }
        public void run() {
            for (int i = start; i < start + 10; ++i) {
                c.speak(i); 
            }
        }
        private Communicator c;
        private int start;
    }

    private static class TestReceiver implements Runnable {
        public TestReceiver(Communicator c, Lock lock, ArrayList<Integer> result) {
            this.c = c;
            this.lock = lock;
            this.result = result;
        }
        public void run() {
            for (int i = 0; i < 10; ++i) {
                int v = c.listen();
                lock.acquire();
                result.add(v);
                lock.release();
            }
        }

        private Communicator c;
        private Lock lock;
        private ArrayList<Integer> result;
    }

    private static void checkResult(ArrayList<Integer> result, int expectLength) {
        for (int i = 0; i < expectLength; ++i) {
            Lib.assertTrue(result.indexOf(i) != -1);
        }
    }

    private static void doPingpongTest() {
        System.out.println("[test:Communicator] pingpong test started");

        Communicator c = new Communicator();
        Lock lock = new Lock();
        ArrayList<Integer> result = new ArrayList<Integer>();

        KThread sender = new KThread(new TestSender(c, 0));
        KThread recver = new KThread(new TestReceiver(c, lock, result));

        sender.fork();
        recver.fork();
        sender.join();
        recver.join();

        checkResult(result, 10);

        System.out.println("[test:Communicator] pingpong test passed");
    }

    private static void doMutliAgentPingpongTest() {
        System.out.println("[test:Communicator] multi-agent pingpong test started");

        Communicator c = new Communicator();
        Lock lock = new Lock();
        ArrayList<Integer> result = new ArrayList<Integer>();

        KThread []allSenders = new KThread[10];
        for (int i = 0; i < 10; ++i) {
            allSenders[i] = new KThread(new TestSender(c, 10 * i));
            allSenders[i].fork();
        }

        KThread []allRecvers = new KThread[10];
        for (int i = 0; i < 10; ++i) {
            allRecvers[i] = new KThread(new TestReceiver(c, lock, result));
            allRecvers[i].fork();
        }

        for (int i = 0; i < 10; ++i) {
            allSenders[i].join();
            allRecvers[i].join();
        }

        checkResult(result, 100);

        System.out.println("[test:Communicator] multi-agent pingpong test passed");
    }

    public static void selfTest() {
        doPingpongTest();
        doMutliAgentPingpongTest();
    }
}

