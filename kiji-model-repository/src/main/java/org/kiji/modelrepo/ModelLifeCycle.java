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

package org.kiji.modelrepo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kiji.express.avro.AvroModelDefinition;
import org.kiji.express.avro.AvroModelEnvironment;
import org.kiji.modelrepo.artifactvalidator.ArtifactValidator;
import org.kiji.modelrepo.artifactvalidator.WarArtifactValidator;
import org.kiji.schema.KijiRowData;
import org.kiji.schema.util.ProtocolVersion;

/**
 * Class to work with model artifact metadata sourced from the model repository table.
 */
public class ModelLifeCycle {

  public static final String DEFINITION_KEY = "definition";
  public static final String ENVIRONMENT_KEY = "environment";
  public static final String LOCATION_KEY = "location";
  public static final String MESSAGES_KEY = "message";
  public static final String PRODUCTION_READY_KEY = "production_ready";
  public static final String UPLOADED_KEY = "uploaded";
  public static final String MODEL_REPO_FAMILY = "model";

  private static final Logger LOG = LoggerFactory.getLogger(ModelLifeCycle.class);

  private final boolean mUploaded;
  private final ArtifactName mArtifact;
  private final String mLocation;
  private final AvroModelDefinition mDefinition;
  private final AvroModelEnvironment mEnvironment;
  private final Map<Long, String> mMessages = Maps.newTreeMap();
  private final Map<Long, Boolean> mProductionReady = Maps.newTreeMap();

  private final URI mBaseStorageURI;

  /**
   * Construct model lifecycle object from row data from the model repository table.
   *
   * @param model row data from the model repository table.
   * @throws IOException if the row data can not be properly accessed to retrieve values.
   */
  public ModelLifeCycle(final KijiRowData model) throws IOException {
    this(model, Sets.newHashSet(
        DEFINITION_KEY,
        ENVIRONMENT_KEY,
        LOCATION_KEY,
        MESSAGES_KEY,
        PRODUCTION_READY_KEY));
  }

  /**
   * Construct model lifecycle object from row data and required fields
   * from the model repository table. Proper construction of ModelArtifact
   * requires UPLOADED_KEY field set to true.
   *
   * @param model row data from the model repository table.
   * @param fieldsToRead read only these fields from the row data.
   * @param baseStorageURI is the base URI of the underlying storage layer.
   * @throws IOException if the row data can not be properly accessed to retrieve values.
   */
  public ModelLifeCycle(final KijiRowData model,
      final Set<String> fieldsToRead) throws IOException {

    // Load name and version.
    mArtifact = new ArtifactName(
        model.getEntityId().getComponentByIndex(0).toString(),
        ProtocolVersion.parse(model.getEntityId().getComponentByIndex(1).toString()));

    // Load name, definition, environment, and location.

    // Every row must have the uploaded field specified and it must be true.
    try {
      mUploaded = model.<Boolean>getMostRecentValue(MODEL_REPO_FAMILY, UPLOADED_KEY);
    } catch (final Exception e) {
      throw new IOException("Requested model could not be extracted.");
    }
    if (!mUploaded) {
      throw new IOException("Requested model was not uploaded, therefore not deployed.");
    }
    if (fieldsToRead.contains(DEFINITION_KEY)) {
      try {
        mDefinition =
            (AvroModelDefinition) model.getMostRecentValue(MODEL_REPO_FAMILY, DEFINITION_KEY);
      } catch (final Exception e) {
        throw new IOException("Following field was not extracted: " + DEFINITION_KEY);
      }
    } else {
      mDefinition = null;
    }
    if (fieldsToRead.contains(ENVIRONMENT_KEY)) {
      try {
        mEnvironment =
            (AvroModelEnvironment) model.getMostRecentValue(MODEL_REPO_FAMILY, ENVIRONMENT_KEY);
      } catch (final Exception e) {
        throw new IOException("Following field was not extracted: " + ENVIRONMENT_KEY);
      }
    } else {
      mEnvironment = null;
    }
    if (fieldsToRead.contains(LOCATION_KEY)) {
      try {
        mLocation = model.getMostRecentValue(MODEL_REPO_FAMILY, LOCATION_KEY).toString();
      } catch (final Exception e) {
        throw new IOException("Following field was not extracted: " + LOCATION_KEY);
      }
    } else {
      mLocation = null;
    }
    // Load production_ready flag.
    if (fieldsToRead.contains(PRODUCTION_READY_KEY)) {
      try {
        final NavigableMap<Long, Boolean> productionReadyValues =
            model.getValues(MODEL_REPO_FAMILY, PRODUCTION_READY_KEY);
        for (final Entry<Long, Boolean> cell : productionReadyValues.entrySet()) {
          mProductionReady.put(cell.getKey(), cell.getValue());
        }
      } catch (final Exception e) {
        throw new IOException("Following field was not extracted: " + PRODUCTION_READY_KEY);
      }
    }
    // Load messages.
    if (fieldsToRead.contains(MESSAGES_KEY)) {
      try {
        final NavigableMap<Long, CharSequence> messagesValues =
            model.getValues(MODEL_REPO_FAMILY, MESSAGES_KEY);
        for (final Entry<Long, CharSequence> cell : messagesValues.entrySet()) {
          mMessages.put(cell.getKey(), cell.getValue().toString());
        }
      } catch (final Exception e) {
        throw new IOException("Following field was not extracted: " + MESSAGES_KEY);
      }
    }
  }

  /**
   * Gets lifecycle artifact's name.
   *
   * @return artifact name.
   */
  public ArtifactName getArtifactName() {
    return mArtifact;
  }

  /**
   * Gets the location where the artifact package exists.
   *
   * @return artifact location.
   */
  public String getLocation() {
    return mLocation;
  }

  /**
   * Gets lifecycle's model definition.
   *
   * @return model definition.
   */
  public AvroModelDefinition getDefinition() {
    return mDefinition;
  }

  /**
   * Gets lifecycle's model environment.
   *
   * @return model environment.
   */
  public AvroModelEnvironment getEnvironment() {
    return mEnvironment;
  }

  /**
   * Gets a map of the messages associated with this lifecycle, keyed by timestamp.
   *
   * @return map of messages keyed by timestamp.
   */
  public Map<Long, String> getMessages() {
    return mMessages;
  }

  /**
   * Gets a map of the production_ready flags associated with this lifecycle, keyed by timestamp.
   *
   * @return map of production_ready flags keyed by timestamp.
   */
  public Map<Long, Boolean> getProductionReady() {
    return mProductionReady;
  }

  /**
   * Returns the canonical name of a model life cycle which is the concatenation of the
   * group + artifact + version. Specifically it's "group.artifact-version"
   *
   * @return the canonical name of a model life cycle which is the concatenation of the
   *         group + artifact + version. Specifically it's "group.artifact-version"
   */
  public String getFullyQualifiedModelName() {
    return getModelName(mGroupName, mArtifactName) + "-" + mArtifactVersion;
  }

  @Override
  public String toString() {
    final StringBuilder modelStringBuilder = new StringBuilder();
    modelStringBuilder.append(String.format("name: %s%n", mArtifact.getName()));
    modelStringBuilder.append(String.format("version: %s%n", mArtifact.getVersion()));

    // Print the fields which were requested by the user.
    if (null != getLocation()) {
      modelStringBuilder.append(String.format("%s: %s%n", "location", getLocation()));
    }
    if (null != getDefinition()) {
      modelStringBuilder.append(String.format("%s: %s%n", "definition", getDefinition()));
    }
    if (null != getEnvironment()) {
      modelStringBuilder.append(String.format("%s: %s%n", "environment", getEnvironment()));
    }

    // Merge the sorted lists of production_ready and message flags and pretty print.
    final Iterator<Entry<Long, Boolean>> productionReady =
        getProductionReady().entrySet().iterator();
    final Iterator<Entry<Long, String>> messages = getMessages().entrySet().iterator();
    Map.Entry<Long, Boolean> productionReadyEntry = null;
    Map.Entry<Long, String> messagesEntry = null;
    if (productionReady.hasNext()) {
      productionReadyEntry = productionReady.next();
    }
    if (messages.hasNext()) {
      messagesEntry = messages.next();
    }
    while (null != productionReadyEntry || null != messagesEntry) {
      if ((null == productionReadyEntry)
          || ((null != messagesEntry)
          && (productionReadyEntry.getKey().compareTo(messagesEntry.getKey()) >= 0))) {
        modelStringBuilder.append(String.format("%s [%d]: %s%n", "message",
            messagesEntry.getKey(),
            messagesEntry.getValue()));
        if (messages.hasNext()) {
          messagesEntry = messages.next();
        } else {
          messagesEntry = null;
        }
      } else {
        modelStringBuilder.append(String.format("%s [%d]: %s%n", "production_ready",
            productionReadyEntry.getKey(),
            productionReadyEntry.getValue()));
        if (productionReady.hasNext()) {
          productionReadyEntry = productionReady.next();
        } else {
          productionReadyEntry = null;
        }
      }
    }
    return modelStringBuilder.toString();
  }

  /**
   * Check that this model lifecycle is associated with a valid model location
   * in the model repository, i.e. that a valid model artifact is found at the model location.
   *
   * @param download set to true allows the method to download and validate the artifact file.
   *
   * @return List of exceptions of inconsistent model locations over time.
   * @throws IOException if a temporary file can not be allocated for downloading model artifacts.
   */
  public List<Exception> checkModelLocation(
      final boolean download) throws IOException {
    final List<Exception> issues = Lists.newArrayList();
    final URL location = mBaseStorageURI.resolve(this.getLocation()).toURL();
    // Currently only supports http and fs.
    // TODO: Support more protocols: ssh, etc.
    if (download) {
      final File artifactFile = File.createTempFile(this.getArtifactName().toString(), ".war");
      artifactFile.deleteOnExit();
      // Download artifact to artifactFile.
      if (downloadArtifact(artifactFile)) {
        // Parse artifactFile for validation.
        final ArtifactValidator artifactValidator = new WarArtifactValidator();
        if (!artifactValidator.isValid(artifactFile)) {
          issues.add(new ModelRepositoryConsistencyException(
              this.getArtifactName(),
              String.format("Artifact from %s is not a valid jar/war file.",
                  location.toString())));
        }
      } else {
        issues.add(new ModelRepositoryConsistencyException(
            this.getArtifactName(),
            String.format("Unable to retrieve artifact from %s.",
                location.toString())));
      }
      // Delete artifactFile as it's no use any more.
      artifactFile.delete();
    } else {
      InputStream artifactInputStream = null;
      try {
        artifactInputStream = location.openStream();
      } catch (Exception e) {
        issues.add(new ModelRepositoryConsistencyException(
            this.getArtifactName(),
            String.format("Unable to find artifact at %s.",
                location.toString())));
      } finally {
        if (null != artifactInputStream) {
          artifactInputStream.close();
        }
      }
    }
    return issues;
  }

  /**
   * Copies artifact from URL to file on disk.
   *
   * @param file target
   *
   * @return true iff copy happen unexceptionally
   * @throws IOException if there is a problem downloading the file.
   */
  public boolean downloadArtifact(final File file) throws IOException {
    final URI resolvedURI = URI.create(mBaseStorageURI.toString() + "/"
       + mLocation);
    final URL location = resolvedURI.toURL();

    LOG.info("Preparing to download model artifact to temporary location {}",
        file.getAbsolutePath());
    try {
      FileUtils.copyURLToFile(location, file);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  @Override
  public boolean equals(Object rhs) {
    if (rhs == null) {
      return false;
    } else {
      return (rhs instanceof ModelArtifact && ((ModelArtifact) rhs).getFullyQualifiedModelName()
          .equals(this.getFullyQualifiedModelName()));
    }
  }

  @Override
  public int hashCode() {
    return getFullyQualifiedModelName().hashCode();
  }

  /**
   * Returns the canonical name of a model life cycle given the group and artifact name. This
   * is here as the layout supports a single name field but other parts of the system support
   * the separate group/artifact names.
   *
   * @param groupName the group name of the lifecycle.
   * @param artifactName the artifact name of the lifecycle.
   * @return the canonical name of the model lifecycle suitable for storage in the Kiji table
   *         storing deployed lifecycles.
   */
  public static String getModelName(String groupName, String artifactName) {
    Preconditions.checkNotNull(groupName);
    Preconditions.checkNotNull(artifactName);
    Preconditions.checkArgument(!groupName.isEmpty(), "Group name must be nonempty string.");
    Preconditions.checkArgument(!artifactName.isEmpty(),
        "Artifact name must be nonempty string.");
    return (groupName + "." + artifactName).intern();
  }
}
