package com.eyeem.poll;

import com.eyeem.poll.PollListView.PollAdapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import com.eyeem.storage.Storage;

import java.util.HashSet;

/**
 * One view custom adapter
 */
public class EmptyViewAdapter extends BaseAdapter implements PollAdapter{
   // FIXME fill parent issues

   View view;

   public EmptyViewAdapter(View view) {
      this.view = view;
   }

   @Override public int getCount() { return 1; }
   @Override public Object getItem(int position) { return null; }
   @Override public long getItemId(int position) { return 0; }
   @Override public View getView(int position, View convertView, ViewGroup parent) {
      return view;
   }

   @Override
   public void setBusy(boolean value) {}

   @Override
   public void refreshViews(ListView lv) {}

   @Override
   public String idForPosition(int position) {
      return "";
   }

   @Override
   public int positionForId(String id) {
      return 0;
   }

   @Override
   public void notifyDataWithAction(Storage.Subscription.Action action, ListView listView) {
      notifyDataSetChanged();
   }

   @Override
   public void recycleBitmaps(View view) {
   }

   @Override
   public HashSet<String> seenIds() {
      return new HashSet<String>();
   }
}
