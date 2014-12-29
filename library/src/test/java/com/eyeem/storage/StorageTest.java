package com.eyeem.storage;

import org.fest.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;

import static org.fest.assertions.api.ANDROID.assertThat;
import org.junit.Assert;

import java.util.concurrent.CountDownLatch;

@RunWith(RobolectricGradleTestRunner.class)
public class StorageTest {

   public static class Item {
      public Item() {}
      public Item(String id, String text) {
         this.id = id;
         this.text = text;
      }
      String id;
      String text;
   }

   private static Item _(String id) {
      return new Item(id, id);
   }

   public static Storage<Item> getStorage() {
      Storage<Item> s = new Storage<Item>(Robolectric.application){
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
      l1.add(_("ramz"));
      l1.add(_("ronaldo"));
      l1.add(_("martin"));

      s.push(_("tobi")); // this should get evicted
      s.retain(_("frank")); // this shouldn't

      Storage<Item>.List l1_transaction = l1.transaction();
      l1_transaction.add(_("phil"));

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
      s.push(_("jedrzej"));
      Storage.Subscription jedrzej_sub = new Storage.Subscription(){
         @Override public void onUpdate(Action action) {
            Assert.assertNotNull(action);
            Assert.assertEquals(Storage.Subscription.DELETE, action.name);
            s.push(_("foo"));
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
}