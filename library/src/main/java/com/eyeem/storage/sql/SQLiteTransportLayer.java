package com.eyeem.storage.sql;

import android.content.Context;

import com.eyeem.storage.Storage;

import java.util.HashMap;

/**
 * Created by vishna on 28/02/15.
 */
public class SQLiteTransportLayer implements Storage.TransportLayer {

   Database.Helper helper;

   public SQLiteTransportLayer(Context context, String filename, Converter converter) {
      helper = new Database.Helper(context, filename, converter);
   }

   @Override public boolean saveSync(Storage.List list, int itemCount) {
      return helper.save(list);
   }

   @Override public boolean loadSync(Storage.List list) {
      return helper.load(list);
   }

   public interface Converter {
      public String string(Object object);
      public String metaString(HashMap<String, Object> meta);

      /**
       * your id shouldn't contain an apostrophe ' or a comma ,
       * @param object
       * @return
       */
      public String id(Object object);

      public Object fromString(String objectId, String objectString);
      public HashMap<String, Object> fromMetaString(String objectString);
   }
}
