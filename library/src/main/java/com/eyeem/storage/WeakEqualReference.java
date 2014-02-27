package com.eyeem.storage;

import java.lang.ref.WeakReference;

/**
 * http://chris.banes.me/2011/12/20/dangers-of-using-weakreferencesoftreference-in-hash-based-collections/
 * via Chris Banes
 */
public class WeakEqualReference<T> extends WeakReference<T> {

   public WeakEqualReference(T r) {
      super(r);
   }

   @SuppressWarnings("unchecked")
   @Override
   public boolean equals(Object other) {

      boolean returnValue = super.equals(other);

      // If we're not equal, then check equality using referenced objects
      if (!returnValue && (other instanceof WeakEqualReference<?>)) {
         T value = this.get();
         if (null != value) {
            T otherValue = ((WeakEqualReference<T>) other).get();

            // The delegate equals should handle otherValue == null
            returnValue = value.equals(otherValue);
         }
      }

      return returnValue;
   }

   @Override
   public int hashCode() {
      T value = this.get();
      return value != null ? value.hashCode() : super.hashCode();
   }
}
