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

import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.RulePriority;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.internal.AlwaysIncreasingSystem2;
import org.sonar.api.utils.log.LogTester;
import org.sonar.core.util.UuidFactoryFast;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.qualityprofile.ActiveRuleDto;
import org.sonar.db.qualityprofile.RulesProfileDto;
import org.sonar.db.rule.RuleDefinitionDto;
import org.sonar.server.qualityprofile.index.ActiveRuleIndexer;
import org.sonar.server.rule.index.RuleIndex;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.util.TypeValidations;

import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.sonar.api.rules.Rule.create;
import static org.sonar.api.rules.RulePriority.MAJOR;
import static org.sonar.db.qualityprofile.QualityProfileTesting.newRuleProfileDto;
import static org.sonar.server.language.LanguageTesting.newLanguage;

public class RegisterQualityProfilesNotificationTest {

  private System2 system2 = new AlwaysIncreasingSystem2();
  @Rule
  public DbTester db = DbTester.create(system2);
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public BuiltInQProfileRepositoryRule builtInQProfileRepositoryRule = new BuiltInQProfileRepositoryRule();
  @Rule
  public LogTester logTester = new LogTester();

  private DbClient dbClient = db.getDbClient();
  private TypeValidations typeValidations = mock(TypeValidations.class);
  private ActiveRuleIndexer activeRuleIndexer = mock(ActiveRuleIndexer.class);
  private BuiltInQProfileInsert builtInQProfileInsert = new BuiltInQProfileInsertImpl(dbClient, system2, UuidFactoryFast.getInstance(), typeValidations, activeRuleIndexer);
  private RuleActivator ruleActivator = new RuleActivator(system2, dbClient, mock(RuleIndex.class), new RuleActivatorContextFactory(dbClient), typeValidations, activeRuleIndexer,
    userSessionRule);
  private BuiltInQProfileUpdate builtInQProfileUpdate = new BuiltInQProfileUpdateImpl(dbClient, ruleActivator, activeRuleIndexer);
  private BuiltInQualityProfilesNotificationSender builtInQualityProfilesNotification = mock(BuiltInQualityProfilesNotificationSender.class);
  private RegisterQualityProfiles underTest = new RegisterQualityProfiles(builtInQProfileRepositoryRule, dbClient,
    builtInQProfileInsert, builtInQProfileUpdate, builtInQualityProfilesNotification);

  @Test
  public void does_not_send_notification_on_new_profile() {
    String language = newLanguageKey();
    builtInQProfileRepositoryRule.add(newLanguage(language), "Sonar way");
    builtInQProfileRepositoryRule.initialize();

    underTest.start();

    verifyZeroInteractions(builtInQualityProfilesNotification);
  }

  @Test
  public void does_not_send_notification_when_built_in_profile_is_not_updated() {
    String language = newLanguageKey();
    RuleDefinitionDto dbRule = db.rules().insert(r -> r.setLanguage(language));
    RulesProfileDto dbProfile = insertBuiltInProfile(language);
    activateRuleInDb(dbProfile, dbRule, MAJOR);
    addPluginProfile(dbProfile, dbRule);
    builtInQProfileRepositoryRule.initialize();

    underTest.start();

    verifyZeroInteractions(builtInQualityProfilesNotification);
  }

  @Test
  public void send_notification_when_built_in_profile_contains_new_rule() {
    String language = newLanguageKey();
    RuleDefinitionDto existingRule = db.rules().insert(r -> r.setLanguage(language));
    RulesProfileDto dbProfile = insertBuiltInProfile(language);
    activateRuleInDb(dbProfile, existingRule, MAJOR);
    RuleDefinitionDto newRule = db.rules().insert(r -> r.setLanguage(language));
    addPluginProfile(dbProfile, existingRule, newRule);
    builtInQProfileRepositoryRule.initialize();

    underTest.start();

    verify(builtInQualityProfilesNotification).send();
  }

  @Test
  public void only_send_one_notification_when_several_built_in_profiles_contain_new_rules() {
    String language = newLanguageKey();
    RuleDefinitionDto existingRule = db.rules().insert(r -> r.setLanguage(language));
    RuleDefinitionDto newRule = db.rules().insert(r -> r.setLanguage(language));

    RulesProfileDto dbProfile1 = insertBuiltInProfile(language);
    activateRuleInDb(dbProfile1, existingRule, MAJOR);
    addPluginProfile(dbProfile1, existingRule, newRule);

    RulesProfileDto dbProfile2 = insertBuiltInProfile(language);
    activateRuleInDb(dbProfile2, existingRule, MAJOR);
    addPluginProfile(dbProfile2, existingRule, newRule);
    builtInQProfileRepositoryRule.initialize();

    underTest.start();

    verify(builtInQualityProfilesNotification).send();
  }

  private void addPluginProfile(RulesProfileDto dbProfile, RuleDefinitionDto... dbRules) {
    RulesProfile pluginProfile = RulesProfile.create(dbProfile.getName(), dbProfile.getLanguage());
    Arrays.stream(dbRules).forEach(dbRule -> pluginProfile.activateRule(create(dbRule.getRepositoryKey(), dbRule.getRuleKey()), MAJOR));
    builtInQProfileRepositoryRule.add(newLanguage(dbProfile.getLanguage()), dbProfile.getName(), false, pluginProfile.getActiveRules().toArray(new ActiveRule[0]));
  }

  private RulesProfileDto insertBuiltInProfile(String language) {
    RulesProfileDto ruleProfileDto = newRuleProfileDto(rp -> rp.setIsBuiltIn(true).setLanguage(language));
    db.getDbClient().qualityProfileDao().insert(db.getSession(), ruleProfileDto);
    db.commit();
    return ruleProfileDto;
  }

  private void activateRuleInDb(RulesProfileDto profile, RuleDefinitionDto rule, RulePriority severity) {
    ActiveRuleDto dto = new ActiveRuleDto()
      .setProfileId(profile.getId())
      .setSeverity(severity.name())
      .setRuleId(rule.getId())
      .setCreatedAt(nextLong())
      .setUpdatedAt(nextLong());
    db.getDbClient().activeRuleDao().insert(db.getSession(), dto);
    db.commit();
  }

  private static String newLanguageKey() {
    return randomAlphanumeric(20).toLowerCase();
  }
}
