package com.eyeem.poll;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import android.util.Log;

import com.eyeem.storage.Storage;
import com.eyeem.storage.Storage.List;


/**
 * Subclass of Poll taking care of paginated polling.
 *
 * @param <T>
 */
public abstract class PaginatedPoll<T> extends Poll<T> {

   /**
    * Limit of items to be returned per API call
    */
   protected int limit;

   public PaginatedPoll() {
      refreshPeriod = 5*60*1000; // 5 minutes
      limit = 30;
   }

   @Override
   protected int appendNewItems(ArrayList<T> newItems, boolean cleanUp) {
      int newCount = newItems.size();
      for (T item : newItems) {
         if (list.contains(item))
            newCount--;
      }

      if (Poll.DEBUG)  Log.i(getClass().getSimpleName(), "update, newCount = " + newCount);
      list.publish(new Storage.Subscription.Action(Storage.Subscription.WILL_CHANGE));
      Storage<T>.List transaction = list.transaction();
      if (cleanUp) {
         if (Poll.DEBUG)  Log.i(getClass().getSimpleName(), "cleanUp");
         transaction.clear();
         exhausted = false;
      }
      transaction.addAll(0, newItems);
      Storage.Subscription.Action action = new Storage.Subscription.Action(Storage.Subscription.ADD_UPFRONT);
      transaction.commit(action);
      return newCount;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   protected int appendOldItems(ArrayList<T> oldItems) {
      Log.i(getClass().getSimpleName(), "offset = "+offset());
      int count = oldItems.size();
      for (T item : oldItems) {
         if (list.contains(item))
            count--;
      }
      /*if (count > 0 && count + list.getStorage().currentSize() > list.getStorage().maxSize()) {
         if (Poll.DEBUG) Log.w(getClass().getSimpleName(), "Hitting storage capacity, trimming list.");
         String idBefore = listener.getLastVisibleId();
         Storage.List transaction = list.transaction();
         removedCount += transaction.makeGap(limit) + limit; // FIXME why +limit
         int removedCount = transaction.getStorage().retainList((List) transaction);
         if (Poll.DEBUG) Log.w(getClass().getSimpleName(), "Freed "+removedCount+" elements from storage.");
         list.publish(new Storage.Subscription.Action(Storage.Subscription.WILL_CHANGE));
         transaction.addAll(oldItems);
         transaction.commit();
         listener.onTrim(idBefore);
      } else {*/
         list.publish(new Storage.Subscription.Action(Storage.Subscription.WILL_CHANGE));
         list.addAll(oldItems);
         if (count == 0)
            exhausted = true;
      //}
      return count;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public void setStorage(Storage.List list) {
      list.enableDedupe(true);
      list.enableSort(comparator());
      super.setStorage(list);
   }

   /**
    * Override and return {@link Comparator} object if you wish
    * to sort items on the list.
    * @return {@link Comparator} instance or null for no sorting
    */
   protected abstract Comparator<T> comparator();

   /**
    * Pagination offset
    * @return pagination offset
    */
   public int offset() {
      return Math.max(list.size(), limit);
   }

   @Override
   public boolean okToSave() {
      return !list.isEmpty();
   }
}
