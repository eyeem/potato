package com.eyeem.storage;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, emulateSdk = 21)
public class StorageConcurrentTest {

   public static class Item {
      public Item() {}
      public Item(String id, String text) {
         this.id = id;
         this.text = text;
      }
      String id;
      String text;
   }

   private static Item __(String id) {
      return new Item(id, id);
   }

   public static Storage<Item> getStorage() {
      Storage<Item> s = new Storage<Item>(RuntimeEnvironment.application){
         @Override public Class<Item> classname() {
            return Item.class;
         }
         @Override public String id(Item item) {
            return item.id;
         }
      };
      s.init();
      return s;
   }

   @Test public void testConcurrentModification() throws InterruptedException  {
      final Storage<Item> s = getStorage();

      final Storage<Item>.List l = s.obtainList("test");

      final int maxSize = 1000;

      // generate simple data
      final Runnable generator = new Runnable() {
         @Override public void run() {
            for (int i = 0; i < maxSize; i++) {
               Item item = new Item();
               item.id = String.valueOf(i);
               item.text = "This is item 1";
               l.add(item);
            }
         }
      };

      // enable duplicates removal on the list
      final Runnable deduper = new Runnable() {
         @Override public void run() {
            while (l.size() < maxSize/4) {
               // wait till we have already some duplicates so that dedupe will start removing stuff
            }
            l.enableDedupe(true);
         }
      };

      List<Runnable> runnables = java.util.Arrays.asList(
         generator,
         generator, // create 1st set of duplicates
         generator, // create 2nd set of duplicates
         deduper
      );
      assertConcurrent("concurrent execution", runnables, 5);
   }

   // copy pase from here: https://github.com/junit-team/junit/wiki/Multithreaded-code-and-concurrency
   public static void assertConcurrent(final String message, final List<? extends Runnable> runnables, final int maxTimeoutSeconds) throws InterruptedException {
      final int numThreads = runnables.size();
      final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
      final ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);
      try {
         final CountDownLatch allExecutorThreadsReady = new CountDownLatch(numThreads);
         final CountDownLatch afterInitBlocker = new CountDownLatch(1);
         final CountDownLatch allDone = new CountDownLatch(numThreads);
         for (final Runnable submittedTestRunnable : runnables) {
            threadPool.submit(new Runnable() {
               public void run() {
                  allExecutorThreadsReady.countDown();
                  try {
                     afterInitBlocker.await();
                     submittedTestRunnable.run();
                  } catch (final Throwable e) {
                     exceptions.add(e);
                  } finally {
                     allDone.countDown();
                  }
               }
            });
         }
         // wait until all threads are ready
         assertTrue("Timeout initializing threads! Perform long lasting initializations before passing runnables to assertConcurrent", allExecutorThreadsReady.await(runnables.size() * 10, TimeUnit.MILLISECONDS));
         // start all test runners
         afterInitBlocker.countDown();
         assertTrue(message +" timeout! More than" + maxTimeoutSeconds + "seconds", allDone.await(maxTimeoutSeconds, TimeUnit.SECONDS));
      } finally {
         threadPool.shutdownNow();
      }
      assertTrue(message + "failed with exception(s)" + exceptions, exceptions.isEmpty());
   }
}