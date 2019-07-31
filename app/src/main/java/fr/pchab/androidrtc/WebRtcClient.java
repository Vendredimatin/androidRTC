/*
 * Copyright 2014 Pierre Chabardes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.pchab.androidrtc;

import android.util.Log;


import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONString;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


import android.content.Context;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

public class WebRtcClient {
    private double latitude=0.0,longitude=0.0;
    public void setLocation(double lat,double lon){
        latitude=lat;longitude=lon;
    }

    public static final String SCREEN_TRACK_ID = "ARDAMSv0",FRONT_TRACK_ID="ARDAMSv1",BACK_TRACK_ID="ARDAMSv2";
    private final static String TAG = "WebRtcClient";
    private final static int MAX_PEER = 2;
    private boolean[] endPoints = new boolean[MAX_PEER];
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionClient.PeerConnectionParameters mPeerConnParams;
    private MediaConstraints mPeerConnConstraints = new MediaConstraints();
    private MediaStream mLocalMediaStream;
    private VideoSource mScreenSource,mFrontSource,mBackSource;
    private VideoTrack mSreenTrack,mFrontTrack,mBackTrack;
    private AudioSource mAudioSource;
    private AudioTrack mAudioTrack;
    private RtcListener mListener;
    private Socket mSocket;
    VideoCapturer screenCapturer,frontCapturer,backCapturer;
    MessageHandler messageHandler = new MessageHandler();
    Context mContext;


    public void sendGPS() {
        for (Peer peer : peers.values()) {
            peer.sendDataChannelMessage(String.valueOf(latitude)+","+String.valueOf(longitude));
        }
    }
    /**
     * Implement this interface to be notified of events.
     */
    public interface RtcListener {
        void onReady(String remoteId);

        void onCall(String applicant);

        void onHandup();

        void onStatusChanged(String newStatus);
    }

    public interface Command {
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    public class CreateOfferCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateOfferCommand");
            Peer peer = peers.get(peerId);
            peer.pc.createOffer(peer, mPeerConnConstraints);
//            sendMessage("r0Z049NKJF2ZCIhRAAAZ","offer",new JSONObject());
        }
    }

    public class CreateAnswerCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "CreateAnswerCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.optString("type")),
                    payload.optString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
            peer.pc.createAnswer(peer, mPeerConnConstraints);
        }
    }

    public class SetRemoteSDPCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "SetRemoteSDPCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.optString("type")),
                    payload.optString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
        }
    }

    public class AddIceCandidateCommand implements Command {
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG, "AddIceCandidateCommand");
            PeerConnection pc = peers.get(peerId).pc;
            if (pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.optString("id"),
                        payload.optInt("label"),
                        payload.optString("candidate")
                );
                pc.addIceCandidate(candidate);
            }
        }
    }

    /**
     * Send a message through the signaling server
     *
     * @param to      id of recipient
     * @param type    type of message
     * @param payload payload of message
     * @throws JSONException
     */
    public void sendMessage(String to, String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        mSocket.emit("message", message);
        Log.d(TAG, "socket send " + type + " to " + to + " payload:" + payload);
    }

    public class MessageHandler {
        private HashMap<String, Command> commandMap;

        public MessageHandler() {
            this.commandMap = new HashMap<>();
            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new CreateAnswerCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
        }

        public Emitter.Listener onMessage = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject data = (JSONObject) args[0];
//                    String info = (String) args[0];
//                    JSONObject data = new JSONObject(info);
                    String from = data.optString("from");
                    String type = data.optString("type");
                    Log.d(TAG, "socket received " + type + " from " + from);
                    JSONObject payload = null;
                    if (!type.equals("init")) {
                        payload = data.optJSONObject("payload");
                    }
                    // if peer is unknown, try to add him
                    if (!peers.containsKey(from)) {
                        // if MAX_PEER is reach, ignore the call
                        Log.i(TAG, "don't contain peer:" + from);
                        int endPoint = findEndPoint();
                        if (endPoint != MAX_PEER) {
                            Peer peer = addPeer(from, endPoint);
//                            peer.pc.addStream(mLocalMediaStream);
                            commandMap.get(type).execute(from, payload);
                        }
                    } else {
                        Command command = commandMap.get(type);
                        if(command!=null){
                            command.execute(from, payload);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        public Emitter.Listener onId = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String id = (String) args[0];
                mListener.onReady(id);
                mListener.onStatusChanged("READY");
                Log.d(TAG, "socket onId " + id);
            }
        };
    }

    public class Peer implements SdpObserver, PeerConnection.Observer, DataChannel.Observer {
        public PeerConnection pc;
        public String id;
        public int endPoint;
        public DataChannel dc;

        public Peer(String id, int endPoint) {
            Log.d(TAG, "new Peer: " + id + " " + endPoint);
            Log.i("dataChannel", "new Peer: " + id + " " + endPoint);
            Log.i("dataChannel", "new peerConnection: ");

            this.pc = factory.createPeerConnection(iceServers, mPeerConnConstraints, this);
            this.id = id;
            this.endPoint = endPoint;
            pc.addStream(mLocalMediaStream); //, new MediaConstraints()

            /*
            DataChannel.Init 可配参数说明：
            ordered：是否保证顺序传输；
            maxRetransmitTimeMs：重传允许的最长时间；
            maxRetransmits：重传允许的最大次数；
             */
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = true;
            dc = pc.createDataChannel("dataChannel", init);
            Log.i("dataChannel", "dataChannel create");
        }

        public void sendDataChannelMessage(String message) {
            byte[] msg = message.getBytes();
            DataChannel.Buffer buffer = new DataChannel.Buffer(
                    ByteBuffer.wrap(msg),
                    false);
            dc.send(buffer);
        }

        public void release() {
            pc.dispose();
            dc.close();
            dc.dispose();
        }

        //DataChannel.Observer----------------------------------------------------------------------

        @Override
        public void onBufferedAmountChange(long l) {

        }

        @Override
        public void onStateChange() {
            Log.i("dataChannel", "readyState:"+ dc.state());
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            ByteBuffer data = buffer.data;
            byte[] bytes = new byte[data.capacity()];
            data.get(bytes);
            String msg = new String(bytes);
            Log.i("dataChannel","dataChannel receive:" + msg);
            Log.i("dataChannel", "dataChannel receive:" + msg);
            Log.i("dataChannel", "peerSize:"+ peers.size());
            // peers中存着远端Web的ID
            for (Map.Entry<String,Peer> entry :peers.entrySet()) {
                Log.i("dataChannel", "peerID:"+entry.getKey());
                Peer peer = entry.getValue();
                if((latitude!=0||longitude!=0)&&msg.equals("GPS")){
                    JSONObject json = new JSONObject();
                    try {
                        json.put("type", "GPS");
                        json.put("latitude", latitude);
                        json.put("longitude", longitude);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    peer.sendDataChannelMessage(json.toString());
                    Log.i("dataChannel", latitude+","+longitude);

                }
                else if((mSreenTrack.enabled()&&msg.equals("screen"))||(mFrontTrack.enabled()&&msg.equals("front"))||(mBackTrack.enabled()&&msg.equals("back"))){
                    switchVideoTo(msg);
                    JSONObject json = new JSONObject();
                    try {
                        json.put("type", "SWITCH");
                        json.put("msg", "succeed to switch to "+msg);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    peer.sendDataChannelMessage(json.toString());
                }
                else{
                    JSONObject json = new JSONObject();
                    try {
                        json.put("type", "FAIL");
                        json.put("msg", "No permission");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    peer.sendDataChannelMessage(json.toString());
                }
            }
        }

        //--------------------------------------------------

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            // TODO: modify sdp to use mPeerConnParams prefered codecs
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", sdp.type.canonicalForm());
                payload.put("sdp", sdp.description);
                Log.d(TAG, "onCreateSuccess");
                sendMessage(id, sdp.type.canonicalForm(), payload);
                pc.setLocalDescription(Peer.this, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
        }

        @Override
        public void onSetFailure(String s) {
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }


        public void onIceConnectionReceivingChange(boolean var1) {

        }

        public void onIceCandidatesRemoved(IceCandidate[] var1) {

        }

        public void onAddTrack(RtpReceiver var1, MediaStream[] var2) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                removePeer(id);
                mListener.onStatusChanged("DISCONNECTED");
            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("label", candidate.sdpMLineIndex);
                payload.put("id", candidate.sdpMid);
                payload.put("candidate", candidate.sdp);
                sendMessage(id, "candidate", payload);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
//            mediaStream.videoTracks.get(0).addRenderer(new VideoRenderer(mRemoteRender));
//            mListener.onAddRemoteStream(mediaStream, endPoint + 1);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(id);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.i("dataChannel", "receive" + dataChannel.id());
            dc = dataChannel;
            dc.registerObserver(this);
            Log.i("dataChannel" ,"连接已建立");
        }

        @Override
        public void onRenegotiationNeeded() {

        }


    }

    private Peer addPeer(String id, int endPoint) {
        Peer peer = new Peer(id, endPoint);
        peers.put(id, peer);

        endPoints[endPoint] = true;
        return peer;
    }

    private void removePeer(String id) {
        Peer peer = peers.get(id);
        peer.pc.close();
        peers.remove(peer.id);
        endPoints[peer.endPoint] = false;
    }

    public WebRtcClient(Context context, RtcListener listener, VideoCapturer[] capturerList, PeerConnectionClient.PeerConnectionParameters params) {
        mContext = context;
        mListener = listener;
        mPeerConnParams = params;
        screenCapturer=capturerList[0];
        frontCapturer=capturerList[1];
        backCapturer=capturerList[2];
        PeerConnectionFactory.initializeAndroidGlobals(mContext, true, true,
                params.videoCodecHwAcceleration);
        factory = new PeerConnectionFactory();
        String host = "http://" + context.getString(R.string.host) + ":" + context.getString(R.string.port) + "/";
        try {
            mSocket = IO.socket(host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        mSocket.on("id", messageHandler.onId);
        mSocket.on("message", messageHandler.onMessage);
        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, "socket state connect");
            }
        });
        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, "socket state disconnect");
            }
        });
        mSocket.on(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.d(TAG, "socket state error");
            }
        });
        mSocket.connect();
        Log.d(TAG, "socket start connect");

        iceServers.add(new PeerConnection.IceServer("turn:numb.viagenie.ca","webrtc@live.com","muazkh"));
        //iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        mPeerConnConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mPeerConnConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mPeerConnConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }


    /**
     * Call this method in Activity.onDestroy()
     */
    public void destroy() {
        for (Peer peer : peers.values()) {
            peer.pc.dispose();
        }
        if (factory != null) {
            factory.dispose();
        }
        if (mScreenSource != null) {
            mScreenSource.dispose();
        }
        if (mFrontSource != null) {
            mFrontSource.dispose();
        }
        if (mBackSource != null) {
            mBackSource.dispose();
        }
//        mSocket.disconnect();
//        mSocket.close();
    }

    private int findEndPoint() {
        for (int i = 0; i < MAX_PEER; i++) if (!endPoints[i]) return i;
        return MAX_PEER;
    }

    /**
     * Start the mSocket.
     * <p>
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     *
     * @param name mSocket name
     */
    public void start(String name) {
        initScreenCapturStream();
        try {
            JSONObject message = new JSONObject();
            message.put("name", name);
            mSocket.emit("readyToStream", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void initScreenCapturStream() {
        mLocalMediaStream = factory.createLocalMediaStream("ARDAMS");
        MediaConstraints videoConstraints = new MediaConstraints();
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(mPeerConnParams.videoHeight)));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(mPeerConnParams.videoWidth)));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(mPeerConnParams.videoFps)));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(mPeerConnParams.videoFps)));

//        VideoCapturer capturer = createScreenCapturer();
        mScreenSource=factory.createVideoSource(screenCapturer);
        mFrontSource=factory.createVideoSource(frontCapturer);
        mBackSource=factory.createVideoSource(backCapturer);

        screenCapturer.startCapture(mPeerConnParams.videoWidth, mPeerConnParams.videoHeight, mPeerConnParams.videoFps);
        frontCapturer.startCapture(mPeerConnParams.videoWidth, mPeerConnParams.videoHeight, mPeerConnParams.videoFps);
        backCapturer.startCapture(mPeerConnParams.videoWidth, mPeerConnParams.videoHeight, mPeerConnParams.videoFps);

        mSreenTrack=factory.createVideoTrack(SCREEN_TRACK_ID,mScreenSource);
        mFrontTrack=factory.createVideoTrack(FRONT_TRACK_ID,mFrontSource);
        mBackTrack=factory.createVideoTrack(BACK_TRACK_ID,mBackSource);

        mSreenTrack.setEnabled(false);
        mFrontTrack.setEnabled(false);
        mBackTrack.setEnabled(false);

//        mLocalMediaStream.addTrack(mSreenTrack);
//        mLocalMediaStream.addTrack(mFrontTrack);
//        mLocalMediaStream.addTrack(mBackTrack);
//        mLocalMediaStream.addTrack(factory.createVideoTrack("ARDAMSv0", mVideoSource));
        mAudioSource = factory.createAudioSource(new MediaConstraints());
        mLocalMediaStream.addTrack(factory.createAudioTrack("ARDAMSa0", mAudioSource));
//        mLocalMediaStream.videoTracks.get(0).addRenderer(new VideoRenderer(mLocalRender));
//        mListener.onLocalStream(mLocalMediaStream);
        mListener.onStatusChanged("STREAMING");
    }
    public void switchVideoTo(String type){
        switch (type){
            case"screen":
                mLocalMediaStream.addTrack(mSreenTrack);
                mLocalMediaStream.removeTrack(mFrontTrack);
                mLocalMediaStream.removeTrack(mBackTrack);
                break;
            case"front":
                try{backCapturer.stopCapture();}catch (Exception e){e.printStackTrace();}
                frontCapturer.startCapture(mPeerConnParams.videoWidth, mPeerConnParams.videoHeight, mPeerConnParams.videoFps);
//                mFrontTrack.setEnabled(true);
                mLocalMediaStream.removeTrack(mSreenTrack);
                mLocalMediaStream.addTrack(mFrontTrack);
                mLocalMediaStream.removeTrack(mBackTrack);
                break;
            case"back":
                try{frontCapturer.stopCapture();}catch (Exception e){e.printStackTrace();}
                backCapturer.startCapture(mPeerConnParams.videoWidth, mPeerConnParams.videoHeight, mPeerConnParams.videoFps);
                mLocalMediaStream.removeTrack(mSreenTrack);
                mLocalMediaStream.removeTrack(mFrontTrack);
//                mBackTrack.setEnabled(true);
                mLocalMediaStream.addTrack(mBackTrack);
        }
    }
    public void setEnabled(boolean b,String type){
        switch (type){
            case "screen":
                mSreenTrack.setEnabled(b);break;
            case "front":
                mFrontTrack.setEnabled(b);break;
            case "back":
                mBackTrack.setEnabled(b);break;
            default:break;
        }
    }

}
