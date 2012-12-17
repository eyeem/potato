package com.eyeem.potato.example.storage;

import android.content.Context;

import com.eyeem.potato.example.model.Tweet;
import com.eyeem.storage.Storage;

public class TweetStorage extends Storage<Tweet> {

	private static TweetStorage sInstance = null;
	private static final int STORAGE_SIZE = 300;

	public TweetStorage(Context context) {
		super(context);
	}

	@Override
	public String id(Tweet object) {
		return object.getId();
	}

	@Override
	public Class<Tweet> classname() {
		return Tweet.class;
	}

	public static TweetStorage getInstance(){
		return sInstance;
	}

	public static void initialize(Context ctx){
		if(sInstance == null){
			sInstance = new TweetStorage(ctx);
			sInstance.init(STORAGE_SIZE);
		}
	}

}
