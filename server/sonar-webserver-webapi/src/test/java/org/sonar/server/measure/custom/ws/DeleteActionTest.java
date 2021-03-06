/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonar.server.measure.custom.ws;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.WebService.Action;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.custom.CustomMeasureDto;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;

import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.db.measure.custom.CustomMeasureTesting.newCustomMeasureDto;
import static org.sonar.server.measure.custom.ws.DeleteAction.PARAM_ID;

public class DeleteActionTest {

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  private DbClient dbClient = db.getDbClient();
  private final DbSession dbSession = db.getSession();
  private DeleteAction underTest = new DeleteAction(dbClient, userSession);
  private WsActionTester ws = new WsActionTester(underTest);

  @Test
  public void verify_definition() {
    Action wsDef = ws.getDef();

    assertThat(wsDef.deprecatedSince()).isEqualTo("7.4");
    assertThat(wsDef.isInternal()).isFalse();
    assertThat(wsDef.since()).isEqualTo("5.2");
    assertThat(wsDef.isPost()).isTrue();
    assertThat(wsDef.changelog()).extracting(Change::getVersion, Change::getDescription).containsOnly(
      tuple("8.4", "Param 'id' data type changes from integer to string."));
  }

  @Test
  public void project_administrator_can_delete_custom_measures() {
    ComponentDto project = db.components().insertPrivateProject();
    userSession.logIn().addProjectPermission(UserRole.ADMIN, project);
    String id = insertCustomMeasure(project);

    ws.newRequest().setParam(PARAM_ID, valueOf(id)).execute();

    assertThat(dbClient.customMeasureDao().selectByUuid(dbSession, id)).isEmpty();
  }

  @Test
  public void throw_RowNotFoundException_if_id_does_not_exist() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Custom measure with id '42' does not exist");

    ws.newRequest().setParam(PARAM_ID, "42").execute();
  }

  @Test
  public void throw_ForbiddenException_if_not_system_administrator() {
    ComponentDto project = db.components().insertPrivateProject();
    String uuid = insertCustomMeasure(project);
    userSession.logIn().setNonSystemAdministrator();

    expectedException.expect(ForbiddenException.class);
    ws.newRequest().setParam(PARAM_ID, valueOf(uuid)).execute();
  }

  @Test
  public void throw_UnauthorizedException_if_not_logged_in() {
    ComponentDto project = db.components().insertPrivateProject();
    String uuid = insertCustomMeasure(project);
    userSession.anonymous();

    expectedException.expect(UnauthorizedException.class);
    ws.newRequest().setParam(PARAM_ID, valueOf(uuid)).execute();
  }

  private String insertCustomMeasure(ComponentDto component) {
    CustomMeasureDto dto = newCustomMeasureDto().setComponentUuid(component.uuid());
    dbClient.customMeasureDao().insert(dbSession, dto);
    dbSession.commit();
    return dto.getUuid();
  }

}
