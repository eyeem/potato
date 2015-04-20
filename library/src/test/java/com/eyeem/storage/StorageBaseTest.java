package com.eyeem.storage;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.junit.Assert;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Comparator;

import java.util.concurrent.CountDownLatch;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, emulateSdk = 21)
public class StorageBaseTest {

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

   @Test public void testInit() {
      Storage<Item> s = getStorage();

      Assert.assertNotNull(s);
      Assert.assertNotNull(s.obtainList("test"));
   }

   @Test public void testDelete() {
      Storage<Item> s = getStorage();

      Storage<Item>.List l = s.obtainList("test");

      int n = 5;
      for (int i = 0; i < n; i++) {
         Item item = new Item();
         item.id = String.valueOf(i);
         item.text = "This is item 1";
         l.add(item);
      }

      // list size should be n
      Assert.assertEquals(n, l.size());

      // let's delete 1st and 3rd item
      l.remove(2);
      l.remove(0);

      // list size should be n - 2
      Assert.assertEquals(n - 2, l.size());

      // now check the values
      Assert.assertEquals(l.get(0).id, "1");
      Assert.assertEquals(l.get(1).id, "3");
      Assert.assertEquals(l.get(2).id, "4");
   }

   @Test public void testEvict() {
      Storage<Item> s = getStorage();

      Storage<Item>.List l1 = s.obtainList("list_1");
      l1.add(__("ramz"));
      l1.add(__("ronaldo"));
      l1.add(__("martin"));

      s.push(__("tobi")); // this should get evicted
      s.retain(__("frank")); // this shouldn't

      Storage<Item>.List l1_transaction = l1.transaction();
      l1_transaction.add(__("phil"));

      // eviction occurs during obtaining List
      Storage<Item>.List l2 = s.obtainList("list_2");

      Assert.assertNotNull(s.get("ramz"));
      Assert.assertNotNull(s.get("ronaldo"));
      Assert.assertNotNull(s.get("martin"));
      Assert.assertNull(s.get("tobi"));
      Assert.assertNotNull(s.get("frank"));
      Assert.assertNotNull(s.get("phil"));
   }

   @Test public void testListRetainRecycle() {
      Storage<Item> s = getStorage();

      // list allocated without retain
      {
         s.obtainList("list_1");
         s.obtainList("list_2");
      }
      System.gc(); // clear up weak refs

      Assert.assertEquals(0, s.listCount());

      // test retaining
      {
         s.obtainList("list_1").retain();
         s.obtainList("list_1").retain();
         s.obtainList("list_2").retain();
      }
      System.gc(); // clear up weak refs

      Assert.assertEquals(2, s.listCount());
      Assert.assertEquals(2, s.obtainList("list_1").retainCount());

      // test recycling
      {
         s.obtainList("list_1").recycle();
         s.obtainList("list_2").recycle();
      }
      System.gc(); // clear up weak refs

      Assert.assertEquals(1, s.listCount());
      Assert.assertEquals(1, s.obtainList("list_1").retainCount());
   }

   @Test public void testDeleteSubscription() {
      final Storage<Item> s = getStorage();
      final CountDownLatch signal = new CountDownLatch(1);
      s.push(__("jedrzej"));
      Storage.Subscription jedrzej_sub = new Storage.Subscription(){
         @Override public void onUpdate(Action action) {
            Assert.assertNotNull(action);
            Assert.assertEquals(Storage.Subscription.DELETE, action.name);
            s.push(__("foo"));
            signal.countDown();
         }
      };
      s.subscribe("jedrzej", jedrzej_sub);
      s.delete("jedrzej");
      try {
         signal.await();
      } catch (InterruptedException e) {}
      Assert.assertNull(s.get("jedrzej"));
      Assert.assertNotNull(s.get("foo"));
   }

   @Test public void testFilterQuery() {
      Storage<Item> s = getStorage();

      Storage<Item>.List l = s.obtainList("test");

      // filter out all id's that id % 2 is 0
      Storage.Query<Item> query = new Storage.Query<Item>(){
         @Override public boolean eval(Item item) {
            return Integer.valueOf(item.id) % 2 != 0;
         }
      };

      final int n = 5;
      for (int i = 0; i < n; i++) {
         Item item = new Item();
         item.id = String.valueOf(i);
         item.text = "This is item 1";
         l.add(item);
      }

      // list size should be n
      Assert.assertEquals(n, l.size());

      // let's filterSelf with query
      l.filterSelf(query);

      // list size should be n - 3
      Assert.assertEquals(n - 3, l.size());

      // now check the values
      Assert.assertEquals(l.get(0).id, "1");
      Assert.assertEquals(l.get(1).id, "3");
   }

   @Test public void testSort() {
      Storage<Item> s = getStorage();

      Storage<Item>.List l = s.obtainList("test");

      // random order
      l.add(__("F"));
      l.add(__("G"));
      l.add(__("A"));
      l.add(__("B"));
      l.add(__("H"));
      l.add(__("D"));

      // list size should be 6
      Assert.assertEquals(6, l.size());

      // sort
      l.sortSelf(new Comparator<Item>(){
         @Override
         public int compare(Item lhs, Item rhs) {
            return lhs.text.compareTo(rhs.text);
         }
      });

      // alphabetical order
      Assert.assertEquals(l.get(0).id, "A");
      Assert.assertEquals(l.get(1).id, "B");
      Assert.assertEquals(l.get(2).id, "D");
      Assert.assertEquals(l.get(3).id, "F");
      Assert.assertEquals(l.get(4).id, "G");
      Assert.assertEquals(l.get(5).id, "H");

      // list size should be 6
      Assert.assertEquals(6, l.size());
   }

   @Test public void testRetainNull() {
      Storage<Item> s = getStorage();
      s.retain(null); // this call shouldn't throw, instead should be ignored
   }
}