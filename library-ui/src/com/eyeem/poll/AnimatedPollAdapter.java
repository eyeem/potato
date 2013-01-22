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
         final int index = positionForId((String)action.params.get("firstId"));
         final int px = (Integer)action.params.get("firstTop");
         if (!paused && index > 0) {
            // WONDERS OF ANDROID: in order to freeze list at top when
            // adding new items we need to add "1" to
            // selected index in setSelectionFromTop
            listView.setSelectionFromTop(index + 1, px);
            listView.postDelayed(new Runnable() {
               @Override
               public void run() {
                  int distance = 0;
                  if (listView.getChildCount() > 0)
                     distance = listView.getChildAt(0).getHeight() * index * 2; // approx

                  listView.smoothScrollBy(-distance, 1000);
                  listView.postDelayed(new Runnable() {
                     @Override
                     public void run() {
                        listView.smoothScrollToPosition(0);
                     }
                  }, 1100);
               }
            }, 500);
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
