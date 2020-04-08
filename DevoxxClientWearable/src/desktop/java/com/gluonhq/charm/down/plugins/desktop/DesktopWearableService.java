/**
 * Copyright (c) 2017, Gluon Software
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse
 *    or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.charm.down.plugins.desktop;

import com.devoxx.model.Conference;
import com.devoxx.model.Link;
import com.devoxx.model.Session;
import com.devoxx.model.Speaker;
import com.devoxx.model.Talk;
import com.devoxx.model.TalkSpeaker;
import com.devoxx.model.WearSpeaker;
import com.devoxx.service.DevoxxService;
import com.devoxx.service.Service;
import com.devoxx.util.WearableConstants;
import com.devoxx.views.helper.Util;
import com.gluonhq.charm.down.plugins.WearableService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;

/**
 * This service implementation allows testing the wearable service on desktop
 * 
 * Not all the funcionality is available
 */
public class DesktopWearableService implements WearableService {

    private static final Logger LOG = Logger.getLogger(DesktopWearableService.class.getName());

    private Service service;
    
    public DesktopWearableService() {
    }
    
    @Override
    public boolean isAmbientMode() {
        return false;
    }

    @Override
    public void setAmbientHandler(Runnable onEnterAmbientHandler, Runnable onUpdateambientHandler, Runnable onExitAmbientHandler) {
    }

    @Override
    public void keepScreenOn(boolean keep) {
    }

    @Override
    public void connect() {
    }

    @Override
    public <T> void sendMessage(String path, String data, Consumer<T> dataHandler) {
        // Comms between wearable and mobile always create a new service
        service = new DevoxxService();
        
        if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.CONFERENCES_PATH)) {
            // send conferences to the Wearable
            sendConferences((Consumer<List<Conference>>) dataHandler);
        } else if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.SESSIONS_PATH)) {
            // send sessions to the Wearable
            if (service.retrieveSessions().size() > 0) {
                sendSessions(Integer.parseInt(data), (Consumer<List<Session>>) dataHandler);
            } else {
                service.retrieveSessions().addListener((obs, ov, nv) -> {
                    if (nv != null && nv.size() > 0) {
                        sendSessions(Integer.parseInt(data), (Consumer<List<Session>>) dataHandler);
                    }
                });
            }
        } else if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.SESSION_SET_FAV_PATH)) {
            setFavSession(path.substring((WearableConstants.CHANNEL_ID + WearableConstants.SESSION_SET_FAV_PATH).length() + 1), 
                    Boolean.parseBoolean(data), (Consumer<boolean[]>) dataHandler);
        } else if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.SESSION_FAV_PATH)) {
            sendFavSession(data, (Consumer<boolean[]>) dataHandler);
        } else if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.SPEAKERS_PATH)) {
            sendSpeakers(data, (Consumer<List<WearSpeaker>>) dataHandler);
        } else if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.TWITTER_PATH)) {
            followOnTwitter(data);
        } else if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.SESSION_SUMMARY_PATH)) {
            sendSummary(data, (Consumer<String>) dataHandler);
        } else if (path.startsWith(WearableConstants.CHANNEL_ID + WearableConstants.OPEN_MOBILE_PATH)) {
            openMobile(data);
        } 
        
    }

    @Override
    public ReadOnlyBooleanProperty errorProperty() {
        return new SimpleBooleanProperty();
    }

    @Override
    public void disconnect() {
    }

    private void sendConferences(Consumer<List<Conference>> dataHandler) {
        if (service.readyProperty().get()) {
            // store the list schedules
            doGetConferences(dataHandler);
        } else {
            service.readyProperty().addListener((obs, ov, nv) -> {
                // store the list schedules
                doGetConferences(dataHandler);
            });
        }
        
    }
    private void doGetConferences(Consumer<List<Conference>> dataHandler) {
        List<Conference> conferencesList = new ArrayList<>();
        for (Conference conference : service.retrieveConferences()) {
            Conference c = new Conference();
            c.setCountry(conference.getCountry());
            c.setId(conference.getId());
            c.setSelected(service.getConference() != null ?
                            conference.getCountry().equals(service.getConference().getCountry()) : false);

            c.setTimezone(conference.getTimezone());
            c.setFromDate(conference.getFromDate());
            c.setToDate(conference.getToDate());

            conferencesList.add(c);
        }
        if (dataHandler != null) {
            Platform.runLater(() -> dataHandler.accept(conferencesList));
        }
    }

    private void sendSessions(int day, Consumer<List<Session>> dataHandler) {
        Conference conference = service.getConference();
        List<Session> sessionsList = new ArrayList<>();
        for (Session session : service.retrieveSessions()) {
            if (conference.getConferenceDayIndex(session.getStartDate()) == day) {
                Session s = new Session();
                s.setRoomName(session.getRoomName());
                s.setSlotId(session.getSlotId());
                s.setDay(session.getDay());
                Talk talk = new Talk();
                talk.setTitle(session.getTitle());
                talk.setSummary(session.getSummary());
                s.setTalk(talk);
                s.setFromTime(session.getFromTime());
                s.setToTime(session.getToTime());

                sessionsList.add(s);
            }
        }
        if (dataHandler != null) {
            Platform.runLater(() -> dataHandler.accept(sessionsList));
        }
    }

    private void setFavSession(String sessionSlotId, boolean fav, Consumer<boolean[]> dataHandler) {
        if (service.readyProperty().get()) {
                doSetFavSession(sessionSlotId, fav, dataHandler);
        } else {
            service.readyProperty().addListener((obs, ov, nv) -> {
                doSetFavSession(sessionSlotId, fav, dataHandler);
            });
        } 
    }
    
    private void doSetFavSession(String sessionSlotId, boolean fav, Consumer<boolean[]> dataHandler) {
        if (service.isAuthenticated()) {
            for (Session session : service.retrieveSessions()) {
                if (session.getSlotId().equals(sessionSlotId)) {
                    if (fav) {
                        service.retrieveFavoredSessions().add(session);
                    } else {
                        service.retrieveFavoredSessions().remove(session);
                    }
                    sendFavSession(sessionSlotId, dataHandler);
                    break;
                }
            }
        }
    }

    private void sendFavSession(String sessionSlotId, Consumer<boolean[]> dataHandler) {
        if (service.readyProperty().get()) {
            doSendFavSession(sessionSlotId, dataHandler);
        } else {
            service.readyProperty().addListener((obs, ov, nv) -> {
                doSendFavSession(sessionSlotId, dataHandler);
            });
        }
    }
    
    private boolean found;
    private void doSendFavSession(String sessionSlotId, Consumer<boolean[]> dataHandler) {
        if (service.isAuthenticated()) {
            ObservableList<Session> retrieveFavoredSessions = service.retrieveFavoredSessions();
            found = false;
            for (Session s : retrieveFavoredSessions) {
                if (s.getSlotId().equalsIgnoreCase(sessionSlotId)) {
                    found = true;
                    break;
                }
            }
            if (dataHandler != null) {
                Platform.runLater(() -> dataHandler.accept(new boolean[] {false, found}));
            }
            
        } else {
            if (dataHandler != null) {
                Platform.runLater(() -> dataHandler.accept(new boolean[] {true, false}));
            }
        }
    }

    private void sendSpeakers(String sessionSlotId, Consumer<List<WearSpeaker>> dataHandler) {
        if (service.readyProperty().get()) {
            getSpeakers(sessionSlotId, dataHandler);
        } else {
            service.readyProperty().addListener((obs, ov, nv) -> {
                getSpeakers(sessionSlotId, dataHandler);
            });
        }
    }

    private void getSpeakers(String sessionSlotId, Consumer<List<WearSpeaker>> dataHandler) {
        ArrayList<String> speakersUUID = new ArrayList<>();
         
        for (Session session : service.retrieveSessions()) {
                
            if (session.getSlotId().equals(sessionSlotId)) {
                
                for (TalkSpeaker talkSpeaker : session.getTalk().getSpeakers()) {
                    Link link = talkSpeaker.getLink();
                    if (link != null && link.getHref() != null && !link.getHref().isEmpty()) {
                        String speakerUUID = link.getHref().substring(link.getHref().lastIndexOf('/') + 1);
                        speakersUUID.add(speakerUUID);
                    }
                }
                break;
            }
        }
        
        List<WearSpeaker> speakersList = new ArrayList<>();

        LOG.log(Level.INFO, String.format("Retrieving %d speakers", speakersUUID.size()));
        CountDownLatch fillSpeakersCountDownLatch = new CountDownLatch(speakersUUID.size());
        final AtomicInteger processed = new AtomicInteger(0);
        new Thread(() -> {
            try {
                boolean ok = fillSpeakersCountDownLatch.await(10, TimeUnit.SECONDS);
                if (ok && processed.get() == speakersUUID.size()) {
                    Platform.runLater(() -> dataHandler.accept(speakersList));
                } else {
                    LOG.log(Level.WARNING, "There was an error retrieving speakers");
                }
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, "Error retrieving speakers", ex);
            }
        }).start();
        
        for (String uuid : speakersUUID) {
            new Thread(() -> {
                ReadOnlyObjectProperty<Speaker> speaker = service.retrieveSpeaker(uuid);
                if (speaker.get() != null) {
                    speakersList.add(getSpeaker(speaker.get()));
                    processed.getAndIncrement();
                    fillSpeakersCountDownLatch.countDown();
                } else {
                    speaker.addListener((obs, ov, nv) -> {
                        speakersList.add(getSpeaker(speaker.get()));
                        processed.getAndIncrement();
                        fillSpeakersCountDownLatch.countDown();
                    });
                }
            }).start();
        }
    }
    
    private WearSpeaker getSpeaker(Speaker speaker) {
        WearSpeaker wearSpeaker = new WearSpeaker();
        Speaker s = new Speaker();
        s.setFirstName(speaker.getFirstName());
        s.setLastName(speaker.getLastName());
        s.setTwitter(speaker.getTwitter());
        wearSpeaker.setSpeaker(s);
        try {
            InputStream is;
            if (speaker.getPicture() == null || speaker.getPicture().isEmpty()) {
                is = Util.class.getResourceAsStream("speaker.png");
            } else {
                is = new URL(speaker.getPicture()).openStream();
            } 
            wearSpeaker.setImage(new Image(is, 64, 64, true, false));
        } catch (IOException ex) { }
        return wearSpeaker;
    }
    
    private void followOnTwitter(String data) {
        // Not available
    }

    private void sendSummary(String sessionSlotId, Consumer<String> dataHandler) {
        if (service.readyProperty().get()) {
            getSessionSummary(sessionSlotId, dataHandler);
        } else {
            service.readyProperty().addListener((obs, ov, nv) -> {
                getSessionSummary(sessionSlotId, dataHandler);
            });
        }
    }

    private void getSessionSummary(String sessionSlotId, Consumer<String> dataHandler) {
        for (Session session : service.retrieveSessions()) {
            if (session.getSlotId().equals(sessionSlotId)) {
                if (dataHandler != null) {
                    Platform.runLater(() -> dataHandler.accept(session.getSummary()));
                }
                break;
            }
        }
    }

    private void openMobile(String data) {
        // not available
    }
    
}
