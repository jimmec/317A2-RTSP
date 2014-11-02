package ubc.cs317.rtsp.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Helper class that handles API calls that can potentially timeout.
 * Eg. when creating socket connections.
 * 
 * Modeled after:
 * http://stackoverflow.com/questions/1247390/java-native-process-timeout/1249984#1249984
 * 
 * @author jimmy
 *
 */
public class TimeoutCaller {
   private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

   /**
    * Static method that can wrap a function call with a timeout.
    * 
    * The function as specified by c will be executed by a different thread,
    * and it will either return the result or throw a TimeoutException after
    * timeout milliseconds.
    * 
    * @param c
    *           the function to call
    * @param timeout
    *           timeout in milliseconds
    * @return the result of Callable.call()
    * @throws InterruptedException
    * @throws ExecutionException
    * @throws TimeoutException
    */
   public static <T> T timedCall(Callable<T> c, long timeout) throws InterruptedException, ExecutionException,
         TimeoutException {
      FutureTask<T> task = new FutureTask<T>(c);
      THREAD_POOL.execute(task);
      return task.get(timeout, TimeUnit.MILLISECONDS);
   }
}