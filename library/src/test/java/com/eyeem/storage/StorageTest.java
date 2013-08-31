package com.eyeem.storage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;

import static org.fest.assertions.api.ANDROID.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
public class StorageTest {

   public static class Item {
      String id;
      String text;
   }

   @Test public void testInit() {
      Storage<Item> s = new Storage<Item>(Robolectric.application){
         @Override public Class<Item> classname() {
            return Item.class;
         }
         @Override public String id(Item item) {
            return item.id;
         }
      };
      s.init(100);
   }
}