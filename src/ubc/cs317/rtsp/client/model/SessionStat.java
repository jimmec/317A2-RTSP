package ubc.cs317.rtsp.client.model;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Tracks all playback statistics related to a single RTSP session.
 * 
 * @author jimmy
 *
 */
public class SessionStat {
   private Timer timer;
   public String id;
   public String videoName;
   public Date startTime;
   public Date endTime;
   public long playbackLength;
   public long framesPlayed;
   public long framesLost;
   public long framesOutOfOrder;
   public int cseq;

   public SessionStat(String id, String vidName) {
      this.id = id;
      videoName = vidName;
      startTime = new Date();
   }

   /**
    * Start a timer to update the time video has actually been playing via 10ms intervals
    */
   public void startPlay() {
      timer = new Timer();
      timer.schedule(new TimerTask() {
         @Override
         public void run() {
            playbackLength += 20;
         }
      }, 20, 20);
   }

   /**
    * Pause/stops the playback timer.
    */
   public void pausePlay() {
      timer.cancel();
   }

   /**
    * Must be called when the current session is finished.
    * ie. when teardown() is called in RTSPConnection
    */
   public void finalize() {
      timer.cancel();
      this.endTime = new Date();
      timer = null;
   }

}
