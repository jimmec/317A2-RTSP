package ubc.cs317.rtsp.client.model;

import java.util.Date;

/**
 * Tracks all playback statistics related to a single RTSP session.
 * 
 * @author jimmy
 *
 */
public class SessionStat {
   public String id;
   public Date startTime;
   public Date endTime;
   public long playbackLength;
   public long framesPlayed;
   public long framesLost;
   public long framesOutOfOrder;
}
