package com.eyeem.poll;

import android.view.View;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import com.eyeem.storage.Storage;

import java.util.HashSet;

import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshAttacher;

/**
 * Created with IntelliJ IDEA.
 * User: vishna
 * Date: 7/2/13
 * Time: 3:15 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PollListView {

   public void onPause();
   public void onResume();
   public void onDestroy();

   public void update();
   public void updateIfNecessary();

   public void setPoll(Poll poll);
   public void setBusyIndicator(BusyIndicator indicator);
   public void setDataAdapter(PollAdapter adapter);
   public void setNoContentView(View view);
   public void setOnErrorView(View view);

   public void setListSelectionFromTop(int index, int px);
   public void setListSelection(int index);

   public void addHeaderView(View view);
   public void addCustomRefreshRunnable(Runnable runnable);
   public void addOnScrollListener(AbsListView.OnScrollListener listener);
   public void removeOnScrollListener(AbsListView.OnScrollListener listener);

   public Poll getPoll();
   public int getListFirstVisiblePosition();
   public int getListHeaderViewsCount();
   public View getListChildAt(int index);
   public int getListChildCount();
   public void listSmoothScrollBy(int distance, int duration);
   public PollAdapter getDataAdapter();

   public PullToRefreshAttacher.OnRefreshListener getOnRefreshListener();

   /**
    * Don't use this, use setDataAdapter instead
    * @param adapter
    */
   public void setAdapter(android.widget.ListAdapter adapter);

   public interface PollAdapter extends android.widget.ListAdapter, android.widget.SpinnerAdapter {
      /**
       * Sets adapter in busy state during scroll FLING. Usually this tells
       * adapter there is a fast ongoing scroll and it's better not to allocate
       * any big objects (e.g. bitmaps) to avoid flickering. Aka drawing + memory
       * allocation sucks big time on Android.
       *
       * @param value
       */
      public void setBusy(boolean value);

      public void notifyDataSetChanged();

      public void notifyDataWillChange(PollListView plv);

      public void notifyDataWithAction(Storage.Subscription.Action action, PollListView plv);

      /**
       * This is called after list has returned from FLING mode. This gives
       * opportunity to implementing classes to go through ListView hierarchy
       * and refresh/invalidate views.
       *
       * @param lv
       */
      public void refreshViews(PollListView lv);

      /**
       * Returns id for the given scroll position
       *
       * @param position
       * @return
       */
      public String idForPosition(int position);

      /**
       * Returns position of the given id.
       *
       * @param id
       * @return position for id, or -1 if datasource does not contain id.
       */
      public int positionForId(String id);

      public HashSet<String> seenIds();

      public void clearViewCache();
   }

   /**
    * Indicates list view being busy. E.g. spinner when content
    * is being loaded in the background thread.
    */
   public interface BusyIndicator {
      public void setBusyIndicator(boolean busy_flag);
      public void pullToRefreshDone();
   }
}
