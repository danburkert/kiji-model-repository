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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import org.apache.commons.io.IOUtils;

import org.kiji.common.flags.Flag;
import org.kiji.modelrepo.ArtifactName;
import org.kiji.modelrepo.KijiModelRepository;
import org.kiji.modelrepo.avro.KijiModelContainer;
import org.kiji.modelrepo.depresolver.DependencyResolver;
import org.kiji.modelrepo.depresolver.DependencyResolverFactory;
import org.kiji.schema.KConstants;
import org.kiji.schema.Kiji;
import org.kiji.schema.KijiURI;
import org.kiji.schema.tools.BaseTool;
import org.kiji.schema.util.FromJson;

/**
 * The deploy tool uploads the model lifecycle and coordinates to the model repository.
 */
public class DeployModelRepoTool extends BaseTool implements KijiModelRepoTool {

  /** Name of artifact to upload. */
  private ArtifactName mArtifact = null;

  /** Name of source artifact. */
  private ArtifactName mSourceArtifact = null;

  /** Artifact's fully qualified path. **/
  private File mArtifactPath = null;

  @Flag(name="kiji", usage="Kiji instance housing the model repository.")
  private String mInstanceURIFlag = KConstants.DEFAULT_INSTANCE_URI;

  private KijiURI mInstanceURI = null;

  @Flag(name="definition", usage="Path to model definition.")
  private String mDefinitionFlag = null;

  @Flag(name="environment", usage="Path to model environment.")
  private String mEnvironmentFlag = null;

  @Flag(name="production-ready", usage="Is the model lifecycle production ready.")
  private boolean mProductionReady = false;

  @Flag(name="message", usage="Update message for this deployment.")
  private String mMessage = null;

  @Flag(name="existing-artifact",
      usage="If specifying package location with existing artifact name.")
  private boolean mExistingArtifact = false;

  @Flag(name="deps", usage="Optional third-party dependencies to include "
      + "in the final deployed artifact.")
  private String mDeps = null;

  @Flag(name="deps-resolver", usage="(raw|maven). raw means whatever the --deps flag "
      + "specifies will be included in the artifact. maven means use --deps as the pom.xml "
      + "and resolve dependencies accordingly.")
  private String mDepsResolver = "raw";

  private DependencyResolver mResolver = null;

  /** {@inheritDoc} */
  @Override
  public String getCategory() {
    return MODEL_REPO_TOOL_CATEGORY;
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Upload a new version of a lifecycle and/or code artifact.";
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return MODEL_REPO_TOOL_BASE + getModelRepoToolName();
  }

  /** {@inheritDoc} */
  @Override
  public String getModelRepoToolName() {
    return "deploy";
  }

  /** {@inheritDoc} */
  @Override
  protected void validateFlags() throws Exception {
    Preconditions.checkNotNull(mDefinitionFlag,
        "Specify a model definition with --definition=modeldefinition.json ");
    Preconditions.checkNotNull(mEnvironmentFlag,
        "Specify a model environment with --environment=modelenv.json");
    Preconditions.checkNotNull(mMessage,
        "Specify an update message for this deployment with --message=updatemessage");

    mInstanceURI = KijiURI.newBuilder(mInstanceURIFlag).build();
    mResolver = DependencyResolverFactory.getResolver(mDepsResolver);
    Preconditions.checkNotNull(mResolver, "Unknown resolver %s", mDepsResolver);
  }

  /** {@inheritDoc} */
  @Override
  protected int run(List<String> args) throws Exception {
    // Validate that there's a maven artifact specified.
    Preconditions.checkArgument(args.size() == 2, "Usage: deploy <package>."
        + "<identifier>[-<version>] <path to artifact to upload>"
        + "%n(e.g. com.mycompany.myorg.my_model-1.0.0 my-model.jar)"
        + "%nor%n"
        + "deploy <package>."
        + "<identifier>[-<version>] <source artifact package>."
        + "<source artifact identifier>[-<source artifact version>]"
        + "%n(e.g. com.mycompany.myorg.my_model-1.0.0 com.mycompany.myorg.existing_model-1.0.0)");

    mArtifact = new ArtifactName(args.get(0));

    List<File> resolvedDeps = null;

    if (mExistingArtifact) {
      mSourceArtifact = new ArtifactName(args.get(1));
    } else {
      mArtifactPath = new File(args.get(1));
      Preconditions.checkArgument(mArtifactPath.exists(), "Error: %s does not exist", args.get(1));
      resolvedDeps = Lists.newArrayList();
      if (mDeps != null) {
        resolvedDeps = mResolver.resolveDependencies(mDeps);
      }
    }

    final Kiji kiji = Kiji.Factory.open(mInstanceURI);
    final KijiModelRepository kijiModelRepository = KijiModelRepository.open(kiji);

    final KijiModelContainer modelContainer = readKijiModelContainer(mDefinitionFlag);

    try {
      if (mExistingArtifact) {
        kijiModelRepository.deployModelLifecycle(
            mArtifact,
            mSourceArtifact,
            modelContainer,
            mProductionReady,
            mMessage);
      } else {
        kijiModelRepository.deployModelLifecycle(
            mArtifact,
            mArtifactPath,
            resolvedDeps,
            modelContainer,
            mProductionReady,
            mMessage);
      }
    } finally {
      kijiModelRepository.close();
      kiji.release();
    }
    return SUCCESS;
  }

  /**
   * Reads an AvroModelDefinition from the specified file.
   *
   * @param filename containing the AvroModelDefinition.
   * @return AvroModelDefinition parsed from the file.
   *
   * @throws IOException if there is an error reading the definition from the specified file.
   */
  private static KijiModelContainer readKijiModelContainer(String filename) throws IOException {
    String json = readJSONFromFile(filename);
    return (KijiModelContainer) FromJson.fromJsonString(json, KijiModelContainer.getClassSchema());
  }

  /**
   * Reads JSON from a file as a single string. Doesn't actually have to be a proper JSON, but
   * this is a helper method for the other classes.
   *
   * @param filename to read from
   * @return String containing the JSON contents of the file.
   * @throws IOException if there is a problem reading JSON from the specified file.
   */
  private static String readJSONFromFile(String filename) throws IOException {
    FileInputStream fis = new FileInputStream(filename);
    return IOUtils.toString(fis);
  }
}
