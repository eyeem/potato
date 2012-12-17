package com.eyeem.poll;


import java.util.ArrayList;
import java.util.Comparator;

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

   /**
    * Number of items in the gap.
    */
   protected int removedCount;

   public PaginatedPoll() {
      refreshPeriod = 5*60*1000; // 5 minutes
      limit = 30;
      removedCount = 0;
   }

   @Override
   protected int appendNewItems(ArrayList<T> newItems, Poll.Listener listener) {
      int newCount = newItems.size();
      for (T item : newItems) {
         if (list.contains(item))
            newCount--;
      }

      if (Poll.DEBUG)  Log.w(getClass().getSimpleName(), "update, newCount = " + newCount);
      boolean removeGap = false;
      if (!list.isEmpty()) {
         String currentId = listener.getCurrentId();
         Storage<T> s = list.getStorage();
         T firstItem = list.get(0);
         if (currentId.equals(s.id(firstItem))) {
            // clear stuff on pull to refresh
            removeGap = true;
         } else if (removedCount > 0) {
            // MIND THE GAP if we're down the list
            for (T item : newItems) {
               if (currentId.equals(s.id(item))) {
                  removeGap = true;
                  break;
               }
            };
         }
      }
      Storage<T>.List transaction = list.transaction();
      if (removeGap) {
         if (Poll.DEBUG)  Log.w(getClass().getSimpleName(), "removing gap");
         transaction.clear();
         exhausted = false;
         removedCount = 0;
      }
      transaction.addAll(0, newItems);
      transaction.commit();
      return newCount;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   protected int appendOldItems(ArrayList<T> oldItems, Poll.Listener listener) {
      Log.i(getClass().getSimpleName(), "offset = "+offset());
      int count = oldItems.size();
      for (T item : oldItems) {
         if (list.contains(item))
            count--;
      }
      if (count > 0) {
         if (count + list.getStorage().currentSize() > list.getStorage().maxSize()) {
            if (Poll.DEBUG) Log.w(getClass().getSimpleName(), "Hitting storage capacity, trimming list.");
            String idBefore = listener.getCurrentId();
            List transaction = list.transaction();
            removedCount += transaction.makeGap(limit) + limit; // FIXME why +limit
            int removedCount = transaction.getStorage().retainList((List) transaction);
            if (Poll.DEBUG) Log.w(getClass().getSimpleName(), "Freed "+removedCount+" elements from storage.");
            transaction.addAll(oldItems);
            transaction.commit();
            listener.onTrim(idBefore);
         } else {
            list.addAll(oldItems);
         }
      } else {
         exhausted = true;
      }

      return count;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   @Override
   public void setStorage(List list) {
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
    * @return
    */
   public int offset() {
      return removedCount + Math.max(list.size(), limit);
   }

   @Override
   public boolean okToSave() {
      return !list.isEmpty();
   }
}
