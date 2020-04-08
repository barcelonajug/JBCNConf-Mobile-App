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
import com.devoxx.model.Sponsor;
import com.devoxx.service.Service;
import com.devoxx.util.DevoxxBundle;
import com.devoxx.views.cell.SponsorLogoHeaderCell;
import com.devoxx.views.cell.SponsorsLogoCell;
import com.devoxx.views.helper.Placeholder;
import com.gluonhq.charm.glisten.afterburner.GluonPresenter;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.CharmListView;
import com.gluonhq.charm.glisten.control.ProgressIndicator;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.connect.GluonObservableList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

import javax.inject.Inject;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public class SponsorsLogoPresenter extends GluonPresenter<DevoxxApplication> {

    private static final String PLACEHOLDER_TITLE = DevoxxBundle.getString("OTN.PLACEHOLDER.TITLE");
    private static final String PLACEHOLDER_MESSAGE = DevoxxBundle.getString("OTN.PLACEHOLDER.MESSAGE");
    private boolean loadDataFromService = true;


    @FXML
    private View sponsors;

    @FXML
    private CharmListView<Sponsor, Integer> sponsorListView;

    @Inject
    private Service service;

    public void initialize() {
        sponsors.setOnShowing(event -> {
            AppBar appBar = getApp().getAppBar();
            appBar.setNavIcon(getApp().getNavMenuButton());
            appBar.setTitleText(DevoxxView.SPONSORS_LOGO.getTitle());
            sponsorListView.setSelectedItem(null);
            if (loadDataFromService) {
                retrieveSponsorList();
                loadDataFromService = false;
            }
        });

        sponsorListView.getStyleClass().add("sponsor-logo-list-view");
        sponsorListView.setPlaceholder(new Placeholder(PLACEHOLDER_TITLE, PLACEHOLDER_MESSAGE, DevoxxView.SPONSORS_LOGO.getMenuIcon()));
        sponsorListView.setHeadersFunction(s -> s.getLevel().getPriority());
        sponsorListView.setHeaderCellFactory(p -> new SponsorLogoHeaderCell());
        sponsorListView.setCellFactory(p -> new SponsorsLogoCell());
    }

    private void retrieveSponsorList() {
        final AppBar appBar = getApp().getAppBar();
        appBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        appBar.setProgressBarVisible(true);

        sponsorListView.setPlaceholder(new Placeholder(PLACEHOLDER_TITLE, PLACEHOLDER_MESSAGE, DevoxxView.SPONSORS_LOGO.getMenuIcon()));

        GluonObservableList<Sponsor> sponsorsList = service.retrieveSponsors();
        sponsorsList.setOnSucceeded(e -> {
            appBar.setProgressBarVisible(false);
            if (sponsorsList.isEmpty()) {
                sponsorListView.setPlaceholder(Placeholder.empty("sponsors", service.getConference().getName()));
            } else {
                sponsorListView.setItems(filterSponsorsWithoutImage(sponsorsList));
            }
        });
        sponsorsList.setOnFailed(e -> {
            appBar.setProgressBarVisible(false);
            final Button retry = new Button("Retry");
            retry.setOnAction(ae -> retrieveSponsorList());
            sponsorListView.setPlaceholder(Placeholder.failure("Sponsors", retry));
        });
    }

    private ObservableList<Sponsor> filterSponsorsWithoutImage(GluonObservableList<Sponsor> sponsorsList) {
        return sponsorsList.stream()
                .filter(s -> s.getImage() != null)
                .collect(Collectors.collectingAndThen(toList(), FXCollections::observableArrayList));
    }
}
