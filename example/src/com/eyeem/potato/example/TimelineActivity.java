package com.eyeem.potato.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.Window;

import com.eyeem.poll.PollListView;
import com.eyeem.poll.PollListView.BusyIndicator;
import com.eyeem.potato.example.adapter.TweetArrayAdapter;
import com.eyeem.potato.example.poll.TweetPoll;
import com.eyeem.potato.example.storage.TweetStorage;

public class TimelineActivity extends Activity implements BusyIndicator{
	private static final String default_user_name = "tobias_heine";
	
	private PollListView listview;
	
	public static final String EXTRA_USER_NAME = "user_name";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TweetStorage.initialize(this);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.activity_timeline);
		
		String userName = null;
		Intent intent = null;
		if ((intent = getIntent()) != null && intent.hasExtra(EXTRA_USER_NAME))
			userName = intent.getExtras().getString(EXTRA_USER_NAME);
		else
			userName = default_user_name;
		
		listview = (PollListView) findViewById(R.id.listview);
		listview.setPoll(new TweetPoll(this, userName));
		listview.setDataAdapter(new TweetArrayAdapter(this, android.R.id.title, userName));
		listview.setNoContentView(LayoutInflater.from(this).inflate(R.layout.view_no_content, null));
		listview.setOnErrorView(LayoutInflater.from(this).inflate(R.layout.view_no_content, null));
		listview.setBusyIndicator(this);
		
		setBusyIndicator(false);
		
		setTitle("Twitter timeline "+userName);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		listview.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		listview.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	@Override
	public void setBusyIndicator(boolean busy_flag) {
		setProgress(busy_flag ? View.VISIBLE : View.INVISIBLE);
	}
}
