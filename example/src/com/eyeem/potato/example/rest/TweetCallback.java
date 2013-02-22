package com.eyeem.potato.example.rest;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

import com.eyeem.potato.example.model.Tweet;
import com.eyeem.potato.example.model.User;

public class TweetCallback extends RESTCallback<Tweet> {

   public TweetCallback(Context ctx) {
      super(ctx);
   }

   @Override
   protected ArrayList<Tweet> parse(String data) {
      ArrayList<Tweet> result = null;
      try {
         JSONArray timeline = new JSONArray(data);
         result = new ArrayList<Tweet>();
         for (int i = 0; i < timeline.length(); i++) {
            JSONObject jsonTweet = timeline.getJSONObject(i);
            Tweet tweet = new Tweet();
            tweet.setId(jsonTweet.optString("id_str"));
            tweet.setText(jsonTweet.optString("text"));

            JSONObject jsonUser = jsonTweet.optJSONObject("user");
            if (jsonUser != null) {
               User user = parseUser(jsonUser);
               tweet.setUser(user);
            }

            JSONObject jsonRetweet = jsonTweet.optJSONObject("retweeted_status");
            if (jsonRetweet != null) {
               jsonUser = jsonRetweet.optJSONObject("user");
               if (jsonUser != null) {
                  tweet.setRetweetedUser(parseUser(jsonUser));
               }
            }

            result.add(tweet);
         }
      } catch (JSONException e) {
         result = new ArrayList<Tweet>();
      }
      return result;
   }

   private User parseUser(JSONObject jsonUser) {
      User user = new User();
      user.setId(jsonUser.optString("id_str"));
      user.setName(jsonUser.optString("screen_name"));
      user.setImage(jsonUser.optString("profile_image_url"));
      return user;
   }
}
