/*
 * Copyright (c) 2016, 2018 Gluon Software
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
package com.devoxx.views;

import com.devoxx.DevoxxApplication;
import com.devoxx.DevoxxView;
import com.devoxx.model.Badge;
import com.devoxx.model.BadgeType;
import com.devoxx.model.Sponsor;
import com.devoxx.model.SponsorBadge;
import com.devoxx.service.Service;
import com.devoxx.util.BasicSecureFilter;
import com.devoxx.util.DevoxxBundle;
import com.devoxx.util.DevoxxSettings;
import com.devoxx.views.cell.BadgeCell;
import com.devoxx.views.helper.Placeholder;
import com.devoxx.views.helper.Util;
import com.gluonhq.attach.barcodescan.BarcodeScanService;
import com.gluonhq.attach.settings.SettingsService;
import com.gluonhq.attach.util.Services;
import com.gluonhq.charm.glisten.afterburner.GluonPresenter;
import com.gluonhq.charm.glisten.application.ViewStackPolicy;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.CharmListView;
import com.gluonhq.charm.glisten.control.FloatingActionButton;
import com.gluonhq.charm.glisten.control.Toast;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import com.gluonhq.connect.GluonObservableList;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;

import javax.inject.Inject;
import java.util.Optional;

public class SponsorBadgePresenter extends GluonPresenter<DevoxxApplication> {

    private static final String EMPTY_LIST_MESSAGE = DevoxxBundle.getString("OTN.BADGES.EMPTY_LIST_MESSAGE");

    @FXML
    private View sponsorView;

    @FXML
    private Label badgesCount;
    
    @Inject
    private Service service;

    private Sponsor sponsor;
    private FloatingActionButton scan;

    public void initialize() {

        scan = new FloatingActionButton();
        scan.getStyleClass().add("badge-scanner");
        scan.showOn(sponsorView);

        sponsorView.setOnShowing(event -> {
            updateAppBar(getApp().getAppBar());

            Services.get(SettingsService.class).ifPresent(service -> {
                if (this.sponsor != null){
                    setSponsor(this.sponsor);
                } else {
                    final Sponsor sponsor = Sponsor.fromCSV(service.retrieve(DevoxxSettings.BADGE_SPONSOR));
                    if (sponsor != null){
                        setSponsor(sponsor);
                    }
                }
            });
        });
    }

    public void setSponsor(Sponsor sponsor) {
        this.sponsor = sponsor;
        loadSponsorBadges(sponsor);
    }

    private void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(getApp().getNavMenuButton());
        appBar.setTitleText(DevoxxView.SPONSOR_BADGE.getTitle());
    }

    private void loadSponsorBadges(Sponsor sponsor) {
        final GluonObservableList<SponsorBadge> badges = service.retrieveSponsorBadges(sponsor);
        final FilteredList<SponsorBadge> filteredBadges = new FilteredList<>(badges, badge -> {
        	badge.setSponsor(sponsor);
            return badge != null /*&& badge.getSponsor() != null && badge.getSponsor().equals(sponsor)*/;
        });
        
        badgesCount.setVisible(false);
        badges.setOnSucceeded(event -> {        	
        	String text = "";
        	switch (badges.size()) {
        		case 1: text = "1 badge"; break;
        		default: text = badges.size() + " badges"; break;
        	}
            badgesCount.setVisible(badges.size()>0);
			badgesCount.setText(text);
        });
        
        CharmListView<SponsorBadge, String> sponsorBadges = new CharmListView<>(filteredBadges);
        sponsorBadges.setPlaceholder(new Placeholder(EMPTY_LIST_MESSAGE, DevoxxView.SPONSOR_BADGE.getMenuIcon()));
        sponsorBadges.setCellFactory(param -> new BadgeCell<>());
        
        sponsorView.setCenter(sponsorBadges);
        

        final Button shareButton = getApp().getShareButton(BadgeType.SPONSOR, sponsor);
        shareButton.disableProperty().bind(sponsorBadges.itemsProperty().emptyProperty());
        AppBar appBar = getApp().getAppBar();
        appBar.setTitleText(DevoxxBundle.getString("OTN.SPONSOR.BADGES.FOR", sponsor.getName()));

        final Button logoutButton = MaterialDesignIcon.EXIT_TO_APP.button(e -> {
            Util.removeKeysFromSettings(DevoxxSettings.BADGE_TYPE, DevoxxSettings.BADGE_SPONSOR);
            this.sponsor = null;
            service.logoutSponsor();
            DevoxxView.BADGES.switchView();
        });
        appBar.getActionItems().setAll(shareButton, logoutButton);

//		  TODO decide where to show badges count
//        badges.setOnSucceeded(event -> {
//        	if (badges.size()==0) {
//        		appBar.setTitleText(DevoxxBundle.getString("OTN.SPONSOR.BADGES.FOR", sponsor.getName()));
//        	} else {
//        		appBar.setTitleText(DevoxxBundle.getString("OTN.SPONSOR.BADGES.FOR", sponsor.getName()) + " (" + badges.size()+ ")");
//        	}
//        });
        
        scan.setOnAction(e -> {
            if (DevoxxSettings.BADGE_TESTS) {
                addBadge(sponsor, badges, Util.getDummyQR());
                return;
            }
            Services.get(BarcodeScanService.class).ifPresent(s -> {
                final Optional<String> scanQr = s.scan(DevoxxBundle.getString("OTN.BADGES.SPONSOR.QR.TITLE", sponsor.getName()), null, null);
                scanQr.ifPresent(qr -> addBadge(sponsor, badges, qr));
            });
        });
    }

    private void addBadge(Sponsor sponsor, ObservableList<SponsorBadge> badges, String qr) {
    	try {
			BasicSecureFilter filter = new BasicSecureFilter();
			SponsorBadge badge = SponsorBadge.parseBadge(filter.decrypt(qr));
			if (badge.getEmail() != null) {
			    boolean exists = false;
			    for (Badge b : badges) {
			        if (b != null && b.getEmail() != null && b.getEmail().equals(badge.getEmail())) {
			            Toast toast = new Toast(DevoxxBundle.getString("OTN.BADGES.QR.EXISTS"));
			            toast.show();
			            exists = true;
			            break;
			        }
			    }
			    if (!exists) {
			        badge.setSponsor(sponsor);
			        badges.add(badge);
			        // Keep SponsorBadgeView on view stack 
			        DevoxxView.BADGE.switchView(ViewStackPolicy.USE).ifPresent(presenter -> ((BadgePresenter) presenter).setBadge(badge, BadgeType.SPONSOR));
			    }
			} else {
			    Toast toast = new Toast(DevoxxBundle.getString("OTN.BADGES.BAD.QR"));
			    toast.show();
			}
		} catch (Exception e) {
		    Toast toast = new Toast(DevoxxBundle.getString("OTN.BADGES.BAD.QR"));
		    toast.show();
		}
    }

    private MenuItem getBadgeChangeMenuItem(String text) {
        final MenuItem scanAsDifferentUser = new MenuItem(text);
        scanAsDifferentUser.setOnAction(e -> {
            Util.removeKeysFromSettings(DevoxxSettings.BADGE_TYPE, DevoxxSettings.BADGE_SPONSOR);
            this.sponsor = null;
            service.logoutSponsor();
            DevoxxView.BADGES.switchView();
        });
        return scanAsDifferentUser;
    }
    
}
