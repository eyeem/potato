package com.eyeem.storage;

import org.fest.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;

import static org.fest.assertions.api.ANDROID.assertThat;
import org.junit.Assert;

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

   // TODO write a test for a filterSelf thingy
}