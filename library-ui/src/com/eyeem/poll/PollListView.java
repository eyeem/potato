package com.eyeem.poll;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView.FixedViewInfo;
import android.widget.ListView;

import com.eyeem.lib.ui.R;
import com.eyeem.storage.Storage.Subscription;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

/**
 * ListView for {@link Poll}. Takes care of calling {@link Poll}'s functions,
 * provides all the goodies like pull-to-refresh and infinite scroll. All you
 * need to do is provide {@link Poll} & {@link PollAdapter} instances and call
 * {@link #onPause()} & {@link #onResume()} in Activity's lifecycle. Aditionally
 * you might want to provide no content/connection views using
 * {@link #setOnErrorView(View)} & {@link #setNoContentView(View)}
 */
@SuppressWarnings("rawtypes")
public class PollListView extends PullToRefreshListView {

   Poll poll;
   BusyIndicator indicator;
   protected PollAdapter dataAdapter;
   protected PollAdapter noContentAdapter;
   protected PollAdapter onErrorAdapter;
   protected PollAdapter currentAdapter;
   View hackingEmptyView;
   ArrayList<Runnable> customRefreshRunnables = new ArrayList<Runnable>();

   String scrollPositionId;
   String topSeenId;
   boolean hasPlaceholderFooter;
   View placeHolderFooterView;

   /**
    * Problems text displayed in pull to refresh header
    * when there are connection problems
    */
   int problemsLabelId;

   /**
    * Progress text displayed in pull to refresh header
    * when refreshing.
    */
   int progressLabelId;

   public PollListView(Context context) {
      super(context);
      progressLabelId = R.string.default_progress_label;
      problemsLabelId = R.string.default_problems_label;
   }

   public PollListView(Context context, AttributeSet attrs) {
      super(context, attrs);
      loadAttributes(context, attrs);

      placeHolderFooterView = new View(getContext());
      placeHolderFooterView.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (getContext().getResources().getDisplayMetrics().density * 70)));
      //thx to google we to call that before setAdapter to be able to add/remove header/footer afterwards
      getRefreshableView().addFooterView(placeHolderFooterView);
      if (!hasPlaceholderFooter) {
         removeFooter();
      }
   }

   private void loadAttributes(Context context, AttributeSet attrs) {
      TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.PollListView);
      progressLabelId = arr.getResourceId(R.styleable.PollListView_progress_text, R.string.default_progress_label);
      problemsLabelId = arr.getResourceId(R.styleable.PollListView_problems_text, R.string.default_problems_label);
      hasPlaceholderFooter = arr.getBoolean(R.styleable.PollListView_add_placeholder_footer, false);
      arr.recycle();
   }

   /**
    * Setter for the refresh indicator
    *
    * @param indicator
    */
   public void setBusyIndicator(BusyIndicator indicator) {
      this.indicator = indicator;
   }

   /**
    * Custom Runnable which will be executed on pull to refresh
    *
    * @param refreshRunnable
    */
   public void addCustomRefreshRunnable(Runnable refreshRunnable) {
      if (!customRefreshRunnables.contains(refreshRunnable))
         customRefreshRunnables.add(refreshRunnable);
   }

   /**
    * Setter for {@link Poll}
    *
    * @param poll
    */
   public void setPoll(Poll poll) {
      this.poll = poll;
      setOnRefreshListener(refreshListener);
      getRefreshableView().setOnScrollListener(scrollListener);
   }

   /**
    * Call in Activity's or Fragment's onPause
    */
   public void onPause() {
      if (poll != null) {
         int position = getRefreshableView().getFirstVisiblePosition() - getRefreshableView().getHeaderViewsCount();
         position = Math.max(position, 0);
         if (poll.list.size() > 0 && dataAdapter != null) {
            scrollPositionId = dataAdapter.idForPosition(position);
         }
         topSeenId = null;
         for (int i = 0; i < poll.list.size() && topSeenId == null; i++) {
            String id = poll.list.idForPosition(i);
            if (dataAdapter.seenIds().contains(id))
               topSeenId = id;
         }
         poll.list.setMeta("scrollPositionId", scrollPositionId);
         poll.list.setMeta("topSeenId", topSeenId);
         poll.list.unsubscribe(subscription);
         if (poll.okToSave()) {
            poll.list.save();
         }
      }
   }

   /**
    * Call in Activity's or Fragment's onResume
    */
   public void onResume() {
      if (poll != null) {

         //FIXME: Hotfix to avoid empty lists if objects are deleted by cache and poll is already exhausted 
         poll.exhausted = false;

         poll.list.subscribe(subscription);
         poll.list.extOn();
         if (!poll.list.ensureConsistence() || poll.list.isEmpty()) {
            poll.resetLastTimeUpdated();
            poll.list.load();
         }
         poll.updateIfNecessary(fetchListener);
      }
      if (dataAdapter != null && pickAdapter() == dataAdapter) {
         if (currentAdapter != dataAdapter)
            setAdapter(dataAdapter);
         dataAdapter.notifyDataSetChanged();
         dataAdapter.clearViewCache();
      }
   }

   public void onDestroy() {
      if (poll != null && poll.list != null) {
         poll.list.extOff();
      }
   }

   /**
    * Set view that will be displayed when there is no content
    * due to an error
    *
    * @param view
    */
   public void setOnErrorView(View view) {
      onErrorAdapter = new EmptyViewAdapter(view);
   }

   /**
    * Set view that will be displayed when there is no content
    *
    * @param view
    */
   public void setNoContentView(View view) {
      hackingEmptyView = view;
      noContentAdapter = new EmptyViewAdapter(view);
   }

   /**
    * Setter for {@link PollAdapter}
    *
    * @param adapter
    */
   public void setDataAdapter(PollAdapter adapter) {
      if (adapter != dataAdapter) {
         dataAdapter = adapter;
         reloadAdapters(null);
      }
   }

   private PollAdapter pickAdapter() {
      if (poll == null) {
         if (hasPlaceholderFooter)
            removeFooter();
         return noContentAdapter;
      }

      if (poll.getState() == Poll.STATE_ERROR) {
         return onErrorAdapter;
      } else if (poll.getState() == Poll.STATE_NO_CONTENT) {
         if (hasPlaceholderFooter)
            removeFooter();
         return noContentAdapter;
      }
      if (hasPlaceholderFooter)
         addFooter();

      return dataAdapter;
   }

   private void addFooter() {
      post(new Runnable() {
         @Override
         public void run() {
            if (getRefreshableView().getFooterViewsCount() == 0)
               getRefreshableView().addFooterView(placeHolderFooterView);
         }
      });
   }

   private void removeFooter() {
      post(new Runnable() {
         @Override
         public void run() {
            getRefreshableView().removeFooterView(placeHolderFooterView);
         }
      });
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

   private OnRefreshListener<ListView> refreshListener = new OnRefreshListener<ListView>() {

      @Override
      public void onRefresh(PullToRefreshBase<ListView> refreshView) {
         if (poll != null) {
            poll.update(updateListener, true);
            for (Runnable r : customRefreshRunnables)
               r.run();
         }
      }

   };

   /**
    * Basically sets adapter in busy mode whenever scroll is in
    * FLING mode. This allows to avoid expensive image loading tasks.
    * Also performs calls on Views to refresh images.
    * <p/>
    * This also issues poll calls for older content, aka infinite scroll.
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

   private Poll.Listener fetchListener = new Poll.Listener() {

      @Override
      public void onStart() {
         if (indicator != null)
            indicator.setBusyIndicator(true);
      }

      @Override
      public void onError(Throwable error) {
         if (indicator != null)
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
      public void onStateChanged(int state) {
      }
   };

   private Poll.Listener updateListener = new Poll.Listener() {

      @Override
      public void onError(Throwable error) {
         String msg = null;
         if (error == null || (msg = error.getMessage()) == null || TextUtils.isEmpty(msg))
            msg = getContext().getString(problemsLabelId);
         messageWithDelay(msg);
         if (indicator != null)
            indicator.setBusyIndicator(false);
      }

      @Override
      public void onSuccess(int newCount) {
         if (poll != null)
            messageWithDelay(poll.getSuccessMessage(getContext(), newCount));

         if (indicator != null)
            indicator.setBusyIndicator(false);
      }

      @Override
      public void onAlreadyPolling() {
         // NO-OP ?
      }

      @Override
      public void onExhausted() {
      }

      @Override
      public void onStart() {
         PollListView.this.setRefreshingLabel(getContext().getString(progressLabelId));
         if (indicator != null && poll.getState() == Poll.STATE_UNKNOWN)
            indicator.setBusyIndicator(true);
      }

      @Override
      public void onStateChanged(int state) {
         reloadAdapters(null);
      }
   };

   private void reloadAdapters(Subscription.Action action) {
      PollAdapter newAdapter = pickAdapter();
      if (newAdapter == null)
         return;
      if (currentAdapter != newAdapter) {
         getRefreshableView().setAdapter(currentAdapter = newAdapter);
      }
      if (newAdapter == noContentAdapter) {
         hackingEmptyView.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, getHeight() - headerHeight()));
      }
      if (action == null) {
         newAdapter.notifyDataSetChanged();
      } else {
         if (action.name.equals(Subscription.WILL_CHANGE) || action.param("singleItemUpdate") != null)
            return;
         else
            newAdapter.notifyDataWithAction(action, getRefreshableView());
      }
   }

   Subscription subscription = new Subscription() {
      @Override
      public void onUpdate(final Action action) {
         if (action.name.equals(Subscription.WILL_CHANGE)) {
            if (dataAdapter != null)
               dataAdapter.notifyDataWillChange(getRefreshableView());
            return;
         } else if (action.name.equals(Subscription.PUSH)) {
            String id = String.valueOf(action.param("objectId"));
            int headerCount = getRefreshableView().getHeaderViewsCount();
            int start = getRefreshableView().getFirstVisiblePosition() - headerCount;
            for (int i = start, j = getRefreshableView().getLastVisiblePosition() - headerCount; i <= j; i++)
               if (i >= 0 && poll.getStorage().getById(id).equals(dataAdapter.getItem(i))) {
                  final View view = getRefreshableView().getChildAt(i - start);
                  final int finalPosition = i;
                  action.param("singleItemUpdate", true);
                  post(new Runnable() {
                     @Override
                     public void run() {
                        dataAdapter.clearViewCache();
                        dataAdapter.getView(finalPosition, view, getRefreshableView());
                     }
                  });
                  break;
               }
         }
         post(new Runnable() {
            @Override
            public void run() {
               reloadAdapters(action);
            }
         });
      }
   };

   /**
    * Update/refresh content
    */
   public void update() {
      poll.update(updateListener, false);
   }

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

      public void notifyDataWillChange(ListView listView);

      public void notifyDataWithAction(Subscription.Action action, ListView listView);

      /**
       * This is called after list has returned from FLING mode. This gives
       * opportunity to implementing classes to go through ListView hierarchy
       * and refresh/invalidate views.
       *
       * @param lv
       */
      public void refreshViews(ListView lv);

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
   }

   private int headerHeight() {
      try {
         int h = 0;
         ListView lv = getRefreshableView();
         Field f = ListView.class.getDeclaredField("mHeaderViewInfos");
         f.setAccessible(true);
         @SuppressWarnings("unchecked")
         ArrayList<FixedViewInfo> mHeaderViewInfos = (ArrayList<FixedViewInfo>) f.get(lv);
         for (FixedViewInfo i : mHeaderViewInfos) {
            h += i.view.getHeight();
         }
         return h;
      } catch (Exception e) {
         return 0;
      }
   }
}
