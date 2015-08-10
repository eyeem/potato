[![Build Status](https://travis-ci.org/eyeem/potato.png?branch=master)](https://travis-ci.org/eyeem/potato)

Potato Library
=================

Sick of Android ContentProvider & SQLite database nonsense? This might be your next
favorite library. An object oriented observable storage. Simpler than counting to potato.

Usage
============
The following code snippet covers basics of this library. For more see
sample apps.

``` java
// Let's say we need to store Tweet objects, first
// we need to extend Storage
class TweetStorage extends Storage<Tweet> {

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

   private TweetStorage(Context context) {
      super(context);
   }

   private static TweetStorage sInstance = null;

   public static TweetStorage getInstance(){
      if (sInstance == null) {
         sInstance = new TweetStorage(context);
         sInstance.init();
      }
      return sInstance;
   }
} // that's it!

// ... now somewhere in your code you can do things like this:
TweetStorage.List homeTimeline = TweetStorage.obtainList("home_timeline");
homeTimeline.subscribe(new Subscription() {
      @Override
      public void onUpdate(Action action) {
         // ...OMG MOAR TWEETS
         // add code here to update UI
      }
   });

// somewhere else in the code you can call something like this
// it will populate ADD_ALL action to your subscribers
homeTimeline.addAll(newTweetsIFetchedFromTheInternet);
```

There's more to the potato! You can override standard persistence layer (`KryoTransportLayer`) and have all of your items stored in a SQLite database. See `SQLiteTransportLayer`

Including in your project
=========================

You can either check out the repo manually or grab a snapshot `aar` which is hosted on sonatype repo. To do so, include this in your build.gradle file:

```
dependencies {

    repositories {
        maven {
            url 'https://oss.sonatype.org/content/repositories/snapshots/'
        }
        mavenCentral()
        mavenLocal()
    }

    compile 'com.eyeem.potato:library:0.9.2.5-SNAPSHOT@aar'

    // ...other dependencies
}
```

Developed By
============

* Lukasz Wisniewski
* Tobias Heine

License
=======

    Copyright 2012-2015 EyeEm Mobile GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
