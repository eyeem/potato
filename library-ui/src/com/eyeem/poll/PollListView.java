package com.eyeem.poll;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.emilsjolander.components.StickyListHeaders.StickyListHeadersAdapter;
import com.emilsjolander.components.StickyListHeaders.StickyListHeadersListView;
import com.eyeem.lib.ui.R;
import com.eyeem.poll.Poll;
import com.eyeem.storage.Storage.Subscription;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

@SuppressWarnings("rawtypes")
public class PollListView extends PullToRefreshListView {

   Poll poll;
   BusyIndicator indicator;
   PollAdapter dataAdapter;
   BaseAdapter noContentAdapter;
   BaseAdapter noConnectionAdapter;
   BaseAdapter currentAdapter;
   View hackingEmptyView;

   int problemsLabelId;
   int progressLabelId;

   public PollListView(Context context) {
      super(context);
      progressLabelId = R.string.default_progress_label;
      problemsLabelId = R.string.default_problems_label;
   }

   public PollListView(Context context, AttributeSet attrs) {
      super(context, attrs);
      loadAttributes(context, attrs);
   }

   private void loadAttributes(Context context, AttributeSet attrs) {
      TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.PollListView);
      progressLabelId = arr.getResourceId(R.styleable.PollListView_progress_text, R.string.default_progress_label);
      problemsLabelId = arr.getResourceId(R.styleable.PollListView_problems_text, R.string.default_problems_label);
      arr.recycle();
   }

   public void setBusyIndicator(BusyIndicator indicator){
      this.indicator = indicator;
   }

   public void setPoll(Poll poll) {
      this.poll = poll;
      setOnRefreshListener(refreshListener);
      getRefreshableView().setOnScrollListener(scrollListener);
   }

   public void onPause() {
      if (poll != null) {
         poll.list.unsubscribe(subscription);
         if (poll.okToSave()) {
            poll.list.save();
         }
      }
   }

   public void onResume() {
      if (poll != null) {
         poll.list.subscribe(subscription);
         if (!poll.list.ensureConsistence() || poll.list.isEmpty()) {
            poll.resetLastTimeUpdated();
            poll.list.load();
         }
         poll.updateIfNecessary(updateListener);
      }
      if (dataAdapter != null && pickAdapter() == dataAdapter) {
         if (currentAdapter != dataAdapter)
            setAdapter(dataAdapter);
         dataAdapter.notifyDataSetChanged();
      }
   }

   public void setNoConnectionView(View view) {
      noConnectionAdapter = new EmptyViewAdapter(view);
   }

   public void setNoContentView(View view) {
      hackingEmptyView = view;
      noContentAdapter = new EmptyViewAdapter(view);
   }

   public void setDataAdapter(PollAdapter adapter) {
      dataAdapter = adapter;
      reloadAdapters();
   }

   private BaseAdapter pickAdapter() {
      if (poll == null) {
         return noContentAdapter;
      }

      if (poll.getState() == Poll.STATE_NO_CONNECTION) {
         return noConnectionAdapter;
      } else if (poll.getState() == Poll.STATE_NO_CONTENT) {
         return noContentAdapter;
      }
      return (BaseAdapter) dataAdapter;
   }

   private void messageWithDelay(String message) {
      PollListView.this.setRefreshingLabel(message);
      postDelayed(new Runnable() {
         @Override
         public void run() {
            PollListView.this.onRefreshComplete();
         }
      }, 2000);
   }

   private OnRefreshListener<ListView> refreshListener = new OnRefreshListener<ListView> () {

      @Override
      public void onRefresh(PullToRefreshBase<ListView> refreshView) {
         if (poll != null) {
            poll.update(updateListener);
         }
      }

   };

   /**
    * Basically sets adapter in busy mode whenever scroll is in
    * FLING mode. This allows to avoid expensive image loading tasks.
    * Also performs calls on Views to refresh images.
    *
    * This allso issues poll calls for older content.
    * @author vishna
    */
   private OnScrollListener scrollListener = new OnScrollListener() {

      boolean wasFlinging = false;

      @Override
      public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
         if (totalItemCount > 0 && firstVisibleItem > 0 && (totalItemCount - (firstVisibleItem + visibleItemCount)) <= 5) {
            if (poll != null && !poll.exhausted) {
               poll.fetchMore(fetchListener);
            }
         }
      }

      @Override
      public void onScrollStateChanged(AbsListView view, int scrollState) {
         if (currentAdapter != dataAdapter)
            return;

         if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
            dataAdapter.setBusy(true);
            wasFlinging = true;
         } else if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && wasFlinging) {
            dataAdapter.setBusy(false);
            wasFlinging = false;
            dataAdapter.refreshViews(getRefreshableView());
         } else {
            dataAdapter.setBusy(false);
         }
      }
   };

   private abstract class PollListener implements Poll.Listener {
      @Override
      public String getCurrentId() {
         try {
            // int i = getRefreshableView().getFirstVisiblePosition();
            int i = getRefreshableView().getLastVisiblePosition() - getRefreshableView().getHeaderViewsCount();
            return dataAdapter.idForPosition(i);
         } catch (Throwable t) {
            return null;
         }
      }

      @Override
      public void onTrim(final String currentId) {
         post(new Runnable() {
            @Override
            public void run() {
               int index =  -1;
               index = dataAdapter.positionForId(currentId);
               // index += getRefreshableView().getHeaderViewsCount();
               if (index >= 0)
                  getRefreshableView().setSelection(index);
            }
         });
      }
   };

   private Poll.Listener fetchListener = new PollListener () {

      @Override
      public void onStart() {
         if(indicator != null)
            indicator.setBusyIndicator(true);
      }

      @Override
      public void onError(Throwable error) {
         if(indicator != null)
            indicator.setBusyIndicator(false);
      }

      @Override
      public void onSuccess(int newCount) {
         if (indicator != null)
            indicator.setBusyIndicator(false);
      }

      @Override
      public void onAlreadyPolling() {
         if (indicator != null)
            indicator.setBusyIndicator(true);
      }

      @Override
      public void onExhausted() {
         if (indicator != null)
            indicator.setBusyIndicator(false);
      }

      @Override
      public void onStateChanged(int state) {}
   };

   private Poll.Listener updateListener = new PollListener () {

      @Override
      public void onError(Throwable error) {
         messageWithDelay(getContext().getString(problemsLabelId));
         if (indicator != null)
            indicator.setBusyIndicator(false);
      }

      @Override
      public void onSuccess(int newCount) {
         messageWithDelay(poll.getSuccessMessage(getContext(), newCount));
         if (indicator != null)
            indicator.setBusyIndicator(false);
      }

      @Override
      public void onAlreadyPolling() {
         // NO-OP ?
      }

      @Override
      public void onExhausted() {}

      @Override
      public void onStart() {
         PollListView.this.setRefreshingLabel(getContext().getString(progressLabelId));
         if (indicator != null && poll.getState() == Poll.STATE_UNKNOWN)
            indicator.setBusyIndicator(true);
      }

      @Override
      public void onStateChanged(int state) {
         reloadAdapters();
      }
   };

   private void reloadAdapters() {
      BaseAdapter newAdapter = pickAdapter();
      if (newAdapter == null)
         return;
      if (currentAdapter != newAdapter) {
         if (getRefreshableView() instanceof StickyListHeadersListView) {
            ((StickyListHeadersListView)getRefreshableView()).setAreHeadersSticky(newAdapter instanceof StickyListHeadersAdapter);
         }
         getRefreshableView().setAdapter(currentAdapter = newAdapter);
      } 
      if (newAdapter == noContentAdapter) {
         WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
         Display display = wm.getDefaultDisplay();
         hackingEmptyView.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, display.getHeight()));
      }
      newAdapter.notifyDataSetChanged();
   }

   Subscription subscription = new Subscription() {
      @Override
      public void onUpdate() {
         post(new Runnable() {
            @Override
            public void run() {
               reloadAdapters();
            }
         });
      }
   };

   public void update() {
      poll.update(updateListener);
   }

   public interface PollAdapter extends android.widget.ListAdapter, android.widget.SpinnerAdapter {
      public void setBusy(boolean value);
      public void notifyDataSetChanged();
      public void refreshViews(ListView lv);
      public String idForPosition(int position);
      public int positionForId(String id);
   }

   public interface BusyIndicator {
      public void setBusyIndicator(boolean busy_flag);
   }
}
