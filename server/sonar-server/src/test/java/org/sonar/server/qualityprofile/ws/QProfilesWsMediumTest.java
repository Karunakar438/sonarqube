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
package org.sonar.server.qualityprofile.ws;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationTesting;
import org.sonar.db.qualityprofile.ActiveRuleDao;
import org.sonar.db.qualityprofile.ActiveRuleDto;
import org.sonar.db.qualityprofile.ActiveRuleKey;
import org.sonar.db.qualityprofile.QProfileDto;
import org.sonar.db.rule.RuleDefinitionDto;
import org.sonar.db.rule.RuleTesting;
import org.sonar.server.es.SearchOptions;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.qualityprofile.QProfileName;
import org.sonar.server.qualityprofile.QProfileTesting;
import org.sonar.server.qualityprofile.index.ActiveRuleIndexer;
import org.sonar.server.rule.index.RuleIndex;
import org.sonar.server.rule.index.RuleIndexer;
import org.sonar.server.rule.index.RuleQuery;
import org.sonar.server.tester.ServerTester;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.sonarqube.ws.client.qualityprofile.QualityProfileWsParameters.ACTION_ACTIVATE_RULE;
import static org.sonarqube.ws.client.qualityprofile.QualityProfileWsParameters.ACTION_DEACTIVATE_RULE;
import static org.sonarqube.ws.client.qualityprofile.QualityProfileWsParameters.ActivateActionParameters.PARAM_PROFILE_KEY;
import static org.sonarqube.ws.client.qualityprofile.QualityProfileWsParameters.ActivateActionParameters.PARAM_RULE_KEY;
import static org.sonarqube.ws.client.qualityprofile.QualityProfileWsParameters.ActivateActionParameters.PARAM_SEVERITY;
import static org.sonarqube.ws.client.rule.RulesWsParameters.PARAM_LANGUAGES;
import static org.sonarqube.ws.client.rule.RulesWsParameters.PARAM_QPROFILE;

public class QProfilesWsMediumTest {

  @ClassRule
  public static ServerTester tester = new ServerTester().withEsIndexes();

  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.forServerTester(tester)
    .logIn().setRoot();

  private DbClient db;
  private DbSession session;
  private WsTester wsTester;
  private RuleIndexer ruleIndexer = tester.get(RuleIndexer.class);
  private ActiveRuleIndexer activeRuleIndexer = tester.get(ActiveRuleIndexer.class);
  private OrganizationDto organization;

  @Before
  public void setUp() {
    tester.clearDbAndIndexes();
    db = tester.get(DbClient.class);
    wsTester = tester.get(WsTester.class);
    session = db.openSession(false);

    ruleIndexer = tester.get(RuleIndexer.class);
    activeRuleIndexer = tester.get(ActiveRuleIndexer.class);
    organization = OrganizationTesting.newOrganizationDto().setKey("org-123");
    db.organizationDao().insert(session, organization, false);
  }

  @After
  public void after() {
    session.close();
  }

  @Test
  public void deactivate_rule() throws Exception {
    QProfileDto profile = createProfile("java");
    RuleDefinitionDto rule = createRule(profile.getLanguage(), "toto");
    createActiveRule(rule, profile);
    session.commit();
    ruleIndexer.indexRuleDefinition(rule.getKey());
    activeRuleIndexer.indexOnStartup(activeRuleIndexer.getIndexTypes());

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(1);

    // 1. Deactivate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ACTION_DEACTIVATE_RULE);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    request.setParam(PARAM_RULE_KEY, rule.getKey().toString());
    request.execute();
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();
  }

  @Test
  public void bulk_deactivate_rule() throws Exception {
    QProfileDto profile = createProfile("java");
    RuleDefinitionDto rule0 = createRule(profile.getLanguage(), "toto1");
    RuleDefinitionDto rule1 = createRule(profile.getLanguage(), "toto2");
    RuleDefinitionDto rule2 = createRule(profile.getLanguage(), "toto3");
    RuleDefinitionDto rule3 = createRule(profile.getLanguage(), "toto4");
    createActiveRule(rule0, profile);
    createActiveRule(rule2, profile);
    createActiveRule(rule3, profile);
    createActiveRule(rule1, profile);
    session.commit();
    activeRuleIndexer.indexOnStartup(activeRuleIndexer.getIndexTypes());

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(4);

    // 1. Deactivate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, DeactivateRulesAction.DEACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    WsTester.Result result = request.execute();
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();
  }

  @Test
  public void bulk_deactivate_rule_not_all() throws Exception {
    QProfileDto profile = createProfile("java");
    QProfileDto php = createProfile("php");
    RuleDefinitionDto rule0 = createRule(profile.getLanguage(), "toto1");
    RuleDefinitionDto rule1 = createRule(profile.getLanguage(), "toto2");
    createActiveRule(rule0, profile);
    createActiveRule(rule1, profile);
    createActiveRule(rule0, php);
    createActiveRule(rule1, php);
    session.commit();
    activeRuleIndexer.indexOnStartup(activeRuleIndexer.getIndexTypes());

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(2);

    // 1. Deactivate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, DeactivateRulesAction.DEACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    WsTester.Result result = request.execute();
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(0);
    assertThat(db.activeRuleDao().selectByProfileUuid(session, php.getKee())).hasSize(2);
  }

  @Test
  public void bulk_deactivate_rule_by_profile() throws Exception {
    QProfileDto profile = createProfile("java");
    RuleDefinitionDto rule0 = createRule(profile.getLanguage(), "hello");
    RuleDefinitionDto rule1 = createRule(profile.getLanguage(), "world");
    createActiveRule(rule0, profile);
    createActiveRule(rule1, profile);
    session.commit();
    activeRuleIndexer.indexOnStartup(activeRuleIndexer.getIndexTypes());

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(2);

    // 1. Deactivate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, DeactivateRulesAction.DEACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    request.setParam(WebService.Param.TEXT_QUERY, "hello");
    WsTester.Result result = request.execute();
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(1);
  }

  @Test
  public void activate_rule() throws Exception {
    QProfileDto profile = createProfile("java");
    RuleDefinitionDto rule = createRule(profile.getLanguage(), "toto");
    session.commit();
    ruleIndexer.indexRuleDefinition(rule.getKey());

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();

    // 1. Activate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ACTION_ACTIVATE_RULE);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    request.setParam(PARAM_RULE_KEY, rule.getKey().toString());
    WsTester.Result result = request.execute();
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(1);
  }

  @Test
  public void activate_rule_diff_languages() throws Exception {
    QProfileDto profile = createProfile("java");
    RuleDefinitionDto rule = createRule("php", "toto");
    session.commit();
    ruleIndexer.indexRuleDefinition(rule.getKey());

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();

    try {
      // 1. Activate Rule
      WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ACTION_ACTIVATE_RULE);
      request.setParam(PARAM_PROFILE_KEY, profile.getKee());
      request.setParam(PARAM_RULE_KEY, rule.getKey().toString());
      request.execute();
      session.clearCache();
      fail();
    } catch (BadRequestException e) {
      assertThat(e.getMessage()).isEqualTo("Rule blah:toto and profile pjava have different languages");
    }
  }

  @Test
  public void activate_rule_override_severity() throws Exception {
    QProfileDto profile = createProfile("java");
    RuleDefinitionDto rule = createRule(profile.getLanguage(), "toto");
    session.commit();
    ruleIndexer.indexRuleDefinition(rule.getKey());

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();

    // 1. Activate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ACTION_ACTIVATE_RULE);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    request.setParam(PARAM_RULE_KEY, rule.getKey().toString());
    request.setParam(PARAM_SEVERITY, "MINOR");
    WsTester.Result result = request.execute();
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    ActiveRuleKey activeRuleKey = ActiveRuleKey.of(profile, rule.getKey());

    Optional<ActiveRuleDto> activeRuleDto = db.activeRuleDao().selectByKey(session, activeRuleKey);
    assertThat(activeRuleDto.isPresent()).isTrue();
    assertThat(activeRuleDto.get().getSeverityString()).isEqualTo(Severity.MINOR);
  }

  @Test
  public void bulk_activate_rule() throws Exception {
    QProfileDto profile = createProfile("java");
    createRule(profile.getLanguage(), "toto");
    createRule(profile.getLanguage(), "tata");
    createRule(profile.getLanguage(), "hello");
    createRule(profile.getLanguage(), "world");
    session.commit();

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();

    // 1. Activate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ActivateRulesAction.ACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    request.setParam(PARAM_LANGUAGES, "java");
    request.execute().assertJson(getClass(), "bulk_activate_rule.json");
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(4);
  }

  @Test
  public void bulk_activate_rule_not_all() throws Exception {
    QProfileDto java = createProfile("java");
    QProfileDto php = createProfile("php");
    createRule(java.getLanguage(), "toto");
    createRule(java.getLanguage(), "tata");
    createRule(php.getLanguage(), "hello");
    createRule(php.getLanguage(), "world");
    session.commit();

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, php.getKee())).isEmpty();

    // 1. Activate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ActivateRulesAction.ACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, php.getKee());
    request.setParam(PARAM_LANGUAGES, "php");
    request.execute().assertJson(getClass(), "bulk_activate_rule_not_all.json");
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, php.getKee())).hasSize(2);
  }

  @Test
  public void bulk_activate_rule_by_query() throws Exception {
    QProfileDto profile = createProfile("java");
    createRule(profile.getLanguage(), "toto");
    createRule(profile.getLanguage(), "tata");
    createRule(profile.getLanguage(), "hello");
    createRule(profile.getLanguage(), "world");
    session.commit();

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();

    // 1. Activate Rule with query returning 0 hits
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ActivateRulesAction.ACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    request.setParam(WebService.Param.TEXT_QUERY, "php");
    request.execute();
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(0);

    // 1. Activate Rule with query returning 1 hits
    request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ActivateRulesAction.ACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, profile.getKee());
    request.setParam(WebService.Param.TEXT_QUERY, "world");
    request.execute();
    session.commit();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).hasSize(1);
  }

  @Test
  public void bulk_activate_rule_by_query_with_severity() throws Exception {
    QProfileDto profile = createProfile("java");
    RuleDefinitionDto rule0 = createRule(profile.getLanguage(), "toto");
    RuleDefinitionDto rule1 = createRule(profile.getLanguage(), "tata");
    session.commit();

    // 0. Assert No Active Rule for profile
    assertThat(db.activeRuleDao().selectByProfileUuid(session, profile.getKee())).isEmpty();

    // 2. Assert ActiveRule with BLOCKER severity
    assertThat(tester.get(RuleIndex.class).search(
      new RuleQuery().setSeverities(ImmutableSet.of("BLOCKER")),
      new SearchOptions()).getIds()).hasSize(2);

    // 1. Activate Rule with query returning 2 hits
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ActivateRulesAction.ACTIVATE_RULES_ACTION);
    request.setParam(ActivateRulesAction.PROFILE_KEY, profile.getKee());
    request.setParam(ActivateRulesAction.SEVERITY, "MINOR");
    request.execute();
    session.commit();

    // 2. Assert ActiveRule with MINOR severity
    assertThat(tester.get(ActiveRuleDao.class).selectByRuleId(session, organization, rule0.getId()).get(0).getSeverityString()).isEqualTo("MINOR");
    assertThat(tester.get(RuleIndex.class).searchAll(new RuleQuery()
      .setQProfile(profile)
      .setKey(rule0.getKey().toString())
      .setActiveSeverities(Collections.singleton("MINOR"))
      .setActivation(true))).hasSize(1);
  }

  @Test
  public void does_not_return_warnings_when_bulk_activate_on_profile_and_rules_exist_on_another_language_than_profile() throws Exception {
    QProfileDto javaProfile = createProfile("java");
    createRule(javaProfile.getLanguage(), "toto");
    createRule(javaProfile.getLanguage(), "tata");
    QProfileDto phpProfile = createProfile("php");
    createRule(phpProfile.getLanguage(), "hello");
    createRule(phpProfile.getLanguage(), "world");
    session.commit();

    // 1. Activate Rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ActivateRulesAction.ACTIVATE_RULES_ACTION);
    request.setParam(PARAM_PROFILE_KEY, javaProfile.getKee());
    request.setParam(PARAM_QPROFILE, javaProfile.getKee());
    request.setParam("activation", "false");
    request.execute().assertJson(getClass(), "does_not_return_warnings_when_bulk_activate_on_profile_and_rules_exist_on_another_language_than_profile.json");
    session.clearCache();

    // 2. Assert ActiveRule in DAO
    assertThat(db.activeRuleDao().selectByProfileUuid(session, javaProfile.getKee())).hasSize(2);
  }

  @Test
  public void reset() throws Exception {
    QProfileDto profile = QProfileTesting.newXooP1(organization);
    QProfileDto subProfile = QProfileTesting.newXooP2(organization).setParentKee(QProfileTesting.XOO_P1_KEY);
    db.qualityProfileDao().insert(session, profile, subProfile);

    RuleDefinitionDto rule = createRule(profile.getLanguage(), "rule");
    ActiveRuleDto active1 = ActiveRuleDto.createFor(profile, rule)
      .setSeverity(rule.getSeverityString());
    ActiveRuleDto active2 = ActiveRuleDto.createFor(subProfile, rule)
      .setSeverity("MINOR");
    db.activeRuleDao().insert(session, active1);
    db.activeRuleDao().insert(session, active2);

    session.commit();
    activeRuleIndexer.indexOnStartup(activeRuleIndexer.getIndexTypes());

    // 0. assert rule child rule is minor
    Optional<ActiveRuleDto> activeRuleDto = db.activeRuleDao().selectByKey(session, active2.getKey());
    assertThat(activeRuleDto.isPresent()).isTrue();
    assertThat(activeRuleDto.get().getSeverityString()).isEqualTo(Severity.MINOR);

    // 1. reset child rule
    WsTester.TestRequest request = wsTester.newPostRequest(QProfilesWs.API_ENDPOINT, ACTION_ACTIVATE_RULE);
    request.setParam("profile_key", subProfile.getKee());
    request.setParam("rule_key", rule.getKey().toString());
    request.setParam("reset", "true");
    request.execute();
    session.clearCache();

    // 2. assert rule child rule is NOT minor
    activeRuleDto = db.activeRuleDao().selectByKey(session, active2.getKey());
    assertThat(activeRuleDto.isPresent()).isTrue();
    assertThat(activeRuleDto.get().getSeverityString()).isNotEqualTo(Severity.MINOR);
  }

  private QProfileDto createProfile(String lang) {
    QProfileDto profile = QProfileTesting.newQProfileDto(organization, new QProfileName(lang, "P" + lang), "p" + lang);
    db.qualityProfileDao().insert(session, profile);
    return profile;
  }

  private RuleDefinitionDto createRule(String lang, String id) {
    RuleDefinitionDto rule = RuleTesting.newRule(RuleKey.of("blah", id))
      .setLanguage(lang)
      .setSeverity(Severity.BLOCKER)
      .setStatus(RuleStatus.READY);
    db.ruleDao().insert(session, rule);
    session.commit();
    ruleIndexer.indexRuleDefinition(rule.getKey());
    return rule;
  }

  private ActiveRuleDto createActiveRule(RuleDefinitionDto rule, QProfileDto profile) {
    ActiveRuleDto activeRule = ActiveRuleDto.createFor(profile, rule)
      .setSeverity(rule.getSeverityString());
    db.activeRuleDao().insert(session, activeRule);
    return activeRule;
  }
}
