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
package org.sonar.server.organization.ws;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.permission.OrganizationPermission.ADMINISTER;
import static org.sonar.server.organization.ws.OrganizationsWsSupport.PARAM_ORGANIZATION;
import static org.sonar.server.organization.ws.UpdateProjectVisibilityAction.ACTION;
import static org.sonar.server.organization.ws.UpdateProjectVisibilityAction.PARAM_PROJECT_VISIBILITY;

public class UpdateProjectVisibilityActionTest {
  @Rule
  public DbTester dbTester = DbTester.create();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private UpdateProjectVisibilityAction underTest = new UpdateProjectVisibilityAction(userSession, dbTester.getDbClient());
  private WsActionTester wsTester = new WsActionTester(underTest);

  @Test
  public void verify_define() {
    WebService.Action action = wsTester.getDef();
    assertThat(action.key()).isEqualTo(ACTION);
    assertThat(action.isPost()).isTrue();
    assertThat(action.description()).isNotEmpty();
    assertThat(action.isInternal()).isTrue();
    assertThat(action.since()).isEqualTo("6.4");
    assertThat(action.handler()).isEqualTo(underTest);
    assertThat(action.changelog()).isEmpty();

    WebService.Param organization = action.param(PARAM_ORGANIZATION);
    assertThat(organization.isRequired()).isTrue();
    assertThat(organization.exampleValue()).isEqualTo("foo-company");
    assertThat(organization.description()).isEqualTo("Organization key");

    WebService.Param projectVisibility = action.param(PARAM_PROJECT_VISIBILITY);
    assertThat(projectVisibility.isRequired()).isTrue();
    assertThat(projectVisibility.possibleValues()).containsExactlyInAnyOrder("private", "public");
    assertThat(projectVisibility.description()).isEqualTo("Default visibility for projects");
  }

  @Test
  public void should_change_project_visibility_to_private() {
    OrganizationDto organization = dbTester.organizations().insert();
    dbTester.organizations().setNewProjectPrivate(organization, false);
    userSession.logIn().addPermission(ADMINISTER, organization);

    wsTester.newRequest()
      .setParam(PARAM_ORGANIZATION, organization.getKey())
      .setParam(PARAM_PROJECT_VISIBILITY, "private")
      .execute();

    assertThat(dbTester.getDbClient().organizationDao().getNewProjectPrivate(dbTester.getSession(), organization)).isTrue();
  }

  @Test
  public void should_change_project_visibility_to_public() {
    OrganizationDto organization = dbTester.organizations().insert();
    dbTester.organizations().setNewProjectPrivate(organization, true);
    userSession.logIn().addPermission(ADMINISTER, organization);

    wsTester.newRequest()
      .setParam(PARAM_ORGANIZATION, organization.getKey())
      .setParam(PARAM_PROJECT_VISIBILITY, "public")
      .execute();
    dbTester.organizations().getNewProjectPrivate(organization);

    assertThat(dbTester.organizations().getNewProjectPrivate(organization)).isFalse();
  }

  @Test
  public void should_fail_if_organization_does_not_exist() {
    TestRequest request = wsTester.newRequest()
      .setParam(PARAM_ORGANIZATION, "does not exist")
      .setParam(PARAM_PROJECT_VISIBILITY, "private");

    expectedException.expect(NotFoundException.class);
    request.execute();
  }

  @Test
  public void should_fail_if_permissions_are_missing() {
    OrganizationDto organization = dbTester.organizations().insert();
    TestRequest request = wsTester.newRequest()
      .setParam(PARAM_ORGANIZATION, organization.getKey())
      .setParam(PARAM_PROJECT_VISIBILITY, "private");

    expectedException.expect(ForbiddenException.class);
    request.execute();
  }

}