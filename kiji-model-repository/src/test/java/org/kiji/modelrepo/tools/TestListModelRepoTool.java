/**
 * (c) Copyright 2013 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.modelrepo.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;

import org.kiji.modelrepo.KijiModelRepository;
import org.kiji.modelrepo.ModelContainer;
import org.kiji.modelrepo.avro.KijiModelContainer;
import org.kiji.schema.EntityId;
import org.kiji.schema.Kiji;
import org.kiji.schema.KijiTable;
import org.kiji.schema.KijiTableWriter;
import org.kiji.schema.tools.BaseTool;
import org.kiji.schema.tools.KijiToolTest;
import org.kiji.schema.util.FromJson;

public class TestListModelRepoTool extends KijiToolTest {

  private Kiji mKiji = null;
  private File mTempDir = null;

  @Before
  public void setupModelRepo() throws Exception {
    mKiji = createTestKiji();
    mTempDir = Files.createTempDir();
    mTempDir.deleteOnExit();
    KijiModelRepository.install(mKiji, mTempDir.toURI());
    // Manually, set up model repository table.
    final KijiTable table = mKiji.openTable(KijiModelRepository.MODEL_REPO_TABLE_NAME);
    final KijiTableWriter writer = table.openTableWriter();
    final EntityId linRegScore = table.getEntityId("org.kiji.fake.linregscore", "1.0.0");
    final EntityId linRegScoreNew = table.getEntityId("org.kiji.fake.linregscore", "1.1.0");
    final EntityId recommend = table.getEntityId("org.kiji.fake.recommend", "1.0.0");
    final EntityId fraudScore = table.getEntityId("org.kiji.fake.fraudscore", "1.0.0");
    final EntityId badModel = table.getEntityId("org.kiji.fake.badmodel", "1.0.0");
    final EntityId incompleteModel = table.getEntityId("org.kiji.fake.incompletemodel", "1.0.0");

    // Write locations
    writer.put(linRegScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.LOCATION_KEY, 1L, "nonexistent.jar");
    writer.put(linRegScoreNew, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.LOCATION_KEY, 2L, "nonexistent.jar");
    writer.put(recommend, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.LOCATION_KEY, 3L, "nonexistent.jar");
    writer.put(fraudScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.LOCATION_KEY, 4L, "nonexistent.jar");
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.LOCATION_KEY, 4L, "nonexistent.jar");
    writer.put(incompleteModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.LOCATION_KEY, 4L, "nonexistent.jar");

    // Set uploaded flags.
    // The ModelArtifact.UPLOADED_KEY flags must persist at every entry from the birth of a record.
    writer.put(linRegScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.UPLOADED_KEY, 1L, true);
    writer.put(linRegScoreNew, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.UPLOADED_KEY, 2L, true);
    writer.put(recommend, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.UPLOADED_KEY, 4L, true);
    writer.put(fraudScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.UPLOADED_KEY, 4L, true);
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.UPLOADED_KEY, 4L, true);
    writer.put(incompleteModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.UPLOADED_KEY, 4L, false);

    // Read fake test definition and write to all the entries.
    final File definitionFile =
        new File("src/test/resources/org/kiji/samplelifecycle/model_container.json");
    final BufferedReader definitionReader = new BufferedReader(new FileReader(definitionFile));
    String line;
    String definitionJson = "";
    while ((line = definitionReader.readLine()) != null) {
      definitionJson += line;
    }
    definitionReader.close();
    final KijiModelContainer modelDefinition = (KijiModelContainer)
        FromJson.fromJsonString(definitionJson, KijiModelContainer.getClassSchema());
    writer.put(linRegScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MODEL_CONTAINER_KEY, 1L, modelDefinition);
    writer.put(linRegScoreNew, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MODEL_CONTAINER_KEY, 2L, modelDefinition);
    writer.put(recommend, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MODEL_CONTAINER_KEY, 3L, modelDefinition);
    writer.put(fraudScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MODEL_CONTAINER_KEY, 4L, modelDefinition);
    // Give incomplete model a definition since every model must have a definition.
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MODEL_CONTAINER_KEY, 5L, modelDefinition);
    writer.put(incompleteModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MODEL_CONTAINER_KEY, 5L, modelDefinition);

    // Write a few messages and production_ready flags.
    writer.put(linRegScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 1L, "Website uses linear regression scorer.");
    writer.put(linRegScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 1L, true);
    writer.put(linRegScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 2L, "Turned off old linear regression scorer.");
    writer.put(linRegScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 2L, false);
    writer.put(linRegScoreNew, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 2L, "Linear regression scorer was updated.");
    writer.put(linRegScoreNew, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 2L, true);
    writer.put(linRegScoreNew, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 3L, "Linear regression is outdated");
    writer.put(linRegScoreNew, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 3L, false);
    writer.put(recommend, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 3L, "Brand new recommendation engine online");
    writer.put(recommend, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 3L, true);
    writer.put(fraudScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 4L, "FraudScore implemented.");
    writer.put(fraudScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 4L, true);
    writer.put(fraudScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 4000L, "FraudScore functioned during cyberattack.");
    writer.put(fraudScore, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 8000L, "FraudScore failed to detect new attacks.");
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 5L, "Should never be implemented.");
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 5L, false);
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 6L, true);
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 7L, false);
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 8L, "Random comment.");
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 9L, "Random comment.");
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 10L, true);
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 11L, false);
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 11L, "Random comment.");
    writer.put(badModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 12L, "Random comment.");
    writer.put(incompleteModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.PRODUCTION_READY_KEY, 11L, false);
    writer.put(incompleteModel, ModelContainer.MODEL_REPO_FAMILY,
        ModelContainer.MESSAGES_KEY, 11L, "Random comment.");

    // Close table.
    writer.close();
    table.release();
  }

  @Test
  public void testShouldListOnlyModelEntryNamesAndVersions() throws Exception {
    // Run list model repository.
    final int status = runTool(new ListModelRepoTool(),
        new String[] { "--kiji=" + mKiji.getURI().toString(), });
    assertEquals(BaseTool.SUCCESS, status);
    // Check 19 lines corresponding to 4 lines per entry * 4 entries + 3 lines for last entry.
    assertEquals(4 * 3 + 2, mToolOutputLines.length);
    // Even includes the "badmodel" artifact which had only a model definition and location.
    assertTrue(mToolOutputStr.contains("badmodel"));
  }

  @Test
  public void testShouldListEveryField() throws Exception {
    // Run list model repository.
    final int status = runTool(new ListModelRepoTool(),
        new String[] { "--kiji=" + mKiji.getURI().toString(),
            "--definition", "--environment", "--location", "--message", "--production-ready", });
    assertEquals(BaseTool.SUCCESS, status);
    // 8 lines for 3 models, 7 lines for last model and 7 lines for badmodel
    assertEquals(8 * 3 + 7 + 7, mToolOutputLines.length);
    // Even includes the "badmodel" artifact which had only a model definition and location.
    assertTrue(mToolOutputStr.contains("badmodel"));
    // Includes all the field names appropriate number of times.
    assertEquals(6, mToolOutputStr.split("definition:").length);
    assertEquals(5, mToolOutputStr.split("environment:").length);
    assertEquals(6, mToolOutputStr.split("location:").length);
    assertEquals(6, mToolOutputStr.split("message ").length);
    assertEquals(6, mToolOutputStr.split("production_ready ").length);
  }

  @Test
  public void testShouldListMessagesAndProductionReadyFlagsSortedByTimestamp() throws Exception {
    // Run list model repository.
    final int status = runTool(new ListModelRepoTool(),
        new String[] { "--kiji=" + mKiji.getURI().toString(),
            "--artifact=org.kiji.fake.badmodel",
            "--max-versions=all",
            "--message",
            "--production-ready", });
    assertEquals(BaseTool.SUCCESS, status);
    final String[] timestampPrefixedLines = mToolOutputStr.split("\\[");
    long currTimestamp = 0;
    for (int i = 1; i < timestampPrefixedLines.length; i++) {
      long nextTimestamp = Long.parseLong(timestampPrefixedLines[i].split("\\]")[0]);
      assertTrue(currTimestamp <= nextTimestamp);
      currTimestamp = nextTimestamp;
    }
  }

  @Test
  public void testShouldRetrieveBasedOnLimitedVersions() throws Exception {
    // Run list model repository.
    final int status = runTool(new ListModelRepoTool(),
        new String[] { "--kiji=" + mKiji.getURI().toString(),
            "--artifact=org.kiji.fake.badmodel", "--max-versions=3", "--production-ready", });
    assertEquals(BaseTool.SUCCESS, status);
    // 2 lines for artifact/version, and 3 lines for 3 versions of production_ready flag.
    assertEquals(5, mToolOutputLines.length);
  }

  @Test
  public void testShouldRetrieveBasedOnVersion() throws Exception {
    // Run list model repository.
    final int status = runTool(new ListModelRepoTool(),
        new String[] { "--kiji=" + mKiji.getURI().toString(),
            "--artifact=org.kiji.fake.linregscore", "--version=1.0.0", });
    assertEquals(BaseTool.SUCCESS, status);
    // 2 lines for artifact/version
    assertEquals(2, mToolOutputLines.length);
  }

  @Test
  public void testShouldFailToRetrieveOnUnknownVersion() throws Exception {
    // Run list model repository.
    final int status = runTool(new ListModelRepoTool(),
        new String[] { "--kiji=" + mKiji.getURI().toString(),
            "--artifact=org.kiji.fake.linregscore", "--version=2.0.0", });
    System.out.println(mToolOutputStr);
    assertEquals(BaseTool.SUCCESS, status);
    assertEquals(0, mToolOutputStr.length());
  }
}
