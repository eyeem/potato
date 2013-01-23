package com.eyeem.poll;

import java.util.ArrayList;
import java.util.Vector;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.eyeem.storage.Storage;
import com.eyeem.storage.Storage.List;

/**
 * Base class for polling. Works in combination with {@link Storage}
 * class.
 *
 * @param <T>
 */
public abstract class Poll<T> {

   public static boolean DEBUG = false;

   /**
    * No poll has been made yet so we don't know
    * anything.
    */
   public static final int STATE_UNKNOWN = -1;

   /**
    * Poll is looking good.
    */
   public static final int STATE_OK = 0;

   /**
    * Poll has no content.
    */
   public static final int STATE_NO_CONTENT = 1;

   /**
    * Poll has no content due to a error.
    */
   public static final int STATE_ERROR = 2;

   /**
    * Associated storage.
    */
   protected Storage<T>.List list;

   /**
    * No more items to poll
    */
   protected boolean exhausted;

   /**
    * Indicates whether we're polling or not.
    */
   protected boolean polling;

   /**
    * Last time successful {@link #update(Listener)} occured.
    * UNIX time ms.
    */
   protected long lastTimeUpdated;

   /**
    * How often should polling happen.
    */
   protected long refreshPeriod;

   /**
    * Represents state in which poll is.
    */
   private int state = STATE_UNKNOWN;

   /**
    * Fetches new items
    * @return
    * @throws Throwable
    */
   protected abstract ArrayList<T> newItems() throws Throwable;

   /**
    * Fetches older items
    * @return
    * @throws Throwable
    */
   protected abstract ArrayList<T> oldItems() throws Throwable;

   /**
    * Appends new items to the {@link #list}
    * @param newItems
    * @param listener
    * @return
    */
   protected abstract int appendNewItems(ArrayList<T> newItems, Poll.Listener listener);

   /**
    * Appends old items to the {@link #list}
    * @param oldItems
    * @param listener
    * @return
    */
   protected abstract int appendOldItems(ArrayList<T> oldItems, Poll.Listener listener);

   /**
    * @param ctx
    * @param newCount
    * @return Message associated with successful update.
    */
   protected abstract String getSuccessMessage(Context ctx, int newCount);

   /**
    * Fetch new items task
    */
   private RefreshTask updating;

   /**
    * Fetch older items task
    */
   private RefreshTask fetchingMore;

   /**
    * Indicates whether {@link Poll} should update or not. Default policy
    * is to update if {@link #lastTimeUpdated} happened more than
    * {@link #refreshPeriod} time ago.
    * @return
    */
   public boolean shouldUpdate() {
      return (System.currentTimeMillis() - refreshPeriod) > lastTimeUpdated;
   }

   /**
    * Updates poll if it {@link #shouldUpdate()}
    * @param listener
    */
   public void updateIfNecessary(Listener listener) {
      if (shouldUpdate())
         update(listener);
   }

   /**
    * Updates poll always. Please consider using
    * {@link #updateIfNecessary(Listener)}
    * @param listener
    */
   public synchronized void update(final Listener listener) {
      if (updating == null || updating.working == false) {
         updating = new RefreshTask() {

            @Override
            protected ArrayList<T> doInBackground(Void... params) {
               try {
                  return newItems();
               } catch (Throwable e) {
                  error = e;
                  this.cancel(true);
               }
               return new ArrayList<T>();
            }

            @Override
            public int itemsAction(ArrayList<T> result) throws Throwable {
               return appendNewItems(result, listener);
            }

            @Override
            protected void onSuccess(int result) {
               lastTimeUpdated = System.currentTimeMillis();
               if (list.isEmpty()) {
                  state = STATE_NO_CONTENT;
                  onStateChanged();
               }
               super.onSuccess(result);
            }
            @Override
            protected void onError(Throwable error) {
               if (list.isEmpty()) {
                  state = STATE_ERROR;
                  onStateChanged();
               }
               super.onError(error);
            }
         };
      }
      updating.refresh(listener);
   }

   public synchronized void fetchMore(final Listener listener) {
      if (exhausted) {
         if (listener != null)
            listener.onExhausted();
         return;
      }
      if (fetchingMore == null || fetchingMore.working == false) {
         fetchingMore = new RefreshTask() {

            @Override
            protected ArrayList<T> doInBackground(Void... params) {
               try {
                  return oldItems();
               } catch (Throwable e) {
                  error = e;
                  this.cancel(true);
               }
               return new ArrayList<T>();
            }

            @Override
            public int itemsAction(ArrayList<T> result) throws Throwable {
               return appendOldItems(result, listener);
            }
         };
      }
      fetchingMore.refresh(listener);
   }

   /**
    * Sets the associated {@link List}
    * @param list
    */
   public void setStorage(Storage<T>.List list) { this.list = list; }

   /**
    * Getter for the Poll's associated {@link List}
    * @return
    */
   public Storage<T>.List getStorage() { return list; }

   /**
    * Poll refresh interface
    */
   public interface Listener {
      /**
       * Poll started
       */
      public void onStart();

      /**
       * Poll encountered an error
       * @param error
       */
      public void onError(Throwable error);

      /**
       * Poll was successful
       * @param newCount number of new items
       */
      public void onSuccess(int newCount);

      /**
       * Poll is already polling
       */
      public void onAlreadyPolling();

      /**
       * Poll's state has changed
       * @param state
       */
      public void onStateChanged(int state);

      /**
       * Poll has exhausted.
       */
      public void onExhausted();

      /**
       * Facility to tell Poll about currently last visible
       * id.
       * @return
       */
      public String getLastVisibleId();

      /**
       * Facility to tell Poll about currently first visible
       * id.
       * @return
       */
      public String getFirstVisibleId();

      public int getFirstTop();

      /**
       * Trim action is about to happen
       * @param currentId
       */
      public void onTrim(String currentId);
   }

   private abstract class RefreshTask extends AsyncTask<Void, Void, ArrayList<T>> {
      Throwable error;

      Vector<Listener> listeners = new Vector<Listener>();
      boolean working;
      boolean executed = false;

      public abstract int itemsAction(ArrayList<T> result) throws Throwable;

      @Override
      protected void onPostExecute(ArrayList<T> result) {
         super.onPostExecute(result);

         int addedItemsCount = 0;
         try {
            addedItemsCount = itemsAction(result);
         } catch (Throwable e) {
            error = e;
         }

         if (error != null) {
            onError(error);
         } else if (result != null) {
            onSuccess(addedItemsCount);
         }
         working = false;
      }

      protected void onSuccess(int result) {
         for (Listener listener : listeners)
            listener.onSuccess(result);
         listeners.clear();
      }

      protected void onError(Throwable error) {
         if (DEBUG) Log.w(getClass().getSimpleName(), "onError", error);
         for (Listener listener : listeners)
            listener.onError(error);
         listeners.clear();
      }

      @Override
      protected void onCancelled() {
         super.onCancelled();
         onError(error);
         working = false;
      }

      protected void onStateChanged() {
         for (Listener listener : listeners)
            listener.onStateChanged(state);
      }

      synchronized void refresh(Listener listener) {
         if (listener != null) {
            if (!listeners.contains(listener))
               listeners.add(listener);

            if (working) {
               listener.onAlreadyPolling();
            }
         }

         if (!working && !executed) {
            executed = true;
            working = true;
            if (listener != null)
               listener.onStart();
            execute();
         }
      }
   }

   /**
    * @return Poll's state
    */
   public int getState() {
      if (list != null && !list.isEmpty())
         return state = STATE_OK;
      return state;
   }

   /**
    * @return tells whether it's ok to persist list in it's current state
    */
   public boolean okToSave() { return true; }

   /**
    * Sets {@link #lastTimeUpdated} value to 0.
    */
   public void resetLastTimeUpdated() {
      lastTimeUpdated = 0;
   }
}
