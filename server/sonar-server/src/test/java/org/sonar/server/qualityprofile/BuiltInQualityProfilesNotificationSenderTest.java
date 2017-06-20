/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.qualityprofile;

import java.util.List;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.notifications.Notification;
import org.sonar.server.notification.NotificationManager;
import org.sonar.server.qualityprofile.BuiltInQualityProfilesNotification.Profile;

import static java.util.Arrays.asList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BuiltInQualityProfilesNotificationSenderTest {

  private NotificationManager notificationManager = mock(NotificationManager.class);

  private BuiltInQualityProfilesNotificationSender underTest = new BuiltInQualityProfilesNotificationSender(notificationManager);

  @Test
  public void add_profile_to_notification() throws Exception {
    String profileName = randomLowerCaseText();
    String language = randomLowerCaseText();
    List<QProfileName> profileNames = asList(new QProfileName(language, profileName));

    underTest.send(profileNames);

    ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationManager).scheduleForSending(notificationArgumentCaptor.capture());
    verifyNoMoreInteractions(notificationManager);
    assertThat(BuiltInQualityProfilesNotification.parse(notificationArgumentCaptor.getValue()).getProfiles())
      .extracting(Profile::getProfileName, Profile::getLanguage)
      .containsExactlyInAnyOrder(tuple(profileName, language));
  }

  private static String randomLowerCaseText() {
    return randomAlphanumeric(20).toLowerCase();
  }
}