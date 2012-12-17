Potato Library
=================

Potato Library is an object oriented observable storage & content polling
solution in one. Wrote for Android by EyeEm. It will come with bacon &
whisky in the future.

Usage
============
The following code snippets cover basics of this library. For more see
sample apps.
``` java
// First off extend Storage class e.g.
class TweetStorage extends Storage<Tweet> {

   public TweetStorage(Context context) {
      super(context);
   }

   // ...override these 2 methods

   @Override
   public String id(Tweet tweet) {
      return tweet.id;
   }

   @Override
   public Class<Tweet> classname() {
      return Tweet.class;
   }

   // ...and have some sort of singleton for the storage

   private static TweetStorage sInstance = null;

   public static void initialize(Context context) {
      if (sInstance == null) {
         sInstance = new TweetStorage(context);
         // setup maximum items size
         sInstance.init(100);
      }
   }

   public static TweetStorage getInstance(){
      return sInstance;
   }
}

// ... now somewhere you should be able to do things like:
TweetStorage.List homeTimeline = TweetStorage.obtainList("home_timeline");
homeTimeline.subscribe(new Subscription() {
      @Override
      public void onUpdate() {
         // ...OMG MOAR TWEETS
         // add code here to update UI
      }
   });
homeTimeline.addAll(newTweetsIFetchedFromTheInternet);
```

There's more to the potato! You can use Poll class to manage content fetching
and combine it with some predefined UI components.

``` java
// ...and bind the Poll with PollListView

class TimelineActivity extends Activity {

   class HomeTimelinePoll extends Poll<Tweet> {
      // ...API CALLS HERE
   }

   PollListView listView;
   HomeTimelinePoll poll;

   @Override
   protected void onCreate(Bundle bundle) {
      super.onCreate(bundle);

      // setup poll
      poll = new HomeTimeline();
      poll.setStorage(TweetStorage.getInstance().obtainList("home_timeline"););

      // setup list view
      listView = (PollListView) findViewById(R.id.myListView);
      listView.setPoll(poll);
      listView.setDataAdapter(new TweetAdapter());
   }

   @Override
   protected void onResume() {
      listView.onResume();
   }

   @Override
   protected void onPause() {
      listView.onPause();
   }
}



```

Developed By
============

* Lukasz Wisniewski
* Tobias Heine

License
=======

    Copyright 2012 EyeEm Mobile GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
