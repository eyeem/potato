package com.eyeem.potato.example.rest;

import java.util.ArrayList;

import android.content.Context;

import com.eyeem.potato.example.rest.RESTClient.RESTResponse;

public abstract class RESTCallback <T>{
	protected Context mContext;
	
	public RESTCallback(Context ctx) {
		mContext = ctx;
	}
	
	public ArrayList<T> handleResponse(RESTResponse response){
		if (response.success()) {
			return doUnmarshalling(response.getData());
		}else {
			//TODO: add some error handling here
			return new ArrayList<T>();
		}
	}
	
	protected abstract ArrayList<T> doUnmarshalling(String data);
}

