package com.eyeem.storage.sql;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.eyeem.storage.Storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import com.eyeem.storage.sql.SQLiteTransportLayer.Converter;

/**
 * Created by vishna on 28/02/15.
 */
public class Database {
   private final static String TAG = Database.class.getSimpleName();
   private final static int VERSION = 1;

   public static class Helper extends SQLiteOpenHelper {

      public final Uri uri;
      private final Context context;
      private final Converter converter;

      public Helper(Context context, String databaseFileName, Converter converter) {
         super(context, databaseFileName, null, VERSION);
         // TODO more generic naming
         this.uri = null; // don't notify about changes
         // this.uri = Uri.parse("appname://storage/classname/objects");
         this.context = context.getApplicationContext();
         this.converter = converter;
      }

      @Override public void onCreate(SQLiteDatabase db) {
         ObjectsTable.create(db);
         ListsTable.create(db);
      }

      @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
         ObjectsTable.drop(db);
         ListsTable.drop(db);
         ObjectsTable.create(db);
         ListsTable.create(db);
      }

      private SQLiteDatabase read() {
         try {
            return getReadableDatabase();
         } catch (SQLException e) {
            Log.w(TAG, e);
            return null;
         }
      }

      private SQLiteDatabase write() {
         try {
            return getWritableDatabase();
         } catch (SQLException e) {
            Log.w(TAG, e);
            return null;
         }
      }

      public void remove(String objectId) {
         SQLiteDatabase db = write();
         if(db == null)
            return;
         db.beginTransaction();
         try{
            db.delete(ObjectsTable.NAME, ObjectsTable.OBJECT_ID + " = ?", new String[]{objectId});
            db.setTransactionSuccessful();
            if (uri != null) context.getContentResolver().notifyChange(uri, null);
         } finally {
            db.endTransaction();
            db.close();
         }
      }

      public void add(Object object) {
         if (object == null)
            return;
         SQLiteDatabase db = write();
         if(db == null)
            return;
         db.beginTransaction();
         try{
            ObjectsTable.insertOrUpdate(object, db, converter);
            db.setTransactionSuccessful();
            if (uri == null) context.getContentResolver().notifyChange(uri, null);
         } finally {
            db.endTransaction();
            db.close();
         }
      }

      public boolean save(Storage.List list) {
         if (list == null)
            return false;
         SQLiteDatabase db = write();
         if(db == null)
            return false;
         db.beginTransaction();
         try {
            Storage.List transaction = list.transaction();
            for (Object object : transaction) {
               ObjectsTable.insertOrUpdate(object, db, converter);
            }
            ListsTable.insertOrUpdate(list, db, converter);
            db.setTransactionSuccessful();
            if (uri != null) context.getContentResolver().notifyChange(uri, null);
            return true;
         } finally {
            db.endTransaction();
            db.close();
         }
      }

      public boolean load(Storage.List list) {
         if (list == null)
            return false;
         SQLiteDatabase db = read();
         if (db == null)
            return false;

         Storage.List transaction = list.transaction();
         try {
            // first read ids & meta
            ListDescriptor listDescriptor = readListDescriptor(db, transaction.getName());
            if (listDescriptor != null) {
               transaction.setMeta(listDescriptor.meta);
               List tmpList = readObjects(db, listDescriptor.idsSQL());
               Collections.sort(tmpList, listDescriptor.comparator(converter));
               transaction.addAll(tmpList);
            }

            return true;
         } finally {
            if (db.isOpen()) db.close();
            transaction.commit(new Storage.Subscription.Action(Storage.Subscription.LOADED));
         }
      }

      private List readObjects(SQLiteDatabase db, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {

         ArrayList<Object> result = new ArrayList<Object>();

         if (db == null) return result;

         Cursor cursor = db.query(ObjectsTable.NAME, ObjectsTable.PROJECTION, selection, selectionArgs, groupBy, having, orderBy);

         if (cursor == null) return result;

         cursor.moveToPosition(-1);

         while (cursor.getCount() > 0 && cursor.moveToNext()) {
            String object_id = cursor.getString(1);
            String object_json = cursor.getString(2);

            if (TextUtils.isEmpty(object_id) || TextUtils.isEmpty(object_json)) continue;

            Object object = converter.fromString(object_id, object_json);

            if (object == null) continue;

            result.add(object);
         }

         cursor.close();

         return result;
      }

      public List readObjects(SQLiteDatabase db, String objectIds) {
         return readObjects(db, ObjectsTable.OBJECT_ID + " IN ("+objectIds+")", null, null, null, null);
      }

      private ListDescriptor readListDescriptor(SQLiteDatabase db, String selection, String[] selectionArgs, String groupBy, String having, String orderBy){
         if (db == null)
            return null;

         Cursor cursor = db.query(ListsTable.NAME, ListsTable.PROJECTION, selection, selectionArgs, groupBy, having, orderBy);
         String list_ids = null;
         String list_meta = null;
         String list_name = null;
         if(cursor.getCount() > 0){
            cursor.moveToFirst();
            list_name = cursor.getString(1);
            list_meta = cursor.getString(2);
            list_ids = cursor.getString(3);
         }
         cursor.close();

         if (TextUtils.isEmpty(list_name)) return null;

         ListDescriptor listDescriptor = new ListDescriptor();
         listDescriptor.name = list_name;
         listDescriptor.meta = converter.fromMetaString(list_meta);
         listDescriptor.ids = new ArrayList<String>(Arrays.asList(TextUtils.split(list_ids, ",")));
         return listDescriptor;
      }

      public ListDescriptor readListDescriptor(SQLiteDatabase db, String listName) {
         return readListDescriptor(db, ListsTable.LIST_NAME + " = ?", new String[]{listName}, null, null, null);
      }
   }

   public static class ObjectsTable {

      public static final String NAME = "objects";

      public static final String ID = "_id";
      public static final String OBJECT_ID = "object_id";
      public static final String OBJECT_JSON = "object_json";

      public static final String PROJECTION[] = {ID, OBJECT_ID, OBJECT_JSON};

      public static void create(SQLiteDatabase db){
         String sql = "create table "+NAME + " ("
            + ID + " integer primary key autoincrement, "
            + OBJECT_ID + " string UNIQUE, "
            + OBJECT_JSON + " string "
            + ");";
         db.execSQL(sql);
      }

      public static void drop(SQLiteDatabase db){
         db.execSQL("drop table if exists " + NAME + ";");
      }

      private static ContentValues values(Object object, Converter converter){
         ContentValues cv = new ContentValues();
         cv.put(OBJECT_ID, converter.id(object));
         cv.put(OBJECT_JSON, converter.string(object));
         return cv;
      }

      public static void insertOrUpdate(Object object, SQLiteDatabase db, Converter converter){
         ContentValues cv = values(object, converter);
         int count = db.update(NAME, cv, OBJECT_ID + " = ?", new String[]{converter.id(object)});
         if (count == 0)
            db.insert(NAME, null, cv);
      }
   }

   public static class ListsTable {

      public static final String NAME = "lists";

      public static final String ID = "_id";
      public static final String LIST_NAME = "list_name";
      public static final String LIST_IDS = "list_ids";
      public static final String LIST_META = "list_meta";

      public static final String PROJECTION[] = {ID, LIST_NAME, LIST_META, LIST_IDS};

      public static void create(SQLiteDatabase db) {
         String sql = "create table " + NAME + " ("
            + ID + " integer primary key autoincrement, "
            + LIST_NAME + " string UNIQUE, "
            + LIST_IDS + " string, "
            + LIST_META + " string "
            + ");";
         db.execSQL(sql);
      }

      public static void drop(SQLiteDatabase db) {
         db.execSQL("drop table if exists " + NAME + ";");
      }

      private static ContentValues values(Storage.List list, Converter converter) {
         ContentValues cv = new ContentValues();
         cv.put(LIST_NAME, list.getName());
         cv.put(LIST_IDS, TextUtils.join(",", list.ids()));
         cv.put(LIST_META, converter.metaString(list.getMeta()));
         return cv;
      }

      public static void insertOrUpdate(Storage.List list, SQLiteDatabase db, Converter converter){
         ContentValues cv = values(list, converter);
         int count = db.update(NAME, cv, LIST_NAME + " = ?", new String[]{list.getName()});
         if (count == 0)
            db.insert(NAME, null, cv);
      }
   }

   public static class ListDescriptor {
      String name;
      List<String> ids;
      HashMap<String, Object> meta;

      public String idsSQL() {
         ArrayList<String> escapedIds = new ArrayList<String>();
         for (String id : ids) {
            escapedIds.add("'"+ id + "'");
         }
         return TextUtils.join(",", escapedIds);
      }

      public Comparator comparator(final Converter converter) {
         return new Comparator() {
            public int compare(Object lhs, Object rhs) {
               int index_lhs = (ids == null || lhs == null || converter == null) ? -1 : ids.indexOf(converter.id(lhs));
               int index_rhs = (ids == null || rhs == null || converter == null) ? -1 : ids.indexOf(converter.id(rhs));

               if (index_lhs == -1) index_lhs = Integer.MAX_VALUE;
               if (index_rhs == -1) index_rhs = Integer.MAX_VALUE;
               return index_lhs < index_rhs ? -1 : (index_lhs == index_rhs ? 0 : 1);
            }
         };
      }
   }
}
