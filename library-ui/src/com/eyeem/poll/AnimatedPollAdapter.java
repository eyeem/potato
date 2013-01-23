package com.eyeem.poll;

import android.widget.BaseAdapter;
import android.widget.ListView;
import com.eyeem.storage.Storage;

public abstract class AnimatedPollAdapter extends BaseAdapter implements PollListView.PollAdapter {
   @Override
   public void notifyDataWithAction(Storage.Subscription.Action action, final ListView listView) {
      if (Storage.Subscription.ADD_UPFRONT.equals(action.name)) {
         boolean paused = isScrollingPaused(listView);
         notifyDataSetChanged();
         int index = positionForId((String)action.params.get("firstId"));
         final int px = (Integer)action.params.get("firstTop");
         if (!paused && index > 0) {
            listView.setSelectionFromTop(index + listView.getHeaderViewsCount(), px);
            if (index == 1) {
               listView.postDelayed(new Runnable() {
                  @Override
                  public void run() {
                     int offset = listView.getFirstVisiblePosition();
                     if (offset == 0) {
                        listView.setSelection(0);
                        return;
                     }
                     int distance = 0;
                     int duration = 1000 * (offset + 1);
                     if (listView.getChildCount() > 0)
                        distance = listView.getChildAt(0).getHeight() * offset; // approx

                     listView.smoothScrollBy(-distance, duration);
                     listView.postDelayed(this, duration);
                  }
               }, 500);
            } else {
               // TODO new items indicator
            }
         } else {
            listView.setSelectionFromTop(index, px);
         }
      } else {
         notifyDataSetChanged();
      }
   }

   public boolean isScrollingPaused(ListView lv) {
      return lv.getFirstVisiblePosition() != 0;
   }

   @Override
   public long getItemId(int position) {
      return position;
   }
}
