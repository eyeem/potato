package com.eyeem.storage;

import android.content.Context;
import android.util.Log;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectStreamClass;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by vishna on 01/10/14.
 */
public class KryoTransportLayer implements Storage.TransportLayer {

   private Context context;

   public KryoTransportLayer(Context context) {
      this.context = context.getApplicationContext();
   }

   public boolean saveSync(Storage.List list, int limit) {
      Class klazz = null;
      try {
         klazz = list.getStorage().classname();
         File dir = new File(dirname(klazz));
         dir.mkdirs();
         Kryo kyro = new Kryo();
         Output output;
         HashMap<String, Object> data = new HashMap<String, Object>();
         data.put("list", list.toArrayList(limit));
         data.put("meta", list.meta);
         output = new Output(new FileOutputStream(filename(list)));
         kyro.writeObject(output, data);
         output.close();
         return true;
      } catch (Throwable e) {
         if (klazz != null) Log.e(klazz.getSimpleName(), "save() error", e);
         return false;
      }
   }

   public boolean loadSync(Storage.List storageList) {
      Kryo kyro = new Kryo();
      Storage storage = storageList.getStorage();
      Class klazz = storage.classname();
      try {
         Input input = new Input(new FileInputStream(filename(storageList)));
         HashMap<String, Object> data = kyro.readObject(input, HashMap.class);
         ArrayList list = (ArrayList)data.get("list");
         input.close();
         Storage.List transaction = storageList.transaction();
         transaction.meta = (HashMap<String, Object>)data.get("meta");
         // don't add objects that already exist in cache as they're most likely fresher
         for (Object loadedObject : list) {
            Object storedObject = storage.get(storage.id(loadedObject));
            transaction.add(storedObject != null ? storedObject : loadedObject);
         }
         transaction.commit(new Storage.Subscription.Action(Storage.Subscription.LOADED));
         return true;
      } catch (FileNotFoundException e) {
         // clean up
         deleteFilesRecursively(getBaseDir(klazz), klazz);
         Log.w(klazz.getSimpleName(), "load() error: file " + filename(storageList) + " missing");
      } catch (Throwable e) {
         Log.e(klazz.getSimpleName(), "load() error", e);
      }
      storageList.publish(new Storage.Subscription.Action(Storage.Subscription.LOADED)); // prolly should be other thing
      return false;
   }

   private String getBaseDir(Class klass) {
      return context.getCacheDir() + File.separator + klass.getSimpleName() + File.separator;
   }

   private String dirname(Class klass) {
      return getBaseDir(klass) + getSerialVersionUID(klass) + File.separator;
   }

   private String filename(Storage.List list) {
      return dirname(list.getStorage().classname()) + list.getName();
   }

   private String getSerialVersionUID(Class klazz) {
      ObjectStreamClass osc = ObjectStreamClass.lookup(klazz);
      if(osc != null )
         return Long.toString(osc.getSerialVersionUID());
      else
         return "0";
   }

   private void deleteFilesRecursively(String folder, Class klazz) {
      try {
         File f = new File(folder);
         // dig deeper into that folder
         if (f.isDirectory()) {
            // don't delete stuff from the current folder
            if (!f.getAbsolutePath().endsWith(getSerialVersionUID(klazz))) {

               // get the list of files to be deleted
               String[] list = f.list();
               if (list != null)
                  for (String path : list)
                     deleteFilesRecursively(f.getAbsolutePath() + File.separator + path,klazz);

               // don't delete the base folder, have to compare like this to account for File.separator
               if (!f.getAbsolutePath().equals(new File(getBaseDir(klazz)).getAbsolutePath())) {
                  // all files deleted, let's delete the folder
                  Log.d(this.getClass().getSimpleName(), "Storage cleanup, deleting directory: " + f.getAbsolutePath());
                  f.delete();
               }
            }
         } else {
            // delete this file
            Log.d(this.getClass().getSimpleName(), "Storage cleanup, deleting file: " + f.getAbsolutePath());
            f.delete();
         }
      } catch (Throwable t) {
         // this code doesn't throw anything, ever!
      }
   }
}
