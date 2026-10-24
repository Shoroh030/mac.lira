package com.lira.vmest;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.webrtc.MediaConstraints;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class WebRtcNativeSmokeTest {
    private static final String TAG = "WebRtcNativeSmoke";
    private static final long CALLBACK_TIMEOUT_SECONDS = 30;
    private static final long STABILITY_WINDOW_MILLIS = 150_000;

    @Test
    public void factoryTransceiversAndStunOfferRemainStable() throws Exception {
        PeerConnectionFactory factory = null;
        PeerConnection peer = null;
        try {
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(targetContext())
                            .createInitializationOptions());
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory();
            assertNotNull("PeerConnectionFactory was not created", factory);
            Log.i(TAG, "factory-created");

            PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(
                    Collections.singletonList(PeerConnection.IceServer
                            .builder("stun:stun.l.google.com:19302").createIceServer()));
            configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
            CountDownLatch gatheringComplete = new CountDownLatch(1);
            peer = factory.createPeerConnection(configuration, observer(gatheringComplete));
            assertNotNull("PeerConnection was not created", peer);
            peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV));
            peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV));
            Log.i(TAG, "peer-and-transceivers-created");

            CountDownLatch offerSet = new CountDownLatch(1);
            String[] error = {null};
            String[] offerSdp = {null};
            PeerConnection activePeer = peer;
            activePeer.createOffer(new SdpObserver() {
                @Override public void onCreateSuccess(SessionDescription offer) {
                    offerSdp[0] = offer.description;
                    peerSetLocal(offer);
                }

                private void peerSetLocal(SessionDescription offer) {
                    activePeer.setLocalDescription(new SdpObserver() {
                        @Override public void onCreateSuccess(SessionDescription description) { }
                        @Override public void onSetSuccess() {
                            Log.i(TAG, "local-offer-set");
                            offerSet.countDown();
                        }
                        @Override public void onCreateFailure(String message) {
                            error[0] = message;
                            offerSet.countDown();
                        }
                        @Override public void onSetFailure(String message) {
                            error[0] = message;
                            offerSet.countDown();
                        }
                    }, offer);
                }

                @Override public void onSetSuccess() { }
                @Override public void onCreateFailure(String message) {
                    error[0] = message;
                    offerSet.countDown();
                }
                @Override public void onSetFailure(String message) {
                    error[0] = message;
                    offerSet.countDown();
                }
            }, new MediaConstraints());

            assertTrue("Timed out creating and setting local SDP",
                    offerSet.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull("SDP offer failed: " + error[0], error[0]);
            assertTrue("Offer omitted the audio transceiver", offerSdp[0].contains("m=audio "));
            assertTrue("Offer omitted the video transceiver", offerSdp[0].contains("m=video "));
            gatheringComplete.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Log.i(TAG, "entering-native-stability-window");
            Thread.sleep(STABILITY_WINDOW_MILLIS);
            Log.i(TAG, "native-stability-window-complete");
        } finally {
            if (peer != null) {
                peer.close();
                peer.dispose();
            }
            if (factory != null) factory.dispose();
        }
    }

    private Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    }

    private PeerConnection.Observer observer(CountDownLatch gatheringComplete) {
        return new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) { }
            @Override public void onIceConnectionReceivingChange(boolean receiving) { }
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringComplete.countDown();
            }
            @Override public void onIceCandidate(org.webrtc.IceCandidate candidate) { }
            @Override public void onIceCandidatesRemoved(org.webrtc.IceCandidate[] candidates) { }
            @Override public void onAddStream(org.webrtc.MediaStream stream) { }
            @Override public void onRemoveStream(org.webrtc.MediaStream stream) { }
            @Override public void onDataChannel(org.webrtc.DataChannel channel) { }
            @Override public void onRenegotiationNeeded() { }
            @Override public void onAddTrack(org.webrtc.RtpReceiver receiver, org.webrtc.MediaStream[] streams) { }
            @Override public void onTrack(RtpTransceiver transceiver) { }
        };
    }
}
