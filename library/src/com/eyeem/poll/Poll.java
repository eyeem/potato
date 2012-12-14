package com.eyeem.poll;

import java.util.ArrayList;
import java.util.Vector;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.eyeem.storage.Storage;

public abstract class Poll<T> {

   public static boolean DEBUG = false;

   public static final int STATE_UNKNOWN = -1;
   public static final int STATE_OK = 0;
   public static final int STATE_NO_CONTENT = 1;
   public static final int STATE_NO_CONNECTION = 2;

   protected Storage<T>.List list;

   protected boolean exhausted;
   protected boolean polling;
   protected long lastTimeUpdated;
   protected long refreshPeriod;
   private int state = STATE_UNKNOWN;

   protected abstract ArrayList<T> newItems() throws Throwable;
   protected abstract ArrayList<T> oldItems() throws Throwable;
   protected abstract int appendNewItems(ArrayList<T> newItems, Poll.Listener listener);
   protected abstract int appendOldItems(ArrayList<T> oldItems, Poll.Listener listener);
   protected abstract String getSuccessMessage(Context ctx, int newCount);

   private RefreshTask updating;
   private RefreshTask fetchingMore;

   public boolean shouldUpdate() {
      return (System.currentTimeMillis() - refreshPeriod) > lastTimeUpdated;
   }

   public void updateIfNecessary(Listener listener) {
      if (shouldUpdate())
         update(listener);
   }

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
                  state = STATE_NO_CONNECTION;
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

   public void setStorage(Storage<T>.List list) { this.list = list; }
   public Storage<T>.List getStorage() { return list; }

   public interface Listener {
      public void onStart();
      public void onError(Throwable error);
      public void onSuccess(int newCount);
      public void onAlreadyPolling();
      public void onStateChanged(int state);
      public void onExhausted();
      public String getCurrentId();
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
      }

      protected void onError(Throwable error) {
         if (DEBUG) Log.w(getClass().getSimpleName(), "onError", error);
         for (Listener listener : listeners)
            listener.onError(error);
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

   public int getState() {
      if (list != null && !list.isEmpty())
         return state = STATE_OK;
      return state;
   }

   public boolean okToSave() { return true; }

   public void resetLastTimeUpdated() {
      lastTimeUpdated = 0;
   }
}
