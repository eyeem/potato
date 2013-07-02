package com.eyeem.poll;

import android.widget.BaseAdapter;
import com.eyeem.storage.Storage;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

import java.util.HashSet;

public abstract class AnimatedPollAdapter extends BaseAdapter implements PollListView.PollAdapter {

   public HashSet<String> seenIds = new HashSet<String>();

   protected String firstId;
   protected int offsetPy;

   @Override
   public void notifyDataWillChange(PollListView plv) {
      boolean pullToRefreshList = false;
      try {
         pullToRefreshList = plv.getRefreshableView().getParent().getParent() instanceof PullToRefreshListView;
      } catch (Throwable t) {}
      int i = plv.getListFirstVisiblePosition() - plv.getListHeaderViewsCount();
      if (i >= 0 || (pullToRefreshList && i+1 >= 0)) {
         // pull to refresh library adds a framelayout header by default so
         // we need to compensate for that
         firstId = idForPosition(i < 0 ? 0 : i);
         offsetPy = plv.getListChildAt(0) == null ? 0 : plv.getListChildAt(0).getTop();
      } else {
         firstId = null;
         offsetPy = 0;
      }
   }

   @Override
   public void notifyDataWithAction(Storage.Subscription.Action action, final PollListView plv) {
      if (Storage.Subscription.ADD_UPFRONT.equals(action.name)) {
         boolean paused = isScrollingPaused(plv);
         notifyDataSetChanged();
         int index = positionForId(firstId);
         firstId = null;
         final int px = offsetPy;
         if (!paused && index > 0) {
            plv.setListSelectionFromTop(index + plv.getListHeaderViewsCount(), px);
            if (index == 1) {
               plv.getRefreshableView().postDelayed(new Runnable() {

                  int counter = 0;

                  @Override
                  public void run() {
                     int offset = plv.getListFirstVisiblePosition();
                     if (offset == 0 || counter == 1) {
                        plv.setListSelection(0);
                        return;
                     }
                     int distance = 0;
                     int duration = 1000 * (offset + 1);
                     if (plv.getListChildCount() > 0)
                        distance = plv.getListChildAt(0).getHeight() * offset; // approx

                     plv.listSmoothScrollBy(-distance, duration);
                     plv.getRefreshableView().postDelayed(this, duration);
                     counter++;
                  }
               }, 500);
            } else {
               // TODO new items indicator
               plv.setListSelection(0);
            }
         } else {
            if (!isScrollingPaused(plv)) {
               // otherwise pull to refresh library starts to behave erratic
               plv.setListSelectionFromTop(index, px); // should prolly be 0, 0 args but I'm too afraid of implications
            } else {
               plv.setListSelectionFromTop(index + plv.getListHeaderViewsCount(), px);
            }
         }
      } else {
         notifyDataSetChanged();
         if (firstId != null) {
            plv.setListSelectionFromTop(positionForId(firstId) + plv.getListHeaderViewsCount(), offsetPy);
            firstId = null;
         }
      }
   }

   public boolean isScrollingPaused(PollListView plv) {
      return plv.getListFirstVisiblePosition() != 0;
   }

   @Override
   public long getItemId(int position) {
      return position;
   }

   @Override
   public HashSet<String> seenIds() {
      return seenIds;
   }

   @Override
   public void clearViewCache() {}
}
