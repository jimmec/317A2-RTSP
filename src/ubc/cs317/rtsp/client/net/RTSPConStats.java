package ubc.cs317.rtsp.client.net;

import java.util.ArrayList;
import java.util.List;

import ubc.cs317.rtsp.client.model.SessionStat;

/**
 * This class tracks playback statistics of an entire RTSP connection,
 * organized by each individual session. <br/>
 * Each time a newSession is created, all stats tracking is done against the latest session.
 * 
 * @author jimmy
 *
 */
public class RTSPConStats {
   List<SessionStat> sessions;

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

   public void newRequest() {
      latest().cseq++;
   }

   public void framePlayed() {
      latest().framesPlayed++;
   }

   public void frameLost() {
      latest().framesLost++;
   }

   public void frameOutOrder() {
      latest().framesOutOfOrder++;
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
