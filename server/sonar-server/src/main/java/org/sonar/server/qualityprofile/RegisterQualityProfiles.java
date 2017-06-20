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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.sonar.api.server.ServerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.qualityprofile.RulesProfileDto;

import static java.lang.String.format;

/**
 * Synchronize Quality profiles during server startup
 */
@ServerSide
public class RegisterQualityProfiles {

  private static final Logger LOGGER = Loggers.get(RegisterQualityProfiles.class);

  private final BuiltInQProfileRepository builtInQProfileRepository;
  private final DbClient dbClient;
  private final BuiltInQProfileInsert builtInQProfileInsert;
  private final BuiltInQProfileUpdate builtInQProfileUpdate;
  private final BuiltInQualityProfilesNotificationSender builtInQualityProfilesNotification;

  public RegisterQualityProfiles(BuiltInQProfileRepository builtInQProfileRepository,
    DbClient dbClient, BuiltInQProfileInsert builtInQProfileInsert, BuiltInQProfileUpdate builtInQProfileUpdate,
    BuiltInQualityProfilesNotificationSender builtInQualityProfilesNotification) {
    this.builtInQProfileRepository = builtInQProfileRepository;
    this.dbClient = dbClient;
    this.builtInQProfileInsert = builtInQProfileInsert;
    this.builtInQProfileUpdate = builtInQProfileUpdate;
    this.builtInQualityProfilesNotification = builtInQualityProfilesNotification;
  }

  public void start() {
    List<BuiltInQProfile> builtInQProfiles = builtInQProfileRepository.get();
    if (builtInQProfiles.isEmpty()) {
      return;
    }

    Profiler profiler = Profiler.create(Loggers.get(getClass())).startInfo("Register quality profiles");
    try (DbSession dbSession = dbClient.openSession(false);
      DbSession batchDbSession = dbClient.openSession(true)) {

      Map<QProfileName, RulesProfileDto> persistedRuleProfiles = loadPersistedProfiles(dbSession);

      List<QProfileName> changedProfiles = new ArrayList<>();
      builtInQProfiles.forEach(builtIn -> {
        RulesProfileDto ruleProfile = persistedRuleProfiles.get(builtIn.getQProfileName());
        if (ruleProfile == null) {
          register(dbSession, batchDbSession, builtIn);
        } else {
          List<ActiveRuleChange> changes = update(dbSession, builtIn, ruleProfile);
          if (!changes.isEmpty()) {
            changedProfiles.add(builtIn.getQProfileName());
          }
        }
      });
      if (!changedProfiles.isEmpty()) {
        builtInQualityProfilesNotification.send(changedProfiles);
      }
    }
    profiler.stopDebug();
  }

  private Map<QProfileName, RulesProfileDto> loadPersistedProfiles(DbSession dbSession) {
    return dbClient.qualityProfileDao().selectBuiltInRulesProfiles(dbSession).stream()
      .collect(MoreCollectors.uniqueIndex(rp -> new QProfileName(rp.getLanguage(), rp.getName())));
  }

  private void register(DbSession dbSession, DbSession batchDbSession, BuiltInQProfile builtIn) {
    LOGGER.info("Register profile {}", builtIn.getQProfileName());

    renameOutdatedProfiles(dbSession, builtIn);

    builtInQProfileInsert.create(dbSession, batchDbSession, builtIn);
  }

  private List<ActiveRuleChange> update(DbSession dbSession, BuiltInQProfile builtIn, RulesProfileDto ruleProfile) {
    LOGGER.info("Update profile {}", builtIn.getQProfileName());

    return builtInQProfileUpdate.update(dbSession, builtIn, ruleProfile);
  }

  /**
   * The Quality profiles created by users should be renamed when they have the same name
   * as the built-in profile to be persisted.
   *
   * When upgrading from < 6.5 , all existing profiles are considered as "custom" (created
   * by users) because the concept of built-in profile is not persisted. The "Sonar way" profiles
   * are renamed to "Sonar way (outdated copy) in order to avoid conflicts with the new
   * built-in profile "Sonar way", which has probably different configuration.
   */
  private void renameOutdatedProfiles(DbSession dbSession, BuiltInQProfile profile) {
    Collection<String> uuids = dbClient.qualityProfileDao().selectUuidsOfCustomRulesProfiles(dbSession, profile.getLanguage(), profile.getName());
    if (uuids.isEmpty()) {
      return;
    }
    Profiler profiler = Profiler.createIfDebug(Loggers.get(getClass())).start();
    String newName = profile.getName() + " (outdated copy)";
    LOGGER.info("Rename Quality profiles [{}/{}] to [{}] in {} organizations", profile.getLanguage(), profile.getName(), newName, uuids.size());
    dbClient.qualityProfileDao().renameRulesProfilesAndCommit(dbSession, uuids, newName);
    profiler.stopDebug(format("%d Quality profiles renamed to [%s]", uuids.size(), newName));
  }
}
