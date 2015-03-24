package com.eyeem.storage;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observable storage for objects of type {@link T}. All objects
 * are stored in a {@link HashMap}. Stored items can be accessed
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

   private static final String DOT = ".";
   private static TransportLayer DEFAULT_TRANSPORT_LAYER = null;

   ConcurrentHashMap<String, T> cache;
   HashMap<String, WeakEqualReference<List>> lists;
   CopyOnWriteArrayList<WeakEqualReference<List>> transactions;
   protected HashMap<String, Subscribers> subscribers;
   Context context;
   Storage<T> storage;
   HashSet<String> persistentItems;
   CopyOnWriteArrayList<List> persistentLists;

   int size;

   public Storage(Context context) {
      this.context = context;
   }

   /**
    * Initalize storage, make sure you run this only once
    * @param size
    */
   public void init() {
      cache = new ConcurrentHashMap<String, T>();
      lists = new HashMap<String, WeakEqualReference<List>>();
      subscribers = new HashMap<String, Subscribers>();
      transactions = new CopyOnWriteArrayList<WeakEqualReference<List>>();
      persistentItems = new HashSet<String>();
      persistentLists = new CopyOnWriteArrayList<List>();
      storage = this;
   }

   /**
    * Removes all items and lists from storage
    */
   public void clearAll() {
      try {
         for (WeakEqualReference<List> _list : lists.values()) {
            List list = _list.get();
            if (list != null) {
               list.clear();
            }
         }
         cache.clear();
         transactions.clear();
         persistentItems.clear();
      } catch (NullPointerException npe) { /* NO-OP */ }
   }

   /**
    * Saves all lists
    */
   public void saveAll() {
      for (WeakEqualReference<List> _list : lists.values()) {
         List list = _list.get();
         if (list != null) {
            list.saveSync();
         }
      }
   }

   /**
    * Counts all lists
    */
   public int listCount() {
      int i = 0;
      for (WeakEqualReference<List> _list : lists.values()) {
         List list = _list.get();
         if (list != null) {
            i++;
         }
      }
      return i;
   }

   /**
    * Saves selected lists
    * @param selectedLists
    */
   public void save(java.util.List<String> selectedLists) {
      for (WeakEqualReference<List> _list : lists.values()) {
         List list = _list.get();
         if (list != null && selectedLists.contains(list.name))
            list.save();
      }
   }

   /**
    * Deletes single item from storage and removes
    * all references in lists
    * @param id
    */
   public void delete(String id) {
      Subscription.Action delete = new Subscription.Action(Subscription.DELETE).param("objectId", id);
      T toBeRemoved;
      if ((toBeRemoved = cache.remove(id)) != null) {
         Set<Entry<String, WeakEqualReference<List>>> set = lists.entrySet();
         Iterator<Entry<String, WeakEqualReference<List>>> it = set.iterator();
         while (it.hasNext()) {
            Entry<String, WeakEqualReference<List>> entry = it.next();
            List list = entry.getValue().get();
            if (list != null)
               list.remove(toBeRemoved);
         }
      }
      persistentItems.remove(id);
      if (subscribers.get(id) != null) {
         subscribers.get(id).updateAll(delete);
      }
      unsubscribeAll(id);
   }

   private void evictUnassociatedEntries() {
      HashSet<String> idsToKeep = new HashSet<String>(persistentItems);

      ArrayList<WeakEqualReference<List>> existingLists = new ArrayList<WeakEqualReference<List>>(lists.values());
      existingLists.addAll(transactions);
      for (WeakEqualReference<List> list_ : existingLists) {
         List list = list_.get();
         if (list == null)
            continue;
         idsToKeep.addAll(list.ids());
      }

      HashSet<String> allIds = new HashSet<String>(cache.keySet());
      int count = 0;
      for (String id : allIds) {
         if (!idsToKeep.contains(id)) {
            if(cache.remove(id) != null) {
               count++;
            }
         }
      }
      Log.d(getClass().getSimpleName(), "evicted " + count + " items");
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
      return cache == null || id == null ? null : cache.get(id);
   }

   /**
    * Gets all items
    * @return
    */
   public Collection<T> getAll() {
      return cache == null ? Collections.<T>emptyList() : cache.values();
   }

   /**
    * Pushes an item to storage, notifies all relevant
    * item & lists subscribers.
    * @param t
    */
   public void push(T t) {
      pushWithParams(t, null);
   }

   /**
    * Pushes an item to storage, notifies all relevant
    * item & lists subscribers.
    * @param t
    * @param params
    */
   public void pushWithParams(T t, HashMap<String, Object> params) {
      String id = id(t);
      addOrUpdate(id, t);
      Subscription.Action push = new Subscription.Action(Subscription.PUSH).param("objectId", id);
      if (params != null) {
         for (Map.Entry<String, Object> e : params.entrySet()) {
            push.param(e.getKey(), e.getValue());
         }
      }

      if (subscribers.get(id) != null) {
         subscribers.get(id).updateAll(push);
      }
      for (WeakEqualReference<List> _list : lists.values()) {
         List list = _list.get();
         if (list != null && list.ids.contains(id)) {
            list.subscribers.updateAll(push);
         }
      }
   }

   public void retain(T t) {
      if (t == null) return;
      persistentItems.add(id(t));
      push(t);
   }

   public void recycle(T t) {
      persistentItems.remove(id(t));
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

   /**
    * Remove all subscriptions ever
    */
   public void unsubscribeAll() {
      subscribers.clear();
      for (WeakEqualReference<List> _list : lists.values()) {
         List list = _list.get();
         if (list != null)
            list.unsubscribeAll();
      }
   }

   protected void addOrUpdate(String id, T object) {
      cache.put(id, object);
   }

   /**
    * Lazy initializes instance of {@link List}.
    * @param name List's name
    * @return
    */
   public List obtainList(String name) {
      evictUnassociatedEntries();
      WeakEqualReference<List> _list = lists.get(name);
      List list;
      if (_list == null) {
         list = new List(name);
         lists.put(name, new WeakEqualReference<List>(list));
         return list;
      }
      list = _list.get();
      if (list == null) {
         list = new List(name);
         lists.put(name, new WeakEqualReference<List>(list));
      }
      return list;
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
    * Current storage items count
    * @return
    */
   public int currentSize() { return cache.size(); }

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
      private CopyOnWriteArrayList<String> ids;
      private Subscribers subscribers;
      private String name;
      private int retainCount;
      protected int trimSize;
      protected List transaction;
      protected HashMap<String, Object> meta;

      private List(String name) {
         ids = new CopyOnWriteArrayList<String>();
         subscribers = new Subscribers();
         this.name = name;
         trimSize = 30;
         retainCount = 0;
      }

      public void setTrimSize(int trimSize) {
         this.trimSize = trimSize;
      }

      public int getTrimSize() {
         return trimSize;
      }

      public List setMeta(String key, Object value) {
         if (meta == null) {
            meta = new HashMap<String, Object>();
         }
         meta.put(key, value);
         return this;
      }

      public List setMeta(HashMap<String, Object> meta) {
        this.meta = meta;
        return this;
      }

      public Object getMeta(String key) {
         if (meta == null)
            return null;
         return meta.get(key);
      }

      /**
       * Returns a mutable copy of the meta data or null
       */
      public HashMap<String, Object> getMeta() {
         return meta != null ? new HashMap<String, Object>(meta) : null;
      }

      public CopyOnWriteArrayList<String> ids() {
         return ids;
      }

      /**
       * Transaction constructor
       * @param list
       */
      private List(List list) {
         ids = new CopyOnWriteArrayList<String>();
         ids.addAll(list.ids);
         subscribers = new Subscribers();
         this.name = list.name;
         this.dedupe = list.dedupe;
         this.comparator = list.comparator;
         if (list.meta != null) {
            this.meta = new HashMap<String, Object>();
            this.meta.putAll(list.meta);
         }
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

      public String getName() {
         return name;
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
               loadSync();
            }
         });
         t.setPriority(Thread.MIN_PRIORITY);
         t.run();
      }

      /**
       * Load objects from associated json file and appends them
       * to the end of the list
       * @return
       */
      public boolean loadSync() {
         return transportLayer().loadSync(this);
      }

      /**
       * Persist items. Async.
       */
      public void save() {
         Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
               saveSync();
            }
         });
         t.setPriority(Thread.MIN_PRIORITY);
         t.run();
      }

      /**
       * Save items synchronously. Mind the trimSize setting.
       * @return true if successful, false otherwise.
       */
      public boolean saveSync() {
         return saveSync(trimSize);
      }

      public boolean saveSync(int limit) {
         return transportLayer().saveSync(this, limit);
      }

      public void retain() {
         if (retainCount == 0) {
            persistentLists.add(this);
         }
         retainCount++;
      }

      public void recycle() {
         retainCount--;
         if (retainCount <= 0) {
            retainCount = 0;
            persistentLists.remove(this);
         }
      }

      public int retainCount() {
         return retainCount;
      }

      private T get(String id) {
         return cache.get(id);
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
               return get(i.next());
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
         subscribers.updateAll(Subscription.ADD);
         return true;
      }

      @Override
      public void add(int location, T object) {
         String id = id(object);
         addOrUpdate(id, object);
         if ((!dedupe) || (dedupe && !ids.contains(id)))
            ids.add(location, id);
         sort();
         subscribers.updateAll(Subscription.ADD);
      }

      @Override
      public boolean addAll(Collection<? extends T> collection) {
         for (T object : collection) {
            addOrUpdate(id(object), object);
            if ((!dedupe) || (dedupe && !ids.contains(id(object))))
               ids.add(id(object));
         }
         sort();
         subscribers.updateAll(Subscription.ADD_ALL);
         return false;
      }

      @Override
      public boolean addAll(int location, Collection<? extends T> collection) {
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            String id = id((T)t);
            if (dedupe) {
               if (ids.contains(id)) {
                  // if there is a duplicate it should get removed so that
                  // it gets inserted near requested location
                  ids.remove(id);
               }
               if (!collectionIds.contains(id)) {
                  // the list that's going to be appended might contain duplicates on its own
                  collectionIds.add(id);
               }
            } else {
               // add anything
               collectionIds.add(id);
            }
            addOrUpdate(id, (T)t);
         }
         boolean value = ids.addAll(location, collectionIds);
         sort();
         subscribers.updateAll(Subscription.ADD_ALL);
         return value;
      }

      public boolean addUpFront(Collection<? extends T> collection, HashMap<String, Object> params) {
         Subscription.Action action = new Subscription.Action(Subscription.ADD_UPFRONT);
         action.params = params;
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            String id = id((T)t);
            if ((!dedupe) || (dedupe && !ids.contains(id)))
               collectionIds.add(id);
            addOrUpdate(id, (T)t);
         }
         boolean value = ids.addAll(0, collectionIds);
         sort();
         subscribers.updateAll(action);
         return value;
      }

      @Override
      public void clear() {
         ids.clear();
         subscribers.updateAll(Subscription.CLEAR);
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
         return get(ids.get(location));
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
         return ids.lastIndexOf(id((T) object));
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
         subscribers.updateAll(Subscription.REMOVE);
         return cache.get(id);
      }

      /**
       * Removes object by its id
       * @param id
       * @return removed object
       */
      public T removeById(String id) {
         int index = indexOfId(id);
         if (index >= 0)
            return remove(index);
         else
            return null;
      }

      @Override
      public boolean remove(Object object) {
         boolean value = ids.remove(id((T) object));
         if (value)
            subscribers.updateAll(Subscription.REMOVE);
         return value;
      }

      @Override
      public boolean removeAll(Collection<?> collection) {
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            collectionIds.add(id((T)t));
         }
         boolean value = ids.removeAll(collectionIds);
         subscribers.updateAll(Subscription.REMOVE_ALL);
         return value;
      }

      @Override
      public boolean retainAll(Collection<?> collection) {
         ArrayList<String> collectionIds = new ArrayList<String>();
         for (Object t : collection) {
            collectionIds.add(id((T)t));
         }
         boolean value = ids.retainAll(collectionIds);
         subscribers.updateAll(Subscription.RETAIN_ALL);
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
            T object = get(id);
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
            T t = get(id);
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
            T t = (T) get(id);
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
            CopyOnWriteArrayList<String> tmp = new CopyOnWriteArrayList<String>();
            for (String id : ids) {
               if (!tmp.contains(id)) {
                  tmp.add(id);
               }
            }
            ids = tmp;
         }
      }

      private void sort() {
         sortSelf(this.comparator);
      }

      public void sortSelf(final Comparator<T> comparator) {
         if (comparator == null)
            return;
         ArrayList<String> mutableIds = new ArrayList<String>(ids);
         Collections.sort(mutableIds, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
               T tLhs = get(lhs);
               T tRhs = get(rhs);
               return comparator.compare(tLhs, tRhs);
            }
         });
         ids.clear();
         ids.addAll(mutableIds);
      }

      /**
       * Trim list to the given size.
       * @param size
       */
      public void trim(int size) {
         CopyOnWriteArrayList<String> trimmed = new CopyOnWriteArrayList<String>();
         trimmed.addAll(ids.subList(0, Math.min(ids.size(), size)));
         ids = trimmed;
         subscribers.updateAll(Subscription.TRIM);
      }

      /**
       * Same as {@link #trim(int)} just happens at the end.
       * @param size
       * @return removed items count
       */
      public int trimAtEnd(int size) {
         int removedCount = 0;
         CopyOnWriteArrayList<String> trimmed = new CopyOnWriteArrayList<String>();
         trimmed.addAll(ids.subList(Math.max(0, ids.size() - size), ids.size()));
         removedCount = ids.size() - trimmed.size();
         ids = trimmed;
         subscribers.updateAll(Subscription.TRIM_AT_END);
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
         CopyOnWriteArrayList<String> newIds = new CopyOnWriteArrayList<String>();
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
       * and then {@link #commit()} or {@link #endTransaction()} once you're done
       * @return
       */
      public List transaction() {
         List list = new List(this);
         transactions.add(new WeakEqualReference<List>(list));
         return list;
      }

      /**
       * Validate transaction changes.
       */
      public void commit() {
         commit(new Subscription.Action(Subscription.COMMIT));
      }

      public void commit(Subscription.Action action) {
         if (transaction != null) {
            transaction.ids = ids;
            transaction.meta = meta;
            transaction.subscribers.updateAll(action);
         }
         endTransaction();
      }

      /**
       * Ends the transaction, removes the temporary list from the tracked lists
       */
      public void endTransaction() {
         transactions.remove(new WeakEqualReference<List>(this));
      }

      /**
       * Last item's id.
       * @return
       */
      public String lastId() {
         try {
           return ids.get(ids.size() - 1);
         } catch (Exception e) {
           return null;
         }
      }

      /**
       * If list contains null items, clears the list.
       * @return true if list is consistent (has no null items)
       */
      public boolean ensureConsistence() {
         for (String id : ids) {
            if (get(id) == null) {
               ids.clear();
               subscribers.updateAll(Subscription.CLEAR);
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
         return (position >=0 && position < ids.size()) ? ids.get(position) : null;
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
            return get(id);
         else
            return null;
      }

      /**
       * @param action
       */
      public void publish(Subscription.Action action) {
         subscribers.updateAll(action);
      }

      public List filter(Query query) {
         List list = this.transaction();
         list.filterSelf(query);
         return list;
      }

      public List filterSelf(Query query) {
         if (query == null)
            return this;
         CopyOnWriteArrayList<String> newIds = new CopyOnWriteArrayList<String>();
         for (T t : this) {
            if (query.eval(t)) {
               newIds.add(id(t));
            }
         }
         this.ids = newIds;
         subscribers.updateAll(Subscription.RELOAD_QUERY);
         return this;
      }
   }

   public interface Query<T> {
      boolean eval(T t);
   }

   /**
    * Facility to manage Subscribers
    */
   public static class Subscribers {
      private boolean muted;
      private ArrayList<Subscription> subscriptions = new ArrayList<Subscription>();

      public void addSubscriber(Subscription subscription) {
         if (!subscriptions.contains(subscription))
            subscriptions.add(subscription);
      }

      public void addAllSubscribers(Subscribers from) {
         for (Subscription s : from.subscriptions) {
            addSubscriber(s);
         }
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
      public final static String ADD = "add";
      public final static String ADD_ALL = "addAll";
      public final static String ADD_UPFRONT = "addUpFront";
      public final static String CLEAR = "clear";
      public final static String COMMIT = "commit";
      public final static String PUSH = "push";
      public final static String REMOVE = "remove";
      public final static String REMOVE_ALL = "removeAll";
      public final static String RETAIN_ALL = "retainAll";
      public final static String TRIM = "trim";
      public final static String TRIM_AT_END = "trimAtEnd";
      public final static String LOADED = "loaded";
      public final static String WILL_CHANGE = "willChange";
      public final static String RELOAD_QUERY = "reloadQuery";
      public final static String DELETE = "delete";

      public static class Action {
         public String name;
         public HashMap<String, Object> params;

         public Action(String name) {
            this.name = name;
            params = new HashMap<String, Object>();
         }

         public Action param(String key, Object value) {
            params.put(key, value);
            return this;
         }

         public Object param(String key) {
            return (params == null) ? null : params.get(key);
         }
      }
      public void onUpdate(Action action);
   }

   public interface TransportLayer {
      public boolean saveSync(Storage.List list, int limit);
      public boolean loadSync(Storage.List storageList);
   }

   protected TransportLayer transportLayer() {
      if (DEFAULT_TRANSPORT_LAYER == null) {
         DEFAULT_TRANSPORT_LAYER = new KryoTransportLayer(context);
      }
      return DEFAULT_TRANSPORT_LAYER;
   }

   public static void setDefaultTransportLayer(TransportLayer transportLayer) {
      DEFAULT_TRANSPORT_LAYER = transportLayer;
   }
}
