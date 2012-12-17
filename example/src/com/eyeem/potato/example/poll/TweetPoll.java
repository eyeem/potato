package com.eyeem.potato.example.poll;

import java.util.ArrayList;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.eyeem.poll.Poll;
import com.eyeem.potato.example.R;
import com.eyeem.potato.example.model.Tweet;
import com.eyeem.potato.example.rest.RESTClient;
import com.eyeem.potato.example.rest.RESTClient.HTTPVerb;
import com.eyeem.potato.example.rest.TweetCallback;
import com.eyeem.potato.example.storage.TweetStorage;

public class TweetPoll extends Poll<Tweet> {

	private String firstElementId = null;
	private String lastElementId = null;
	private String userName = null;

	private Uri timelineUri = Uri.parse("http://api.twitter.com/1/statuses/user_timeline.json");
	private RESTClient restClient;
	private TweetCallback mCallback;

	public TweetPoll(Context ctx, String userName) {
		restClient = new RESTClient(HTTPVerb.GET, timelineUri);
		setStorage(TweetStorage.getInstance().obtainList(userName));
		this.userName = userName;
		mCallback = new TweetCallback(ctx);
	}

	@Override
	protected ArrayList<Tweet> newItems() throws Throwable {
		Bundle requestParams = new Bundle();
		requestParams.putString("screen_name", userName);
		requestParams.putString("include_rts", "true");
		if (firstElementId != null)
			requestParams.putString("since_id", firstElementId);

		return mCallback.handleResponse(restClient.execute(requestParams));
	}

	@Override
	protected ArrayList<Tweet> oldItems() throws Throwable {
		Bundle requestParams = new Bundle();
		requestParams.putString("screen_name", userName);
		requestParams.putString("include_rts", "true");
		if (lastElementId != null)
			requestParams.putString("max_id", lastElementId);
		else if (getStorage().size() > 0)
			requestParams.putString("max_id", getStorage().get(getStorage().size()-1).getId());
		
		return mCallback.handleResponse(restClient.execute(requestParams));
	}

	@Override
	protected int appendNewItems(ArrayList<Tweet> newItems, Listener listener) {
		int newCount = newItems.size();
		for (Tweet item : newItems) {
			if (list.contains(item))
				newCount--;
		}
		if (newItems.size() > 0) 
			firstElementId = newItems.get(0).getId();

		list.addAll(0, newItems);
		return newCount;
	}

	@Override
	protected int appendOldItems(ArrayList<Tweet> oldItems, Listener listener) {
		int count = oldItems.size();
		for (Tweet item : oldItems) {
			if (list.contains(item))
				count--;
		}
		list.addAll(oldItems);

		if (count == 0) {
			exhausted = true;
		} else if (oldItems.size() > 0) {
			lastElementId = oldItems.get(oldItems.size()-1).getId();
		}

		return count;
	}

	@Override
	protected String getSuccessMessage(Context ctx, int newCount) {
		return String.format(ctx.getString(R.string.tweet_poll_response), newCount);
	}
}
