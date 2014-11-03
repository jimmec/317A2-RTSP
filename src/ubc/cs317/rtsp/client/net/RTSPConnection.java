/*
 * University of British Columbia
 * Department of Computer Science
 * CPSC317 - Internet Programming
 * Assignment 2
 * 
 * Author: Jonatan Schroeder
 * January 2013
 * 
 * This code may not be used without written consent of the authors, except for 
 * current and future projects and assignments of the CPSC317 course at UBC.
 */

package ubc.cs317.rtsp.client.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import ubc.cs317.rtsp.client.exception.RTSPException;
import ubc.cs317.rtsp.client.model.Frame;
import ubc.cs317.rtsp.client.model.Session;
import ubc.cs317.rtsp.util.TimeoutCaller;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

   private static final String CRLF = "\r\n";
   private static final int CON_ATTEMPT_TIMEOUT = 30000;
   private static final int BUFFER_LENGTH = 15000;
   private static final long MINIMUM_DELAY_READ_PACKETS_MS = 20;

   private Session session;
   private RTSPConStats stat;
   private State sessionState;
   private int cseq;
   private String sessionId;
   private String sessionVid;
   private Timer rtpTimer;
   private Socket rtspSocket;
   private BufferedReader rtspReader;
   private BufferedWriter rtspWriter;
   private DatagramSocket dataSocket;

   /**
    * Establishes a new connection with an RTSP server. No message is sent at
    * this point, and no stream is set up.
    * 
    * @param session
    *           The Session object to be used for connectivity with the UI.
    * @param server
    *           The hostname or IP address of the server.
    * @param port
    *           The TCP port number where the server is listening to.
    * @throws RTSPException
    *            If the connection couldn't be accepted, such as if the host
    *            name or port number are invalid or there is no connectivity.
    */
   public RTSPConnection(Session session, final String server, final int port) throws RTSPException {

      this.session = session;
      stat = new RTSPConStats();

      // Try to establish control connection to server within a timeout
      try {
         rtspSocket = TimeoutCaller.timedCall(new Callable<Socket>() {
            @Override
            public Socket call() throws Exception {
               return new Socket(server, port);
            }
         }, CON_ATTEMPT_TIMEOUT);
      } catch (InterruptedException e) {
         throw new RTSPException("Connection attempt failed: " + e.getMessage(), e);
      } catch (ExecutionException e1) {
         if (e1.getCause() instanceof UnknownHostException) {
            throw new RTSPException(String.format("Invalid host:port: '%s:%d'!", server, port), e1);
         } else {
            throw new RTSPException(String.format("Cannot connect to server at '%s:%d'!", server, port), e1);
         }
      } catch (TimeoutException e2) {
         throw new RTSPException(String.format("Connection attemp to '%s:%d' timed out after %d miliseconds!",
               server,
               port,
               CON_ATTEMPT_TIMEOUT), e2);
      }
      setState(State.INIT);
   }

   /**
    * Sends a SETUP request to the server. This method is responsible for
    * sending the SETUP request, receiving the response and retrieving the
    * session identification to be used in future messages. It is also
    * responsible for establishing an RTP datagram socket to be used for data
    * transmission by the server. The datagram socket should be created with a
    * random UDP port number, and the port number used in that connection has
    * to be sent to the RTSP server for setup. This datagram socket should also
    * be defined to timeout after 1 second if no packet is received.
    * 
    * @param videoName
    *           The name of the video to be setup.
    * @throws RTSPException
    *            If there was an error sending or receiving the RTSP data, or
    *            if the RTP socket could not be created, or if the server did
    *            not return a successful response.
    */
   public synchronized void setup(String videoName) throws RTSPException {
      if (sessionState != State.INIT) {
         return;
      }
      // Start a UDP data socket.
      try {
         dataSocket = new DatagramSocket();
         dataSocket.setSoTimeout(1000);
      } catch (SocketException e) {
         throw new RTSPException(String.format("Could not create a new data connection!"), e);
      }

      try {
         sendCommand("SETUP " + videoName, null, String.valueOf(dataSocket.getLocalPort()));
      } catch (IOException e) {
         throw new RTSPException("Failed to send SETUP request: " + e.getMessage(), e);
      }

      try {
         RTSPResponse resp = RTSPResponse.readRTSPResponse(rtspReader);
         checkRespSuccessful(resp);
         sessionId = resp.getHeaderValue("SESSION");
         sessionVid = videoName;
         setState(State.READY);
         stat.newSession(sessionId, videoName);
      } catch (IOException e) {
         throw new RTSPException("Failed to read SETUP request response: " + e.getMessage(), e);
      }
   }

   /**
    * Sends a PLAY request to the server. This method is responsible for
    * sending the request, receiving the response and, in case of a successful
    * response, starting the RTP timer responsible for receiving RTP packets
    * with frames.
    * 
    * @throws RTSPException
    *            If there was an error sending or receiving the RTSP data, or
    *            if the server did not return a successful response.
    */
   public synchronized void play() throws RTSPException {
      if (sessionState != State.READY) {
         return;
      }
      try {
         sendCommand("PLAY " + sessionVid, sessionId, null);
      } catch (IOException e) {
         throw new RTSPException(String.format("Cannot send PLAY request '%s': %s", sessionVid, e.getMessage()), e);
      }
      try {
         RTSPResponse resp = RTSPResponse.readRTSPResponse(rtspReader);
         checkRespSuccessful(resp);
         startRTPTimer();
         stat.playStart();
         setState(State.PLAYING);
      } catch (IOException e) {
         throw new RTSPException("Failed to read PLAY request response: " + e.getMessage(), e);
      }
   }

   /**
    * Starts a timer that reads RTP packets repeatedly. The timer will wait at
    * least MINIMUM_DELAY_READ_PACKETS_MS after receiving a packet to read the
    * next one.
    */
   private void startRTPTimer() {

      rtpTimer = new Timer();
      rtpTimer.schedule(new TimerTask() {
         @Override
         public void run() {
            receiveRTPPacket();
         }
      }, 0, MINIMUM_DELAY_READ_PACKETS_MS);
   }

   /**
    * Receives a single RTP packet and processes the corresponding frame. The
    * data received from the datagram socket is assumed to be no larger than
    * BUFFER_LENGTH bytes. This data is then parsed into a Frame object (using
    * the parseRTPPacket method) and the method session.processReceivedFrame is
    * called with the resulting packet. In case of timeout no exception should
    * be thrown and no frame should be processed.
    */
   private void receiveRTPPacket() {
      try {
         byte[] buf = new byte[BUFFER_LENGTH];
         DatagramPacket packet = new DatagramPacket(buf, BUFFER_LENGTH);
         dataSocket.receive(packet);
         Frame frame = parseRTPPacket(packet.getData(), packet.getLength());
         session.processReceivedFrame(frame);
         stat.newFrame(frame);
      } catch (SocketTimeoutException e2) {
         // Do nothing on timeouts
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   /**
    * Sends a PAUSE request to the server. This method is responsible for
    * sending the request, receiving the response and, in case of a successful
    * response, cancelling the RTP timer responsible for receiving RTP packets
    * with frames.
    * 
    * @throws RTSPException
    *            If there was an error sending or receiving the RTSP data, or
    *            if the server did not return a successful response.
    */
   public synchronized void pause() throws RTSPException {
      if (sessionState != State.PLAYING) {
         return;
      }
      try {
         sendCommand("PAUSE " + sessionVid, sessionId, null);
         RTSPResponse resp = RTSPResponse.readRTSPResponse(rtspReader);
         checkRespSuccessful(resp);
         rtpTimer.cancel();
         stat.playPause();
         setState(State.READY);
      } catch (IOException e) {
         throw new RTSPException("Cannot send PAUSE request: " + e.getMessage(), e);
      }
   }

   /**
    * Sends a TEARDOWN request to the server. This method is responsible for
    * sending the request, receiving the response and, in case of a successful
    * response, closing the RTP socket. This method does not close the RTSP
    * connection, and a further SETUP in the same connection should be
    * accepted. Also this method can be called both for a paused and for a
    * playing stream, so the timer responsible for receiving RTP packets will
    * also be cancelled.
    * 
    * @throws RTSPException
    *            If there was an error sending or receiving the RTSP data, or
    *            if the server did not return a successful response.
    */
   public synchronized void teardown() throws RTSPException {
      if (sessionState == State.INIT) {
         return;
      }
      try {
         sendCommand("TEARDOWN " + sessionVid, sessionId, null);
         RTSPResponse resp = RTSPResponse.readRTSPResponse(rtspReader);
         checkRespSuccessful(resp);
         stat.setRequestCount(cseq);
         rtpTimer.cancel();
         stat.endSession();
         dataSocket.close();
         dataSocket = null;
         setState(State.INIT);
      } catch (IOException e) {
         throw new RTSPException("Unable to complete TEARDOWN request: " + e.getMessage(), e);
      }
   }

   /**
    * Closes the connection with the RTSP server. This method should also close
    * any open resource associated to this connection, such as the RTP
    * connection, if it is still open.
    */
   public synchronized void closeConnection() {
      if (sessionState != State.INIT) {
         try {
            teardown();
         } catch (RTSPException e) {

         }
         rtpTimer.cancel();
         if (dataSocket != null) {
            dataSocket.close();
            dataSocket = null;
         }
      }
      try {
         rtspReader.close();
         rtspWriter.close();
         rtspSocket.close();
      } catch (IOException e) {

      } finally {
         rtspReader = null;
         rtspWriter = null;
         rtspSocket = null;
      }
   }

   /**
    * Helper to keep RTSP Session state. Encapsulates all state handling logic.
    * 
    * @param desiredState
    * @throws RTSPException
    */
   private void setState(State desiredState) throws RTSPException {
      switch (desiredState) {
      case INIT: {
         sessionState = State.INIT;
         sessionVid = null;
         cseq = 0;
         try {
            rtspReader = new BufferedReader(new InputStreamReader(rtspSocket.getInputStream()));
            rtspWriter = new BufferedWriter(new OutputStreamWriter(rtspSocket.getOutputStream()));
         } catch (IOException e) {
            throw new RTSPException("Cannot get input/output from/to server!", e);
         }
         break;
      }
      case READY: {
         sessionState = State.READY;
         break;
      }
      case PLAYING: {
         sessionState = State.PLAYING;
         break;
      }
      }
   }

   /**
    * Parses an RTP packet into a Frame object.
    * 
    * @param packet
    *           the byte representation of a frame, corresponding to the RTP
    *           packet.
    * @return A Frame object.
    */
   private static Frame parseRTPPacket(byte[] packet, int length) {
      boolean marker = false;
      // first 7bits
      byte payloadType = (byte) (packet[0] >>> 1);
      // next 16bits Big endian
      short sequenceNumber = (byte) (((packet[0] << 7 | packet[1] >>> 1) << 8) | (packet[1] << 7 | packet[2] >>> 1));
      // next 32bits Big endian
      int timestamp = ((packet[2] << 7 | packet[3] >>> 1) << 24) | ((packet[3] << 7 | packet[4] >>> 1) << 16)
            | ((packet[4] << 7 | packet[5] >>> 1) << 8) | (packet[5] << 7 | packet[6] >>> 1);

      return new Frame(payloadType, marker, sequenceNumber, timestamp, packet, 12, length - 12);
   }

   /**
    * Helper used to send RTSP requests to the server. Handles the CSeq header, and msg formatting.
    * 
    * @param command
    *           the RTSP command and resource URL
    * @param sessionID
    *           the content of the Session: header
    * @param transport
    *           the port passed in the Transport: header
    * @throws IOException
    */
   private void sendCommand(String command, String sessionId, String port) throws IOException {
      if (rtspWriter == null) {
         return;
      }
      StringBuilder req = new StringBuilder(command + " RTSP/1.0").append(CRLF).append("CSeq: " + cseq).append(CRLF);
      if (port != null) {
         req.append("Transport: RTP/UDP; client_port= " + port).append(CRLF);
      }
      if (sessionId != null) {
         req.append("Session: " + sessionId).append(CRLF);
      }
      req.append(CRLF);

      rtspWriter.write(req.toString());
      rtspWriter.flush();
      // Only increment the sequence # if request was sent successfully.
      cseq++;
   }

   /**
    * Helper to check if an RTSPResponse was successful,
    * if not throws RTSPException with error code and message.
    * 
    * @param resp
    * @throws RTSPException
    */
   private void checkRespSuccessful(RTSPResponse resp) throws RTSPException {
      if (resp.getResponseCode() != 200) {
         throw new RTSPException(resp.getResponseCode() + resp.getResponseMessage());
      }
   }

   private enum State {
      INIT, READY, PLAYING;
   }
}
