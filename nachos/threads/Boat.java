package nachos.threads;

import nachos.machine.*;
import nachos.ag.BoatGrader;
import java.util.LinkedList;

public class Boat {
    static BoatGrader bg;

    public static void selfTest() {
        BoatGrader b = new BoatGrader();

        System.out.println("\n ***Testing Boats with only 2 children***");
        begin(0, 2, b);

        System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
        begin(1, 2, b);

        // System.out.println("\n ***Testing Boats with 3 children, 3
        // adults***");
        // begin(3, 3, b);
    }

    public static void begin(int adults, int children, BoatGrader b) {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;
        World world = new World(bg);

        // Instantiate global variables here

        // Create threads here. See section 3.4 of the Nachos for Java
        // Walkthrough linked from the projects page.

        LinkedList<KThread> allThreads = new LinkedList<KThread>();
        for (int i = 0; i < adults; ++i) {
            KThread t = new KThread(new Adult(world));
            allThreads.add(t);
        }
        for (int i = 0; i < children; ++i) {
            KThread t = new KThread(new Child(world));
            allThreads.add(t);
        }

        for (KThread t : allThreads) {
            t.fork();
        }
        for (KThread t : allThreads) {
            t.join();
        }

        Lib.assertTrue(world.molokai.getNumAdult() == adults);
        Lib.assertTrue(world.molokai.getNumChild() == children);
    }

    static void AdultItinerary() {
        bg.initializeAdult(); // Required for autograder interface. Must be the
                              // first thing called.
        // DO NOT PUT ANYTHING ABOVE THIS LINE.

        /*
         * This is where you should put your solutions. Make calls to the
         * BoatGrader to show that it is synchronized. For example:
         * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
         * across to Molokai
         */
    }

    static void ChildItinerary() {
        bg.initializeChild(); // Required for autograder interface. Must be the
                              // first thing called.
        // DO NOT PUT ANYTHING ABOVE THIS LINE.
    }

    static void SampleItinerary() {
        // Please note that this isn't a valid solution (you can't fit
        // all of them on the boat). Please also note that you may not
        // have a single thread calculate a solution and then just play
        // it back at the autograder -- you will be caught.
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }

    static class World {
        public World(BoatGrader grader) {
            this.grader = grader;
            this.boat = new TheBoat(this);
        }

        public BoatGrader grader;
        public Island oahu = new Island();
        public Island molokai = new Island();
        public TheBoat boat;

        public Lock readyLock = new Lock();
        private Lock announceNewComerLock = new Lock();
        private Condition announceNewComerCond = new Condition(announceNewComerLock);
        private boolean needBack = false;

        public void announceNewComer(boolean needBack) {
            announceNewComerLock.acquire();
            this.needBack = needBack;
            announceNewComerCond.wakeAll();
            announceNewComerLock.release();
        }

        public boolean waitNewComer() {
            announceNewComerLock.acquire();
            announceNewComerCond.sleep();
            boolean needBack = this.needBack;
            announceNewComerLock.release();
            return needBack;
        }
    }

    static class Island {
        public int getNumAdult() {
            return numAdult;
        }
        public int getNumChild() {
            return numChild;
        }

        public void enter(Person p) {
            mutex.acquire();
            if (p.isAdult()) {
                numAdult++;
            } else {
                numChild++;
            }
            mutex.release();
        }

        public void exit(Person p) {
            mutex.acquire();
            if (p.isAdult()) {
                numAdult--;
            } else {
                numChild--;
            }

            Lib.assertTrue(numAdult >= 0 && numChild >= 0);
            mutex.release();
        }

        private Lock mutex = new Lock();
        private int numAdult = 0;
        private int numChild = 0;
    }

    static class TheBoat {
        public TheBoat(World world) {
            this.world = world;
            this.island = world.oahu;
        }

        public int tryGetOn(Person other, boolean pilotOnly) {
            mutex.acquire();
            int rc = 0;

            if (other.getIsland() == island) {
                if (pilot == null) {
                    pilot = other;
                    rc = 1;
                } else if (!pilotOnly && passenger == null && pilot.isChild() && other.isChild()) {
                    passenger = other;
                    rc = 2;
                }
            }

            mutex.release();

            return rc;
        }

        public void go(Person me) {
            mutex.acquire();
            Lib.assertTrue(pilot == me);

            if (pilot.isAdult()) {
                if (island == world.oahu) {
                    world.grader.AdultRowToMolokai();
                } else {
                    world.grader.AdultRowToOahu();
                }
            } else {
                if (island == world.oahu) {
                    world.grader.ChildRowToMolokai();
                } else {
                    world.grader.ChildRowToOahu();
                }
            }

            if (passenger != null) {
                if (passenger.isAdult()) {
                    if (island == world.oahu) {
                        world.grader.AdultRideToMolokai();
                    } else {
                        world.grader.AdultRideToOahu();
                    }
                } else {
                    if (island == world.oahu) {
                        world.grader.ChildRideToMolokai();
                    } else {
                        world.grader.ChildRideToOahu();
                    }
                }
            }

            Island nextIsland = island == world.oahu ? world.molokai : world.oahu;
            Condition nextCondition = island == world.oahu ? waitMolokai : waitOahu;

            pilot.setIsland(nextIsland);
            if (passenger != null) {
                passenger.setIsland(nextIsland);
                waitPassenger.wake();
            }
            nextCondition.wakeAll();

            this.island = nextIsland;
            this.pilot = null;
            this.passenger = null;

            mutex.release(); 
        }

        public void waitGoPassenger(Person me) {
            mutex.acquire();
            Lib.assertTrue(passenger == me);
            waitPilot.wake();
            waitPassenger.sleep();
            mutex.release();
        }

        public void waitGoPilot(Person me) {
            mutex.acquire();
            Lib.assertTrue(pilot == me);
            waitPilot.sleep();
            mutex.release();
        }

        public void waitOnOahu(Person me) {
            mutex.acquire();
            Lib.assertTrue(me.getIsland() == world.oahu);
            if (island != world.oahu) {
                waitOahu.sleep();
            }
            mutex.release();
        }

        public void waitOnMolokai(Person me) {
            mutex.acquire();
            Lib.assertTrue(me.getIsland() == world.molokai);
            if (island != world.molokai) {
                waitMolokai.sleep();
            }
            mutex.release();
        }
        
        private World world;
        private Island island;
        private Lock mutex = new Lock();
        private Condition waitPilot = new Condition(mutex);
        private Condition waitPassenger = new Condition(mutex);
        private Condition waitOahu = new Condition(mutex);
        private Condition waitMolokai = new Condition(mutex);
        private Person pilot, passenger;
    }

    static class Person implements Runnable {
        public Person(int type, World world) {
            Lib.assertTrue(type >= 0 && type <= 1);
            this.type = type;
            this.world = world;
            this.setIsland(world.oahu);
        }

        public boolean isAdult() {
            return type == 0;
        }
        public boolean isChild() {
            return type == 1;
        }

        public World getWorld() {
            return world;
        }

        public TheBoat getBoat() {
            return world.boat;
        }

        public Island getIsland() {
            Lib.assertTrue(island != null);
            return island;
        }

        public void setIsland(Island newIsland) {
            if (island != null) {
                island.exit(this);
            }
            newIsland.enter(this);
            island = newIsland;
        }

        public void run() {

        }

        private int type = -1;
        private Island island;
        private World world;
    }

    static class Adult extends Person {
        public Adult(World world) {
            super(0, world);
        }
    
        public void run() {
            getWorld().grader.initializeAdult();
            TheBoat boat = getBoat();

            // I'm on oahu
            while (true) {
                if (getIsland().getNumChild() < 2) {
                    int rc = boat.tryGetOn(this, true);
                    if (rc == 1) { // I have became the pilot
                        break;
                    }
                }
                boat.waitOnOahu(this);
            }

            boolean needBack = true;
            if (getIsland().getNumAdult() == 1 && getIsland().getNumChild() == 0) {
                needBack = false;
            }

            boat.go(this);
            getWorld().announceNewComer(needBack);
        }
    }

    static class Child extends Person {
        public Child(World world) {
            super(1, world);
        }
        
        public void run() {
            getWorld().grader.initializeChild();
            while (true) {
                boolean exit;
                if (getIsland() == getWorld().oahu) {
                    exit = onChildOahu();
                } else {
                    exit = onChildMolokai();
                }
                if (exit) {
                    break;
                }
            }
        }

        private boolean onChildOahu() {
            TheBoat boat = getBoat();

            if (getIsland().getNumChild() == 1) {
                boat.waitOnOahu(this);
                return false;
            }

            int rc = boat.tryGetOn(this, false);
                
            if (rc == 1) {
                //if (getIsland().getNumChild() > 1) {
                boat.waitGoPilot(this);
                //}
    
                boolean needBack = true;
                if (getIsland().getNumChild() <= 2 && getIsland().getNumAdult() == 0) {
                    needBack = false;
                }
    
                boat.go(this);
                getWorld().announceNewComer(needBack);
                isLastPilot = true;
    
                return !needBack;
            } else if (rc == 2) {
                boolean needBack = true;
                if (getIsland().getNumChild() <= 2 && getIsland().getNumAdult() == 0) {
                    needBack = false;
                }
                boat.waitGoPassenger(this);
                return !needBack;
            } else {
                boat.waitOnOahu(this);
            }
    
            return false;
        }
    
        private boolean onChildMolokai() {
            TheBoat boat = getBoat();

            boolean needBack;
            if (isLastPilot) {
                needBack = true;
                isLastPilot = false;
            } else {
                needBack = getWorld().waitNewComer();
            }
    
            if (needBack) {
                int rc = boat.tryGetOn(this, true);
                if (rc == 1) {
                    boat.go(this);
                } else {
                    Lib.assertTrue(rc == 0);
                }
                return false;
            } else {
                return true;
            }
        }

        private boolean isLastPilot = false;
    }
}

