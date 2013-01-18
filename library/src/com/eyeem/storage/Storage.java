package com.eyeem.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;

import android.content.Context;
import android.util.Log;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Observable storage for objects of type {@link T}. All objects
 * are stored in a {@link LruCache}. Stored items can be accessed
 * directly via {@link #get(String)} or they can be organized into
 * {@link List}s. Any change in storage is reflected in relevant
 * {@link List}s. These changes can be observed using #{@link Subscription}.
 * One can subscribe to be notified of single object changes using
 * {@link #subscribe(String, Subscription)} or list changes using
 * {@link List#subscribe(Subscription)}
 *
 * <p>
 * Within storage there can be only one {@link T} item with the given id but
 * it can appear in multiple @{link List}s.
 *
 * <p>
 * Subclasses must implement {@link #id(Object)} method to identify objects
 * that are stored and {@link #classname()} returning {@link Class}.
 * @param <T>
 */
@SuppressWarnings("unchecked")
public abstract class Storage<T> {

   LruCache<String, T> cache;
   HashMap<String, List> lists;
   HashMap<String, Subscribers> subscribers;
   Context context;
   Storage<T> storage;

   int size;

   public Storage(Context context) {
      this.context = context;
   }

   /**
    * Initalize storage, make sure you run this only once
    * @param size
    */
   public void init(int size) {
      cache = new LruCache<String, T>(this.size = size);
      lists = new HashMap<String, List>();
      subscribers = new HashMap<String, Subscribers>();
      storage = this;
   }

   /**
    * Removes all items and lists from storage
    */
   public void clearAll() {
      for (List list : lists.values())
         list.clear();
      cache.evictAll();
   }

   /**
    * Deletes single item from storage and removes
    * all references in lists
    * @param id
    */
   public void delete(String id) {
      T toBeRemoved;
      if ((toBeRemoved = cache.remove(id)) != null) {
         Set<Entry<String, List>> set = lists.entrySet();
         Iterator<Entry<String, List>> it = set.iterator();
         while (it.hasNext()) {
            Entry<String, List> entry = it.next();
            entry.getValue().remove(toBeRemoved);
         }
      }
      unsubscribeAll(id);
   }

   /**
    * Checks if item with the given id is in the storage
    * @param id
    * @return
    */
   public boolean contains(String id) {
      return cache != null && id != null && cache.get(id) != null;
   }

   /**
    * Gets item by its id
    * @param id
    * @return
    */
   public T get(String id) {
      return cache == null ? null : cache.get(id);
   }

   /**
    * Pushes an item to storage, notifies all relevant
    * item & lists subscribers.
    * @param t
    */
   public void push(T t) {
      String id = id(t);
      cache.put(id(t), t);
      if (subscribers.get(id) != null) {
         subscribers.get(id).updateAll("push");
      }
      for (List list : lists.values()) {
         if (list.ids.contains(id)) {
            list.subscribers.updateAll("push");
         }
      }
   }

   /**
    * Adds subscription for the item
    * @param id
    * @param subscription
    */
   public void subscribe(String id, Subscription subscription) {
      if (subscribers.get(id) == null) {
         subscribers.put(id, new Subscribers());
      }
      subscribers.get(id).addSubscriber(subscription);
   }

   /**
    * Unsubscribe from item notifications
    * @param id
    * @param subscription
    */
   public void unsubscribe(String id, Subscription subscription) {
      if (subscribers.get(id) == null)
         return;
      subscribers.get(id).removeSubscriber(subscription);
   }

   /**
    * Remove all item subscriptions
    * @param id
    */
   public void unsubscribeAll(String id) {
      if (subscribers.get(id) == null)
         return;
      subscribers.get(id).removeAllSubscribers();
   }

   private void addOrUpdate(String id, T object) {
      cache.put(id, object);
   }

   /**
    * Lazy initializes instance of {@link List}.
    * @param name List's name
    * @return
    */
   public List obtainList(String name) {
      if (lists.get(name) == null) {
         List list = new List(name);
         lists.put(name, list);
      }
      return lists.get(name);
   }

   /**
    * Removes the list
    * @param name
    */
   public void removeList(String name) {
      lists.remove(name);
   }

   public void clearList(List list) {
      // TODO check which ids exist in other lists and don't remove those from cache
   }

   /**
    * Keeps items on the list, removes everything else
    * @param list to be preserved
    * @return removed items count
    */
   public int retainList(List list) {
      int count = 0;
      Map<String, T> snapshot = cache.snapshot();
      for (String id : snapshot.keySet()) {
         if (id != null && !list.ids.contains(id)) {
            count += cache.remove(id) == null ? 0 : 1;
         }
      }
      for (List otherList : lists.values()) {
         if (otherList.name.equals(list.name))
            continue;
         otherList.ids.clear();
      }
      return count;
   }

   /**
    * Current storage items count
    * @return
    */
   public int currentSize() { return cache.snapshot().size(); }

   /**
    * Max allowed storage items count
    * @return
    */
   public int maxSize() { return size; }

   /**
    * Unique item identifier
    * @param object
    * @return
    */
   public abstract String id(T object);

   /**
    * @return Associated template {@link Class}
    */
   public abstract Class<T> classname();

   /**
    * {link Storage}'s lightweight array. This is a facade
    * containing just item ids. Within storage there can be
    * only one item with the given id but it can be listed in
    * several locations.
    */
   public class List implements Iterable<T>, java.util.List<T> {
      private Vector<String> ids;
      private Subscribers subscribers;
      private String name;
      protected int trimSize;
      protected List transaction;

      private List(String name) {
         ids = new Vector<String>();
         subscribers = new Subscribers();
         this.name = name;
         trimSize = 30;
      }

      /**
       * Transaction constructor
       * @param list
       */
      private List(List list) {
         ids = new Vector<String>();
         ids.addAll(list.ids);
         subscribers = new Subscribers();
         this.name = list.name;
         this.dedupe = list.dedupe;
         this.comparator = list.comparator;
         trimSize = list.trimSize;
         transaction = list;
         mute();
      }

      /**
       * Subscribe to list notifications
       * @param subscription
       */
      public void subscribe(Subscription subscription) {
         subscribers.addSubscriber(subscription);
      }

      /**
       * Unsubscribe from list notifications
       * @param subscription
       */
      public void unsubscribe(Subscription subscription) {
         subscribers.removeSubscriber(subscription);
      }

      /**
       * Unsubscribe all notifications
       */
      public void unsubscribeAll() {
         subscribers.removeAllSubscribers();
      }

      private String dirname() {
         return context.getCacheDir() + "/" + classname().getSimpleName() + "/";
      }

      public String filename() {
         return dirname() + name + ".json";
      }

      /**
       * Load items from persistence. Async.
       */
      public void load() {
         if (size() > 0)
            return;
         Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
               Kryo kyro = new Kryo();
               try {
                  Input input = new Input(new FileInputStream(filename()));
                  ArrayList<T> list = kyro.readObject(input, ArrayList.class);
                  input.close();
                  addAll(list);
                  // FIXME don't add objects that already exist in cache as they're most likely fresher
               } catch (Throwable e) {
                  Log.e(classname().getSimpleName(), "load() error", e);
               }
            }
         });
         t.setPriority(Thread.MIN_PRIORITY);
         t.run();
      }

      /**
       * Persist items. Async.
       */
      public void save() {
         Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
               try {
                  File dir = new File(dirname());
                  dir.mkdirs();
                  Kryo kyro = new Kryo();
                  Output output;
                  output = new Output(new FileOutputStream(filename()));
                  kyro.writeObject(output, toArrayList(trimSize));
                  output.close();
               } catch (Throwable e) {
                  Log.e(classname().getSimpleName(), "save() error", e);
               }
            }
         });
         t.setPriority(Thread.MIN_PRIORITY);
         t.run();
      }

      @Override
      public Iterator<T> iterator() {
         final Iterator<String> i = ids.iterator();
         return new Iterator<T>() {

            @Override
            public boolean hasNext() {
               return i.hasNext();
            }

            @Override
            public T next() {
               return cache.get(i.next());
            }

            @Override
            public void remove() {
               i.remove();
            }
         };
      }

      @Override
      public boolean add(T object) {
         String id = id(object);
         addOrUpdate(id, object);
         if ((!dedupe) || (dedupe && !ids.contains(id)))
            ids.add(id);
         sort();
         subscribers.updateAll("add");
         return true;
      }

      @Override
      public void add(int location, T object) {
         String id = id(object);
         addOrUpdate(id, object);
         if ((!dedupe) || (dedupe && !ids.contains(id)))
            ids.add(location, id);
         sort();
         subscribers.updateAll("add");
      }

      @Override
      public boolean addAll(Collection<? extends T> collection) {
         for (T object : collection) {
            addOrUpdate(id(object), object);
            if ((!dedupe) || (dedupe && !ids.contains(id(object))))
               ids.add(id(object));
         }
         sort();
         subscribers.updateAll("addAll");
         return false;
      }

      @Override
      public boolean addAll(int location, Collection<? extends T> collection) {
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            String id = id((T)t);
            if ((!dedupe) || (dedupe && !ids.contains(id)))
               collectionIds.add(id);
            addOrUpdate(id, (T)t);
         }
         boolean value = ids.addAll(location, collectionIds);
         sort();
         subscribers.updateAll("addAll");
         return value;
      }

      public boolean addAllUpfront(Collection<? extends T> collection) {
         Subscription.Action action = new Subscription.Action("addAllUpfront");
         action.beforeIds = (Vector<String>)ids.clone();
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            String id = id((T)t);
            if ((!dedupe) || (dedupe && !ids.contains(id)))
               collectionIds.add(id);
            addOrUpdate(id, (T)t);
         }
         boolean value = ids.addAll(0, collectionIds);
         sort();
         action.afterIds = (Vector<String>)ids.clone();
         subscribers.updateAll(action);
         return value;
      }

      @Override
      public void clear() {
         ids.clear();
         subscribers.updateAll("clear");
      }

      @Override
      public boolean contains(Object object) {
         return ids.contains(id((T) object));
      }

      @Override
      public boolean containsAll(Collection<?> collection) {
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            collectionIds.add(id((T)t));
         }
         return ids.containsAll(collectionIds);
      }

      @Override
      public T get(int location) {
         return cache.get(ids.get(location));
      }

      @Override
      public int indexOf(Object object) {
         return ids.indexOf(id((T) object));
      }

      @Override
      public boolean isEmpty() {
         return ids.isEmpty();
      }

      @Override
      public int lastIndexOf(Object object) {
         return ids.lastIndexOf(id((T)object));
      }

      @Override
      public ListIterator<T> listIterator() {
         // TODO implement
         throw new NoSuchMethodError();
      }

      @Override
      public ListIterator<T> listIterator(int location) {
         // TODO implement
         throw new NoSuchMethodError();
      }

      @Override
      public T remove(int location) {
         String id = ids.get(location);
         ids.remove(id);
         subscribers.updateAll("remove");
         return cache.get(id);
      }

      @Override
      public boolean remove(Object object) {
         boolean value = ids.remove(id((T) object));
         if (value)
            subscribers.updateAll("remove");
         return value;
      }

      @Override
      public boolean removeAll(Collection<?> collection) {
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            collectionIds.add(id((T)t));
         }
         boolean value = ids.removeAll(collectionIds);
         subscribers.updateAll("removeAll");
         return value;
      }

      @Override
      public boolean retainAll(Collection<?> collection) {
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            collectionIds.add(id((T)t));
         }
         boolean value = ids.retainAll(collectionIds);
         subscribers.updateAll("retainAll");
         return value;
      }

      @Override
      public T set(int location, T object) {
         T previous = cache.get(ids.get(location));
         ids.set(location, id(object));
         return previous;
      }

      @Override
      public int size() {
         return ids.size();
      }

      @Override
      public java.util.List<T> subList(int start, int end) {
         if (start < 0 || end > size()) {
            throw new IndexOutOfBoundsException("Wrong subList params, start="+start+", end="+end);
         }
         // TODO implement
         throw new NoSuchMethodError();
      }

      public ArrayList<T> toArrayList(int count) {
         ArrayList<T> list = new ArrayList<T>();
         for (String id : ids) {
            T object = cache.get(id);
            if (object != null) {
               list.add(object);
               count--;
            }
            if (count < 0)
               break;
         }
         return list;
      }

      @Override
      public Object[] toArray() {
         T[] array = (T[])Array.newInstance(classname(), ids.size());
         int index  = 0;
         for (String id : ids) {
            T t = cache.get(id);
            if (t != null) {
               array[index++] = t;
            }
         }
         return array;
      }

      @Override
      public <T> T[] toArray(T[] array) {
         if (array == null || array.length < ids.size()) {
            // provided array's capacity is not good enough for our needs
            array = (T[])Array.newInstance(classname(), ids.size());
         } else if (array.length > ids.size()) {
            // and that's just too much
            for (int i=0; i < array.length; i++) {
               array[i] = null;
            }
         }
         int index  = 0;
         for (String id : ids) {
            T t = (T) cache.get(id);
            if (t != null) {
               array[index++] = t;
            }
         }
         return array;
      }

////// LIST POLICIES
      private Comparator<T> comparator;
      private boolean dedupe;

      /**
       * If you wish list to be sorted, provide comparator. If you
       * pass null, sorting is disabled (default).
       * @param comparator
       */
      public void enableSort(Comparator<T> comparator) {
         this.comparator = comparator;
         sort();
      }

      /**
       * Enable duplicate removal. By default this is off.
       * Note this doesn't mean items are duplicated in memory/cache.
       * Same item (id) can be listed multiple times on the {link List} though.
       * @param dedupe
       */
      public void enableDedupe(boolean dedupe) {
         this.dedupe = dedupe;
         if (dedupe && ids.size() > 1) {
            Vector<String> tmp = new Vector<String>();
            for (String id : ids) {
               if (!tmp.contains(id)) {
                  tmp.add(id);
               }
            }
            ids = tmp;
         }
      }

      private void sort() {
         if (comparator == null)
            return;
         Collections.sort(ids, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
               T tLhs = cache.get(lhs);
               T tRhs = cache.get(rhs);
               return comparator.compare(tLhs, tRhs);
            }
         });
      }

      /**
       * Trim list to the given size.
       * @param size
       */
      public void trim(int size) {
         Vector<String> trimmed = new Vector<String>(size);
         trimmed.addAll(ids.subList(0, Math.min(ids.size(), size)));
         ids = trimmed;
         subscribers.updateAll("trim");
      }

      /**
       * Same as {@link #trim(int)} just happens at the end.
       * @param size
       * @return removed items count
       */
      public int trimAtEnd(int size) {
         int removedCount = 0;
         Vector<String> trimmed = new Vector<String>(size);
         trimmed.addAll(ids.subList(Math.max(0, ids.size() - size), ids.size()));
         removedCount = ids.size() - trimmed.size();
         ids = trimmed;
         subscribers.updateAll("trimAtEnd");
         return removedCount;
      }

      /**
       * Keeps size elements at the beginning and the end of the list
       * making a gap in the middle
       * @param size
       * @return
       */
      public int makeGap(int size) {
         int n = ids.size();
         int gapSize = n - 2*size;
         if (gapSize <= 0)
            return 0;
         java.util.List<String> start = ids.subList(0, size);
         java.util.List<String> end = ids.subList(n-size, n);
         Vector<String> newIds = new Vector<String>();
         newIds.addAll(start);
         newIds.addAll(end);
         ids = newIds;
         return gapSize;
      }

      /**
       * Causes notifications not to be propagated to subscribers
       */
      public void mute() {
         subscribers.mute();
      }

      /**
       * Reverts {@link #mute()}.
       */
      public void unmute() {
         subscribers.unmute();
      }

      /**
       * Returns copy of the list on which you can make changes
       * and then {@link #commit()} once you're done
       * @return
       */
      public List transaction() {
         return new List(this);
      }

      /**
       * Validate transaction changes.
       */
      public void commit() {
         if (transaction != null) {
            transaction.ids = ids;
            transaction.subscribers.updateAll("commit");
         }
      }

      /**
       * Last item's id.
       * @return
       */
      public String lastId() {
         return ids.lastElement();
      }

      /**
       * If list contains null items, clears the list.
       * @return true if list is consistent (has no null items)
       */
      public boolean ensureConsistence() {
         for (String id : ids) {
            if (cache.get(id) == null) {
               ids.clear();
               subscribers.updateAll("clear");
               return false;
            }
         }
         return true;
      }

      /**
       * @return Associated {@link Storage} item.
       */
      public Storage<T> getStorage() { return storage; }

      /**
       * Item's id for the given position.
       * @param position
       * @return
       */
      public String idForPosition(int position) {
         return ids.get(position);
      }

      /**
       * First array index of the given item's id.
       * @param id
       * @return
       */
      public int indexOfId(String id) {
         return ids.indexOf(id);
      }

      /**
       * @param id
       * @return Item of the given id or null.
       */
      public T getById(String id) {
         if (ids.contains(id))
            return storage.get(id);
         else
            return null;
      }
   }

   /**
    * Facility to manage Subscribers
    */
   public static class Subscribers {
      private boolean muted;
      private ArrayList<Subscription> subscriptions = new ArrayList<Subscription>();

      public void addSubscriber(Subscription subscription) {
         subscriptions.add(subscription);
      }

      public void removeSubscriber(Subscription subscription) {
         subscriptions.remove(subscription);
      }

      public void removeAllSubscribers() {
         subscriptions.clear();
      }

      public void updateAll(String actionName) {
         updateAll(new Subscription.Action(actionName));
      }

      public void updateAll(Subscription.Action action) {
         if (muted)
            return;
         for (Subscription s : (ArrayList<Subscription>)subscriptions.clone()) {
            if (s != null)
               s.onUpdate(action);
         }
      }

      private void mute() {muted = true;}
      private void unmute() {muted = false;}
   }

   /**
    * Interface to receiving notifications on {@link Storage}
    * and {@link List} items.
    */
   public interface Subscription {
      public static class Action {
         public String name;
         public Vector<String> beforeIds;
         public Vector<String> afterIds;

         public Action(String name) {
            this.name = name;
         }

         public Action(String name, Vector<String> beforeIds, Vector<String> afterIds) {
            this.name = name;
            this.beforeIds = beforeIds;
            this.afterIds = afterIds;
         }
      }
      public void onUpdate(Action action);
   }
}
