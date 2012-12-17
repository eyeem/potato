package com.eyeem.potato.example.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
/**
 * Inspired by neilgoodman
 * http://neilgoodman.net/2011/12/26/modern-techniques-for-implementing-rest-clients-on-android-4-0-and-below-part-1/
 */
public class RESTClient {
	private static final String TAG = RESTClient.class.getName();
	
	public enum HTTPVerb {
        GET,
        POST,
        PUT,
        DELETE
    }
	
	private HTTPVerb     	mVerb;
    private Uri          	mAction;
    
    public RESTClient(HTTPVerb verb, Uri action) {
		mVerb = verb;
		mAction = action;
	}
    
    public RESTResponse execute(Bundle params) {
    	RESTResponse result = null;
    	try {
            // At the very least we always need an action.
            if (mAction == null) {
                Log.e(TAG, "You did not define an action. REST call canceled.");
                result = new RESTResponse(); // We send an empty response back. The implementation will always need to check the RESTResponse 
                                           // and handle error cases like this.
            }
            
            // Here we define our base request object which we will
            // send to our REST service via HttpClient.
            HttpRequestBase request = null;
            
            // Let's build our request based on the HTTP verb we were
            // given.
            switch (mVerb) {
                case GET: {
                    request = new HttpGet();
                    attachUriWithQuery(request, mAction, params);
                }
                break;
                
                case DELETE: {
                    request = new HttpDelete();
                    attachUriWithQuery(request, mAction, params);
                }
                break;
                
                case POST: {
                    request = new HttpPost();
                    request.setURI(new URI(mAction.toString()));
                    
                    // Attach form entity if necessary. Note: some REST APIs
                    // require you to POST JSON. This is easy to do, simply use
                    // postRequest.setHeader('Content-Type', 'application/json')
                    // and StringEntity instead. Same thing for the PUT case 
                    // below.
                    HttpPost postRequest = (HttpPost) request;
                    
                    if (params != null) {
                        UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(paramsToList(params));
                        postRequest.setEntity(formEntity);
                    }
                }
                break;
                
                case PUT: {
                    request = new HttpPut();
                    request.setURI(new URI(mAction.toString()));
                    
                    // Attach form entity if necessary.
                    HttpPut putRequest = (HttpPut) request;
                    
                    if (params != null) {
                        UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(paramsToList(params));
                        putRequest.setEntity(formEntity);
                    }
                }
                break;
            }
            
            if (request != null) {
                HttpClient client = new DefaultHttpClient();
                
                // Let's send some useful debug information so we can monitor things
                // in LogCat.
                Log.d(TAG, "Executing request: "+ verbToString(mVerb) +": "+ mAction.toString());
                
                // Finally, we send our request using HTTP. 
                HttpResponse response = client.execute(request);
                
                HttpEntity responseEntity = response.getEntity();
                StatusLine responseStatus = response.getStatusLine();
                int        statusCode     = responseStatus != null ? responseStatus.getStatusCode() : 0;
                
                // Here we create our response and send it back
                RESTResponse restResponse = new RESTResponse(responseEntity != null ? EntityUtils.toString(responseEntity) : null, statusCode);
                result =  restResponse;
            } else {
            	// Request was null if we get here, so let's just send our empty RESTResponse like usual.
            	result =  new RESTResponse();
            }
        }
        catch (URISyntaxException e) {
            Log.e(TAG, "URI syntax was incorrect. "+ verbToString(mVerb) +": "+ mAction.toString(), e);
            result =  new RESTResponse();
        }
        catch (UnsupportedEncodingException e) {
            Log.e(TAG, "A UrlEncodedFormEntity was created with an unsupported encoding.", e);
            result =  new RESTResponse();
        }
        catch (ClientProtocolException e) {
            Log.e(TAG, "There was a problem when sending the request.", e);
            result =  new RESTResponse();
        }
        catch (IOException e) {
            Log.e(TAG, "There was a problem when sending the request.", e);
            result =  new RESTResponse();
        }
    	
    	return result;
    }
    
    public static class RESTResponse {
        private String mData;
        private int    mCode;
        public static int RETURN_CODE_OK = 200;
        
        public RESTResponse() {
        }
        
        public RESTResponse(String data, int code) {
            mData = data;
            mCode = code;
        }
        
        public String getData() {
            return mData;
        }
        
        public int getCode() {
            return mCode;
        }
        
        public boolean success(){
        	return mCode == RETURN_CODE_OK;
        }
    }
    
    
    private static void attachUriWithQuery(HttpRequestBase request, Uri uri, Bundle params) {
        try {
            if (params == null) {
                // No params were given or they have already been
                // attached to the Uri.
                request.setURI(new URI(uri.toString()));
            }
            else {
                Uri.Builder uriBuilder = uri.buildUpon();
                
                // Loop through our params and append them to the Uri.
                for (BasicNameValuePair param : paramsToList(params)) {
                    uriBuilder.appendQueryParameter(param.getName(), param.getValue());
                }
                
                uri = uriBuilder.build();
                request.setURI(new URI(uri.toString()));
            }
        }
        catch (URISyntaxException e) {
            Log.e(TAG, "URI syntax was incorrect: "+ uri.toString());
        }
    }
    
    private static String verbToString(HTTPVerb verb) {
        switch (verb) {
            case GET:
                return "GET";
                
            case POST:
                return "POST";
                
            case PUT:
                return "PUT";
                
            case DELETE:
                return "DELETE";
        }
        
        return "";
    }
    
    private static List<BasicNameValuePair> paramsToList(Bundle params) {
        ArrayList<BasicNameValuePair> formList = new ArrayList<BasicNameValuePair>(params.size());
        
        for (String key : params.keySet()) {
            Object value = params.get(key);
            
            // We can only put Strings in a form entity, so we call the toString()
            // method to enforce. We also probably don't need to check for null here
            // but we do anyway because Bundle.get() can return null.
            if (value != null) formList.add(new BasicNameValuePair(key, value.toString()));
        }
        
        return formList;
    }
    
}
