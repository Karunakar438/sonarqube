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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sonar.api.Startable;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.journal.EsJournalDto;
import org.sonar.server.user.index.UserIndexer;

public class BackgroundIndexer implements Startable {

  private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1,
    new ThreadFactoryBuilder()
      .setPriority(Thread.MIN_PRIORITY)
      .setNameFormat("BackgroundIndexer-%d")
      .build());
  private final EsJournal esJournal;
  private final DbClient dbClient;
  private final UserIndexer userIndexer;

  public BackgroundIndexer(EsJournal esJournal, DbClient dbClient, UserIndexer userIndexer) {
    this.esJournal = esJournal;
    this.dbClient = dbClient;
    this.userIndexer = userIndexer;
  }

  @Override
  public void start() {
    executorService.scheduleAtFixedRate(
      () -> index(),
      1,
      1,
      TimeUnit.SECONDS
    );
  }

  @Override
  public void stop() {
    executorService.shutdown();
  }

  protected void index() {
    try (DbSession dbSession = dbClient.openSession(false)) {
      Optional<EsJournalDto> esJournalDto = esJournal.checkout(dbSession);
      if (esJournalDto.isPresent()) {
        userIndexer.index(esJournalDto.get().getDocUuid());
        esJournal.release(dbSession, esJournalDto.get());
      }
    } catch (Exception ex) {
      System.out.println(ex);
      // Add counter to esJournalDto
    }
  }
}
