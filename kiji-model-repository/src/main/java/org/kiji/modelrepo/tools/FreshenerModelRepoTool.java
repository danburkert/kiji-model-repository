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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;

import org.kiji.common.flags.Flag;
import org.kiji.modelrepo.ArtifactName;
import org.kiji.modelrepo.KijiModelRepository;
import org.kiji.modelrepo.ModelContainer;
import org.kiji.schema.Kiji;
import org.kiji.schema.KijiURI;
import org.kiji.schema.tools.BaseTool;

/**
 * Tool for attaching models stored in the model repository as Fresheners fulfilled by the Kiji
 * ScoringServer.
 */
public class FreshenerModelRepoTool extends BaseTool implements KijiModelRepoTool {

  // Positional arguments for KijiURI of the instance which holds the model repository, the model
  // name-version, and the fully qualified class name of the KijiFreshnessPolicy.

  @Flag(name="parameters", usage="JSON encoded map of Freshener parameters. (default={})")
  private String mParametersFlag = null;

  @Flag(name="overwrite-existing", usage="Specify to overwrite an existing Freshener attached to "
      + "the output column specified in the model environment. (default=false)")
  private Boolean mOverwriteFlag = null;

  @Flag(name="instantiate-classes", usage="Specify to instantiate the freshness policy during "
      + "registration so that its serializeToParameters method can be included in registered "
      + "parameters. (requires class available on the classpath) (default=false)")
  private Boolean mInstantiateFlag = null;

  @Flag(name="setup-classes", usage="Specify to call the freshness policy's setup method before "
      + "calling its serializeToParameters method. Context for setup will be created using other "
      + "parameters given here. (requires --instantiate-classes) (default=false)")
  private Boolean mSetupFlag = null;

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return "fresh-model";
  }

  /** {@inheritDoc} */
  @Override
  public String getModelRepoToolName() {
    return "fresh";
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return "Attach models as KijiScoring Fresheners.";
  }

  /** {@inheritDoc} */
  @Override
  public String getCategory() {
    return "Metadata";
  }

  private static final Gson GSON = new Gson();

  /**
   * Deserialize a string-string map from JSON.
   *
   * @param serializedMap JSON serialized map of parameters.
   * @return Java Map deserialized from input JSON.
   */
  private static Map<String, String> mapFromJSON(
      final String serializedMap
  ) {
    return GSON.fromJson(serializedMap, Map.class);
  }

  private KijiURI mURI = null;
  private ArtifactName mArtifactName = null;

  /** {@inheritDoc} */
  @Override
  protected void validateFlags() {
    if (null != mSetupFlag && mSetupFlag) {
      Preconditions.checkArgument(null != mInstantiateFlag && mInstantiateFlag, "--setup-classes "
          + "requires --instantiate-classes.");
    }
  }

  /**
   * Validate that non-flag args are specified correctly.
   *
   * @param nonFlagArgs arguments without --flag=.
   */
  private void validateNonFlagArgs(
      final List<String> nonFlagArgs
  ) {
    Preconditions.checkArgument(3 == nonFlagArgs.size(), "fresh-model has three required positional"
        + " arguments: <KijiURI of the instance housing the model repo> <model name and value> "
        + "<fully qualified class name of a KijiFreshnessPolicy>");
    mURI = KijiURI.newBuilder(nonFlagArgs.get(0)).build();
    mArtifactName = new ArtifactName(nonFlagArgs.get(1));
    Preconditions.checkNotNull(mArtifactName.getName(), "Artifact name must be specified using "
        + "--model.");
    Preconditions.checkNotNull(mArtifactName.getVersion(), "Artifact version must be specified "
        + "using --model.");
  }

  /** {@inheritDoc} */
  @Override
  protected int run(final List<String> nonFlagArgs) throws Exception {
    validateNonFlagArgs(nonFlagArgs);
    final String policyClassName = nonFlagArgs.get(2);
    final Kiji kiji = Kiji.Factory.open(mURI);
    try {
      final KijiModelRepository repo = KijiModelRepository.open(kiji);
      try {
        final boolean override = (null != mOverwriteFlag) ? mOverwriteFlag : false;
        final boolean instantiate = (null != mInstantiateFlag) ? mInstantiateFlag : false;
        final boolean setup = (null != mSetupFlag) ? mSetupFlag : false;
        final Map<String, String> parameters = (null != mParametersFlag)
            ? mapFromJSON(mParametersFlag) : Collections.<String, String>emptyMap();
        final ModelContainer lifeCycle = repo.getModelLifeCycle(mArtifactName);
        lifeCycle.attachAsRemoteFreshener(
            kiji, policyClassName, parameters, override, instantiate, setup);
        getPrintStream().printf("Freshener attached to column: %s with policy: %s and model: %s",
            // TODO: ajprax - verify that this column name is correct
            lifeCycle.getModelContainer().getColumnName(),
            policyClassName,
            mArtifactName.getFullyQualifiedName());
        return SUCCESS;
      } finally {
        repo.close();
      }
    } finally {
      kiji.release();
    }
  }
}
