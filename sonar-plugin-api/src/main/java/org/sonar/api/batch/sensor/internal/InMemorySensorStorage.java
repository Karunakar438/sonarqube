/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.api.batch.sensor.internal;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.sonar.api.batch.sensor.coverage.CoverageType;
import org.sonar.api.batch.sensor.coverage.internal.DefaultCoverage;
import org.sonar.api.batch.sensor.cpd.internal.DefaultCpdTokens;
import org.sonar.api.batch.sensor.highlighting.internal.DefaultHighlighting;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.batch.sensor.measure.Measure;
import org.sonar.api.batch.sensor.symbol.internal.DefaultSymbolTable;
import org.sonar.api.utils.SonarException;

class InMemorySensorStorage implements SensorStorage {

  Table<String, String, Measure> measuresByComponentAndMetric = HashBasedTable.create();

  Collection<Issue> allIssues = new ArrayList<>();

  Map<String, DefaultHighlighting> highlightingByComponent = new HashMap<>();
  Map<String, DefaultCpdTokens> cpdTokensByComponent = new HashMap<>();
  Table<String, CoverageType, DefaultCoverage> coverageByComponentAndType = HashBasedTable.create();
  Map<String, DefaultSymbolTable> symbolsPerComponent = new HashMap<>();

  @Override
  public void store(Measure measure) {
    // Emulate duplicate measure check
    String componentKey = measure.inputComponent().key();
    String metricKey = measure.metric().key();
    if (measuresByComponentAndMetric.contains(componentKey, metricKey)) {
      throw new SonarException("Can not add the same measure twice");
    }
    measuresByComponentAndMetric.row(componentKey).put(metricKey, measure);
  }

  @Override
  public void store(Issue issue) {
    allIssues.add(issue);
  }

  @Override
  public void store(DefaultHighlighting highlighting) {
    String fileKey = highlighting.inputFile().key();
    // Emulate duplicate storage check
    if (highlightingByComponent.containsKey(fileKey)) {
      throw new UnsupportedOperationException("Trying to save highlighting twice for the same file is not supported: " + highlighting.inputFile().relativePath());
    }
    highlightingByComponent.put(fileKey, highlighting);
  }

  @Override
  public void store(DefaultCoverage defaultCoverage) {
    String fileKey = defaultCoverage.inputFile().key();
    // Emulate duplicate storage check
    if (coverageByComponentAndType.contains(fileKey, defaultCoverage.type())) {
      throw new UnsupportedOperationException("Trying to save coverage twice for the same file is not supported: " + defaultCoverage.inputFile().relativePath());
    }
    coverageByComponentAndType.row(fileKey).put(defaultCoverage.type(), defaultCoverage);
  }

  @Override
  public void store(DefaultCpdTokens defaultCpdTokens) {
    String fileKey = defaultCpdTokens.inputFile().key();
    // Emulate duplicate storage check
    if (cpdTokensByComponent.containsKey(fileKey)) {
      throw new UnsupportedOperationException("Trying to save CPD tokens twice for the same file is not supported: " + defaultCpdTokens.inputFile().relativePath());
    }
    cpdTokensByComponent.put(fileKey, defaultCpdTokens);
  }

  @Override
  public void store(DefaultSymbolTable symbolTable) {
    String fileKey = symbolTable.inputFile().key();
    // Emulate duplicate storage check
    if (symbolsPerComponent.containsKey(fileKey)) {
      throw new UnsupportedOperationException("Trying to save symbol table twice for the same file is not supported: " + symbolTable.inputFile().relativePath());
    }
    symbolsPerComponent.put(fileKey, symbolTable);
  }

}
