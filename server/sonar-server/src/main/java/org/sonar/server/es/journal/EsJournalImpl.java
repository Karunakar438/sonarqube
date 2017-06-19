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

package org.sonar.server.es.journal;

import java.util.Optional;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.journal.EsJournalDto;

public class EsJournalImpl implements EsJournal {

  private final DbClient dbClient;

  public EsJournalImpl(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  @Override
  public void checkin(DbSession dbSession, EsJournalDto esJournalDto) {
    dbClient.esJournalDao().insert(dbSession, esJournalDto);
  }

  @Override
  public Optional<EsJournalDto> checkout(DbSession dbSession) {
    return Optional.ofNullable(dbClient.esJournalDao().selectNextEntry(dbSession));
  }

  @Override
  public void release(DbSession dbSession, EsJournalDto esJournalDto) {
    dbClient.esJournalDao().delete(dbSession, esJournalDto.getUuid());
  }
}
