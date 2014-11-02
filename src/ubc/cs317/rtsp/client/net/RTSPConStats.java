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
      sessions.add(new SessionStat(id, vidName));
   }

   public void endSession() {
      latest().finalize();
   }

   public void playStart() {
      latest().startPlay();
   }

   public void playPause() {
      latest().pausePlay();
   }

   public void setRequestCount(int count) {
      latest().cseq = count;
   }

   /**
    * Call this each we process a new Frame. It will attempt to record the relevant stats.
    * 
    * @param f
    *           the newest processed Frame.
    */
   public void newFrame(Frame f) {
      latest().framesPlayed++;
   }

   private SessionStat latest() {
      if (sessions.size() < 1) {
         throw new IllegalStateException();
      }
      return sessions.get(sessions.size());
   }

   /**
    * Print out a summary of all recorded stats so far.
    */
   public void report() {

   }
}
