package com.arcusys.liferay.vaadinplugin.ui;

/*
 * #%L
 * Liferay Vaadin Plugin
 * %%
 * Copyright (C) 2010 - 2013 Vaadin Ltd.
 * Copyright (C) 2013 Arcusys Ltd.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.arcusys.liferay.vaadinplugin.ControlPanelUI;
import com.arcusys.liferay.vaadinplugin.util.*;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.vaadin.data.Container;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.ui.*;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

public class ChangeVersionWindow extends Window {
    private static final Log log = LogFactoryUtil.getLog(ChangeVersionWindow.class);

    private static final String VERSION_PROPERTY = "name";
    private static final String RELEASE_TYPE_PROPERTY = "releaseType";

    private Thread versionFetch = new Thread() {
        private List<VaadinVersion> getVersions() {
            List<VaadinVersion> versionList = VersionStorage.GetInstance().getVersions();
            if (versionList == null) {
                long cacheLifeTime = 1L*60*60*1000;
                versionList = new VaadinVersionFetcher().fetchAllVersionList();
                VersionStorage.GetInstance().setVersions(versionList, cacheLifeTime);
            }
            return versionList;
        }
        @Override
        public void run() {
            try {
                List<VaadinVersion> versionList = getVersions();
                getUI().getSession().getLockInstance().lock();
                    beanItemContainer.addAll(versionList);
                    updateState(true);
                getUI().getSession().getLockInstance().unlock();
            } catch (Exception e) {
                log.warn("Version list could not be downloaded", e);
                getUI().getSession().getLockInstance().lock();

                layout.removeAllComponents();
                layout.addComponent(new Label( "Version list could not be downloaded: " + e.getMessage()));
                getUI().getSession().getLockInstance().unlock();
            } finally {
                // Release memory
                versionFetch = null;
            }
        }
    };

    private final VerticalLayout layout = new VerticalLayout();
    private final BeanItemContainer<VaadinVersion> beanItemContainer = new BeanItemContainer<VaadinVersion>(
            VaadinVersion.class);

    private final OptionGroup includeVersions = new OptionGroup(
            "Also include non-stable versions", EnumSet.of(
            VaadinVersion.VaadinReleaseType.prerelease, VaadinVersion.VaadinReleaseType.nightly));

    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final ComboBox versionSelection = new ComboBox("Select version", beanItemContainer);

    private final Button changeVersionButton = new Button("Change version",
            new Button.ClickListener() {
                public void buttonClick(Button.ClickEvent event) {
                    VaadinVersion version = (VaadinVersion) versionSelection
                            .getValue();
                    if (version == null) {

                        Notification.show("Please select a version");
                        return;
                    }

                    ControlPanelUI mainUI = (ControlPanelUI) getUI();
                    mainUI.showWarningWindow(version.getDownloadUrl());
                    close();
                }
            });
    private final Button cancelButton = new Button("Cancel",
            new Button.ClickListener() {
                public void buttonClick(Button.ClickEvent event) {
                    close();
                }
            });

    public ChangeVersionWindow() {
        super("Change Vaadin version");
        setModal(true);
        setSizeUndefined();

        layout.setMargin(true);
        layout.setSizeUndefined();
        layout.setSpacing(true);

        setContent(layout);

        progressIndicator.setCaption("Fetching version list");

        includeVersions.setItemCaptionMode(ComboBox.ItemCaptionMode.ID);

        includeVersions.setMultiSelect(true);
        includeVersions.setValue(Collections
                .singleton(VaadinVersion.VaadinReleaseType.release));
        includeVersions.setImmediate(true);
        includeVersions.addValueChangeListener(new Property.ValueChangeListener() {
            public void valueChange(Property.ValueChangeEvent event) {
                updateFilter();
            }
        });

        versionSelection.setItemCaptionPropertyId(VERSION_PROPERTY);
        versionSelection.setNullSelectionAllowed(false);
        versionSelection.setRequired(true);

        //versionFetch.start();

        layout.addComponent(includeVersions);
        layout.addComponent(versionSelection);

        HorizontalLayout buttonRow = new HorizontalLayout();
        buttonRow.setSpacing(true);
        buttonRow.addComponent(changeVersionButton);
        buttonRow.addComponent(cancelButton);

        layout.addComponent(buttonRow);

        updateState(false);

        updateFilter();
    }

    public void initialize() {
        versionFetch.start();
    }

    private void updateFilter() {
        @SuppressWarnings("unchecked")
        final Collection<VaadinVersion.VaadinReleaseType> types = (Collection<VaadinVersion.VaadinReleaseType>) includeVersions
                .getValue();
        beanItemContainer.removeAllContainerFilters();
        beanItemContainer.addContainerFilter(new Container.Filter() {
            public boolean passesFilter(Object itemId, Item item)
                    throws UnsupportedOperationException {
                Object releaseType = item
                        .getItemProperty(RELEASE_TYPE_PROPERTY).getValue();
                return releaseType == VaadinVersion.VaadinReleaseType.release
                        || types.contains(releaseType);
            }

            public boolean appliesToProperty(Object propertyId) {
                return RELEASE_TYPE_PROPERTY.equals(propertyId);
            }

        });
    }

    private void updateState(boolean enabled) {
        versionSelection.setEnabled(enabled);
        includeVersions.setEnabled(enabled);
        changeVersionButton.setEnabled(enabled);
        cancelButton.setEnabled(enabled);

        if (enabled && progressIndicator.getParent() != null) {
            layout.removeComponent(progressIndicator);
        } else if (!enabled && progressIndicator.getParent() == null) {
            layout.addComponent(progressIndicator, 0);
        }
    }

}