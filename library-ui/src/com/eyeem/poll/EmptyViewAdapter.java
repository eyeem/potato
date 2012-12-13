package com.eyeem.poll;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

/**
 * One view custom adapter
 */
public class EmptyViewAdapter extends BaseAdapter {
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
}
