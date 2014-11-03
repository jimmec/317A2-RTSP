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
   private SimpleCircularBuffer<Integer> recentFrameTimestamps;
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
      recentFrameTimestamps = new SimpleCircularBuffer<Integer>(Integer.class, 10);
   }

   /**
    * Call this when teardown is called.
    */
   public void endSession() {
      currSesh.finalize();
      currSesh = null;
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
      recentFrameTimestamps.add(f.getTimestamp());
      Short[] sView = recentFrameSeqs.getView();
      Integer[] tView = recentFrameTimestamps.getView();
      currSesh.framesPlayed++;
      int n = sView.length;
      // if (n >= 2 && sView[n - 2] != (1 << 16) - 1 && sView[n - 2] + 1 != sView[n - 1]) {
      // currSesh.framesOutOfOrder++;
      // }
   }

   /**
    * Print out a summary of all recorded stats so far.
    */
   public void report() {

   }
}
