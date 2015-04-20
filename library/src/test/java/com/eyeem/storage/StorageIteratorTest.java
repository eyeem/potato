package com.eyeem.storage;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Iterator;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, emulateSdk = 21)
/**
 * NOTE: This class is adapted from here: http://cs.gmu.edu/~pammann/637/javaIDM/idmTests.java
 *
 * This class contains 13 JUnit tests for the Iterator interface. The tests are derived from an
 * IDM (input domain modeling) based on the JavaDoc API for Iterator.
 * The three methods tested are: hasNext(), next(), remove()
 * The following characteristics have been identified and are used to generate tests for the 
 * methods: 
 * C1-T: iterator has more values 
 * C2-T: iterator returns a non-null object reference 
 * C3-T: remove() is supported
 * C4-T: remove() precondition is satisfied
 * C5-T: collection in consistent state while iterator in use 
 */
public class StorageIteratorTest {

   public static class Item {
      public Item() {}
      public Item(String id, String text) {
         this.id = id;
         this.text = text;
      }
      String id;
      String text;

      @Override public boolean equals(Object o) {
         if (o instanceof Item)
            return ((Item) o).id.equals(id);
         else
            return super.equals(o);
      }
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

   public static Storage<Item>.List testList() {
      return getStorage().obtainList("test");
   }

   @Test
   /**
    * Test 1 of hasNext(): testHasNext_BaseCase()
    * Characteristics and Test Frames: C1-T, C5-T
    * C1-T: iterator has more values - hasNext() returns true
    * C5-T: collection in consistent state while iterator in use - 
    *   hasNext() does not result in a ConcurrentModificationException
    */
   public final void testHasNext_BaseCase() throws ConcurrentModificationException 
   {
      List s = testList();
      s.add(__("cat"));
      s.add(__("dog"));
      Iterator itr = s.iterator();
      assertTrue(itr.hasNext()); 
   }//TestHasNext_BaseCase
   
   /**
    * Test 2 of hasNext(): testHasNext_C1()
    * Characteristics and Test Frames: C1-F, C5-T 
    * C1-F: iterator is empty - hasNext() returns false
    * C5-T: collection in consistent state while iterator in use - 
    *   hasNext() does not result in a ConcurrentModificationException
    */
   public final void testHasNext_C1() throws ConcurrentModificationException
   {
      List s = testList();
      Iterator itr = s.iterator();
      assertFalse(itr.hasNext()); 
   }//TestHasNext_C1
   
   /**
    * Test 3 of hasNext(): testHasNext_C5()
    * Characteristics and Test Frames: C1-T, C5-F
    * C1-T: iterator has more values - hasNext() does not return false
    * C5-F: collection not in inconsistent state while iterator in use - 
    *   hasNext() DOES NOT results in a ConcurrentModificationException
    */
   @Test
   public final void testHasNext_C5() 
   {
      List s = testList();
      s.add(__("cat"));
      Iterator itr = s.iterator();
      s.add(__("dog"));
      assertTrue(itr.hasNext());
      itr.next();
      assertFalse(itr.hasNext());
   }//TestHasNext_C5
   
   
   
   /** 4 Tests for Iterator method next() 
    *    The 3 characteristics associated with next() are:
    *  C1, C2, C5
    **/
   
   /**
    * Test 1 of next(): testNext_BaseCase()
    * Characteristics and Test Frames: C1-T, C2-T, C5-T
    * C1-T: iterator has more values - hasNext() returns true
    * C2-T: iterator rtns a non-null obj ref - next() returns non-Null value
    * C5-T:  collection in consistent state while iterator in use - 
    *    next() does not result in a ConcurrentModificationException
    */
   @Test
   public final void testNext_BaseCase() throws ConcurrentModificationException 
   {
      List s = testList();
      s.add(__("cat"));
      Iterator itr = s.iterator();
      assertTrue(itr.hasNext()); 
      assertEquals(__("cat"), itr.next());
   }//testNext_BaseCase
   
   /**
    * Test 2 of next(): testNext_C1()
    * Characteristics and Test Frames: C1-F, C2-F, C5-T
    * C1-F: iterator is empty - hasNext() returns false
    * C2-F: iterator rtns a non-null obj ref - 
    *    next() results in a NoSuchElementException
    * C5-T: collection in consistent state while iterator in use - 
    *    next() does not result in a ConcurrentModificationException
    */
   @Test(expected=NoSuchElementException.class)
   public final void testNext_C1() throws ConcurrentModificationException 
   {
      List s = testList();
      Iterator itr = s.iterator();
      assertFalse(itr.hasNext());
      itr.next();
   }//testNext_C1 
   
   /**
    * Test 3 of next(): testNext_C2()
    * Characteristics and Test Frames: C1-T, C2-F, C5-T
    * C1-T: iterator has more values - hasNext() returns true
    * C2-F: iterator rtns a null -  next() returns Null 
    * C5-T: collection in consistent state while iterator in use - 
    *    next() does not result in a ConcurrentModificationException
    */
   @Test(expected=NullPointerException.class)
   public final void testNext_C2() throws ConcurrentModificationException 
   {
      List s = testList();
      s.add(null); // null objects are not tolerated by potato
   }//testNext_C2
   
   /**
    * Test 4 of next(): testNext_C5()
    * Characteristics and Test Frames: C1-T, C2-dc, C5-F
    * C1-T:  iterator has more values - hasNext() returns true
    * C2-dc:  iterator rtns a non-null obj ref - 
    *    due to the thrown exception, the return value doesn't matter
    * C5-F:  collection not in consistent state while iterator in use - 
    *    next() DOES NOT results in a ConcurrentModificationException
    */
   @Test
   public final void testNext_C5()  
   {
      List s = testList();
      s.add(__("cat"));
      Iterator itr = s.iterator();
      assertTrue(itr.hasNext());
      s.add(__("dog"));
      itr.next();
      assertFalse(itr.hasNext());    
   }//testNext_C5
   
   
   
   /** 6 Tests for Iterator method remove() 
    * The 5 characteristics associated with remove() are:
    *  C1, C2, C3, C4, C5
    **/  
   
   /**
    * Test 1 of remove(): testRemove_BaseCase()
    * Characteristics and Test Frames: C1-T, C2-T, C3-T, C4-T, C5-T
    * C1-T: iterator has more values - hasNext() returns true
    * C2-T: iterator rtns a non-null obj ref - next() returns non-Null value
    * C3-T: remove() is supported - remove() does not result in an UnsupportedOperationException
    * C4-T: remove() precondition is satisfied - remove() does not result in an IllegalStateException
    * C5-T: collection in consistent state while iterator in use - remove() does not result in a ConcurrentModificationException
    *
    * POTATO NOTE: lists rely on CopyOnWriteArrayList so Iterator.remove is not supported
    */
   @Test(expected=UnsupportedOperationException.class)
   public final void testRemove_BaseCase() throws UnsupportedOperationException, 
   IllegalStateException, ConcurrentModificationException   
   {
      List s = testList();
      s.add(__("cat"));
      Iterator itr = s.iterator();
      assertTrue(itr.hasNext()); 
      assertEquals(__("cat"), itr.next());
      itr.remove();
   }//testNext_BaseCase
   
   /**
    * Test 2 of remove(): testRemove_C1()
    * Characteristics and Test Frames: C1-F, C2-F, C3-T, C4-T, C5-T
    * C1-F: iterator is empty - hasNext() returns false
    * C2-F: iterator does not return a non-null obj ref - next() results 
    * in a NoSuchElementException that is caught to allow the test to continue
    * C3-T: remove() is supported - remove() does not result in an UnsupportedOperationException
    * C4-T: remove() precondition is satisfied - remove() does not result in an IllegalStateException
    * C5-T: collection in consistent state while iterator in use - 
    *    remove() does not result in a ConcurrentModificationException
    *
    * POTATO NOTE: null objects are not allowed by potato
    */
   @Test(expected=NullPointerException.class)
   public final void testRemove_C1() throws UnsupportedOperationException, 
   IllegalStateException, ConcurrentModificationException, NoSuchElementException 
   {
      Boolean nextState = false;
      
      List s = testList();
      s.add(null);
      Iterator itr = s.iterator();
      assertNull(itr.next());
      assertFalse(itr.hasNext());
      
      try {
            itr.next(); 
        } catch (NoSuchElementException expected) {
         nextState = true;
        }
        assertTrue(nextState);   //verify that next() resulted in a NSEE
      
      itr.remove();
   }//testNext_C1 
   
   /**
    * Test 3 of remove(): testRemove_C2()
    * Characteristics and Test Frames: C1-T, C2-F, C3-T, C4-T, C5-T
    * C1-T: iterator has more values - hasNext() returns true
    * C2-F: iterator rtns a null - next() returns null
    * C3-T: remove() is supported - remove() does not result in an UnsupportedOperationException
    * C4-T: remove() precondition is satisfied - remove() does not result in an IllegalStateException
    * C5-T: collection in consistent state while iterator in use - 
    *    remove() does not result in a ConcurrentModificationException
    *
    * POTATO NOTE: null objects are not allowed by potato
    */
   @Test(expected=NullPointerException.class)
   public final void testRemove_C2() throws UnsupportedOperationException, 
   IllegalStateException, ConcurrentModificationException 
   {
      List s = testList();
      s.add(null);
      Iterator itr = s.iterator();
      assertTrue(itr.hasNext()); 
      assertNull(itr.next());
      itr.remove();
   }//testRemove_C2
   
   /**
    * Test 4 of remove(): testRemove_C3()
    * Characteristics and Test Frames: C1-T, C2-T, C3-F, C4-T, C5-T
    * C1-T: iterator has more values - hasNext() returns true
    * C2-T: iterator rtns a non-null obj ref - next() returns null
    * C3-T: remove() is not supported - remove() results in an UnsupportedOperationException
    * C4-T: remove() precondition is satisfied - remove() does not result in an IllegalStateException
    * C5-T: collection in consistent state while iterator in use - 
    *    remove() does not result in a ConcurrentModificationException
    */
   @Test(expected=UnsupportedOperationException.class)
   public final void testRemove_C3() throws IllegalStateException, 
     ConcurrentModificationException 
   {
      //AbstractList aL = testList();//new ArrayList(sA);
      List t = testList();
      t = java.util.Arrays.asList(__("cat"));
      Iterator itr = t.iterator();
      assertTrue(itr.hasNext()); 
      assertEquals(__("cat"), itr.next());
      itr.remove();
   }//testRemove_C3
   
   /**
    * Test 5 of remove(): testRemove_C4()
    * Characteristics and Test Frames: C1-T, C2-T, C3-T, C4-F, C5-T
    * C1-T: iterator has more values - hasNext() returns true
    * C2-T: iterator rtns a non-null obj ref - next() returns non-Null value
    * C3-T: remove() is supported - remove() does not result in an UnsupportedOperationException
    * C4-F: remove() precondition is not satisfied - remove() results in an IllegalStateException
    * C5-T: collection in consistent state while iterator in use - remove() does not result in a ConcurrentModificationException
    *
    * POTATO NOTE: List depends on CopyOnWriteArrayList so this is not supported
    */
   @Test(expected=UnsupportedOperationException.class)
   public final void testRemove_C4() throws UnsupportedOperationException, 
    ConcurrentModificationException 
   {
      List s = testList();
      s.add(__("cat"));
      Iterator itr = s.iterator();
      assertTrue(itr.hasNext()); 
      assertEquals(__("cat"), itr.next());
      itr.remove();
      itr.remove();
   }//testRemove_C4
      
      
   /**
    * Test 6 of next(): testRemove_C5()
    * Characteristics and Test Frames: C1-T, C2-T, C3-T, C4-T, C5-F
    * C1-T: iterator has more values - hasNext() returns true
    * C2-T: iterator rtns a non-null obj ref - next() returns non-Null value
    * C3-T: remove() is supported - remove() does not result in an UnsupportedOperationException
    * C4-T: remove() precondition is satisfied - remove() does not result in an IllegalStateException
    * C5-F: collection in inconsistent state while iterator in use - 
    *    remove() results in a ConcurrentModificationException
    *
    * POTATO NOTE: List depends on CopyOnWriteArrayList so this is not supported
    */
   @Test(expected=UnsupportedOperationException.class)
   public final void testRemove_C5() throws UnsupportedOperationException, 
   IllegalStateException, ConcurrentModificationException  
   {
      List s = testList();
      s.add(__("cat"));
      Iterator itr = s.iterator();
      assertTrue(itr.hasNext());
      itr.next();    
      s.add(__("dog"));
      itr.remove();
   }//testRemove_C5

}