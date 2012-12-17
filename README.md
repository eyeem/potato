Potato Library
=================

Potato Library is an object oriented observable storage & content polling
solution in one. Wrote for Android by EyeEm. It will come with bacon &
whisky in the future.

Usage
============
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
