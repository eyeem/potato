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
      String id;
      String text;
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
      s.init(100);
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
}