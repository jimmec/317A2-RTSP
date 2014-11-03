package ubc.cs317.rtsp.util;

import java.lang.reflect.Array;

/**
 * A simple array backed circular buffer.
 * Supports inserts, and provides reordered views.
 * Does not support deletes.
 * 
 * @author jimmy
 *
 * @param <T>
 */
public class SimpleCircularBuffer<T> {
   T[] buf;
   Class<T> clazz;
   int tail; // points to the next insert pos
   int len; // current size of buffer

   @SuppressWarnings("unchecked")
   public SimpleCircularBuffer(Class<T> clazz, int size) {
      buf = (T[]) Array.newInstance(clazz, size);
      this.clazz = clazz;
   }

   public void add(T elem) {
      buf[tail] = elem;
      tail = (tail + 1) % buf.length;
      if (len <= buf.length) {
         len++;
      }
   }

   /**
    * Returns a reshuffled view of this circular buffer as a regular array,
    * where the oldest inserted elem is at position 0,
    * and the latest elem is at the last index of the array. <br/>
    * 
    * The buffer is locked while we retrieve a view.
    * 
    * @return
    */
   @SuppressWarnings("unchecked")
   public synchronized T[] getView() {
      T[] view = (T[]) Array.newInstance(this.clazz, len);
      if (len < buf.length) {
         System.arraycopy(buf, 0, view, 0, len);
      } else {
         System.arraycopy(buf, tail, view, 0, buf.length - tail);
         if (tail > 0) {
            System.arraycopy(buf, 0, view, buf.length - tail, tail);
         }
      }
      return view;
   }

}