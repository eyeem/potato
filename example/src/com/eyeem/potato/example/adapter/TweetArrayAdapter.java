package com.eyeem.potato.example.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.eyeem.poll.PollListView.PollAdapter;
import com.eyeem.potato.example.PotatoApplication;
import com.eyeem.potato.example.R;
import com.eyeem.potato.example.TimelineActivity;
import com.eyeem.potato.example.model.Tweet;
import com.eyeem.potato.example.storage.TweetStorage;
import com.eyeem.storage.Storage;
import com.nostra13.universalimageloader.core.ImageLoader;

public class TweetArrayAdapter extends ArrayAdapter<Tweet> implements PollAdapter {
	private Storage<Tweet>.List mTimeline = null;
	private ImageLoader mLoader;
	
	public TweetArrayAdapter(Context context, int textViewResourceId, String userName) {
		super(context, textViewResourceId);
		mTimeline = TweetStorage.getInstance().obtainList(userName);
		mLoader = ((PotatoApplication)context.getApplicationContext()).getImageLoader();
	}
	
	@Override
	public Tweet getItem(int position) {
		return mTimeline.get(position);
	}
	
	@Override
	public int getCount() {
		return mTimeline.size();
	}
	
	
	class ViewHolder{
		private TextView text;
		private TextView tweet;
		private ImageView userImage; 
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;
		if (convertView == null) {
			holder = new ViewHolder();
			convertView = LayoutInflater.from(getContext()).inflate(R.layout.adapter_tweet, null);
			holder.text = (TextView) convertView.findViewById(R.id.text);
			holder.tweet = (TextView) convertView.findViewById(R.id.tweet);
			holder.userImage = (ImageView) convertView.findViewById(R.id.user_image);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		
		final Tweet tweetObject = getItem(position);
		holder.text.setText(tweetObject.getUser().getName());
		holder.tweet.setText(tweetObject.getText());
		
		holder.userImage.setImageBitmap(null);
		
		if (tweetObject.getRetweetedUser() != null) {
			convertView.setBackgroundDrawable(getContext().getResources().getDrawable(R.drawable.adapter_bg));
			holder.text.setVisibility(View.VISIBLE);
			holder.text.setText("Click to open timeline from "+tweetObject.getRetweetedUser().getName());
			mLoader.displayImage(tweetObject.getRetweetedUser().getImage(), holder.userImage);
			convertView.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					Intent timeline = new Intent(getContext(), TimelineActivity.class);
					timeline.putExtra(TimelineActivity.EXTRA_USER_NAME, tweetObject.getRetweetedUser().getName());
					getContext().startActivity(timeline);
				}
			});
		}else {
			holder.text.setVisibility(View.INVISIBLE);
			convertView.setBackgroundDrawable(getContext().getResources().getDrawable(R.drawable.adapter_bg_normal));
			mLoader.displayImage(tweetObject.getUser().getImage(), holder.userImage);
		}
		
		return convertView;
	}

	@Override
	public void setBusy(boolean value) {}

	@Override
	public void refreshViews(ListView lv) {}

	@Override
	public String idForPosition(int position) {
		return null;
	}

	@Override
	public int positionForId(String id) {
		return 0;
	}
}
