package ubc.cs317.rtsp.client.net;

import java.util.ArrayList;
import java.util.List;

import ubc.cs317.rtsp.client.model.Frame;
import ubc.cs317.rtsp.client.model.SessionStat;
import ubc.cs317.rtsp.util.SimpleCircularBuffer;

/**
 * This class helps track playback statistics of an entire RTSP connection,
 * organized by each individual session. <br/>
 * Each time a newSession is created, all stats tracking is done against the latest session.
 * 
 * @author jimmy
 *
 */
public class RTSPConStats {
   private List<SessionStat> sessions;
   private SimpleCircularBuffer<Short> recentFrameSeqs;
   private SimpleCircularBuffer<Long> recentFrameTimestamps;
   private SessionStat currSesh;

   public RTSPConStats() {
      sessions = new ArrayList<SessionStat>();
   }

   /**
    * Start tracking stats for a new RTSP session.
    * MUST be called before using any other methods to track this session.
    * Should be called each time setup() is called in RTSPConnection.
    * 
    * @param id
    *           sessionId as returned by the RTSP server for the current session to be tracked
    * @param vidName
    *           name of the video file being requested for this session.
    */
   public void newSession(String id, String vidName) {
      SessionStat sesh = new SessionStat(id, vidName);
      sessions.add(sesh);
      currSesh = sesh;
      recentFrameSeqs = new SimpleCircularBuffer<Short>(Short.class, 10);
      recentFrameTimestamps = new SimpleCircularBuffer<Long>(Long.class, 10);
   }

   /**
    * Call this when teardown is called.
    */
   public void endSession() {
      currSesh.finalize();
      currSesh = null;
      report();
   }

   public void playStart() {
      currSesh.startPlay();
   }

   public void playPause() {
      currSesh.pausePlay();
   }

   public void setRequestCount(int count) {
      currSesh.cseq = count;
   }

   /**
    * Call this each we process a new Frame. It will attempt to record the relevant stats.
    * 
    * @param f
    *           the newest processed Frame.
    */
   public void newFrame(Frame f) {
      recentFrameSeqs.add(f.getSequenceNumber());
      recentFrameTimestamps.add(Long.valueOf(Integer.toBinaryString(f.getTimestamp()), 2));
      Short[] sView = recentFrameSeqs.getView();
      Long[] tView = recentFrameTimestamps.getView();
      currSesh.framesPlayed++;
      checkFrameOrder(tView);
   }

   /**
    * Helper that will try to determine whether or not the newest frame is out of order.
    * 
    * @param tView
    *           a view of the frame timestamps as retrieved from the SimpleCircularBuffer
    */
   private void checkFrameOrder(Long[] tView) {
      int n = tView.length;
      if (n >= 2 && tView[n - 2] != 0xFFFFFFFF
            && (tView[n - 2] < tView[n - 1] - 9000000 || tView[n - 2] > tView[n - 1])) {
         currSesh.framesOutOfOrder++;
      }
   }

   /**
    * Print out a summary of all recorded stats so far.
    */
   public void report() {
      System.out.println("Stats: ");
      for (SessionStat s : sessions) {
         System.out.println(String.format("==============Start time: %s==============", s.startTime.toString()));
         System.out.println(String.format("ID: %s, %s", s.id, s.videoName));
         System.out.println(String.format("Total requests: %d", s.cseq));
         System.out.println(String.format("Total frames: %d", s.framesPlayed));
         System.out.println(String.format("Frames out of order: %d", s.framesOutOfOrder));
         System.out.println(String.format("Frames lost: %d", s.framesLost));
         System.out.println(String.format("Playback length (ms): %d", s.playbackLength));
         System.out.println(String.format("Avg framerate (f/s): %f", (double) s.framesPlayed
               / (s.playbackLength / 1000)));
         System.out.println(String.format("Session length (ms): %d", s.endTime.getTime() - s.startTime.getTime()));
         System.out.println(String.format("==============End time: %s==============", s.endTime.toString()));
         System.out.println();
      }
   }
}
