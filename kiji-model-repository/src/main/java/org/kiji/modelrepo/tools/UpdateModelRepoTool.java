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

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import org.kiji.common.flags.Flag;
import org.kiji.modelrepo.ArtifactName;
import org.kiji.modelrepo.KijiModelRepository;
import org.kiji.modelrepo.ModelContainer;
import org.kiji.schema.KConstants;
import org.kiji.schema.Kiji;
import org.kiji.schema.KijiURI;
import org.kiji.schema.tools.BaseTool;

/**
 * Model repository consistency update tool set the production ready flag
 * and annotate models with messages.
 */
public final class UpdateModelRepoTool extends BaseTool implements KijiModelRepoTool {

  /** Artifact's name. **/
  private ArtifactName mArtifact = null;

  @Flag(name="kiji", usage="Name of the Kiji instance housing the model repository.")
  private String mInstanceName = KConstants.DEFAULT_INSTANCE_URI;

  @Flag(name="production-ready", usage="Set the production ready flag. Default: true")
  private boolean mProductionReady = true;

  @Flag(name="message", usage="Annotate the model with a message.")
  private String mMessage = null;

  private KijiURI mInstanceURI = null;

  @Override
  protected void validateFlags() throws Exception {
    super.validateFlags();
    mInstanceURI = KijiURI.newBuilder(mInstanceName).build();
  }

  @Override
  public String getName() {
    return MODEL_REPO_TOOL_BASE + getModelRepoToolName();
  }

  @Override
  public String getCategory() {
    return MODEL_REPO_TOOL_CATEGORY;
  }

  @Override
  public String getModelRepoToolName() {
    return "update";
  }

  @Override
  public String getDescription() {
    return "Updates the production ready flag and sets message for models in the model repository.";
  }

  @Override
  protected int run(List<String> args) throws Exception {
    Preconditions.checkArgument(args.size() == 1, "Must specify model with: "
        + "update <package>.<identifier>-<version> [--flags]");
    mArtifact = new ArtifactName(args.get(0));
    Preconditions.checkArgument(mArtifact.isVersionSpecified(),
        "Must specify version. E.g. org.myorg.myproject.artifact-1.2.3");


    final Kiji kijiInstance = Kiji.Factory.open(mInstanceURI);
    final KijiModelRepository kmr = KijiModelRepository.open(kijiInstance);
    try {
      kmr.setProductionReady(
          mArtifact,
          mArtifact.getVersion(),
          mProductionReady,
          mMessage);
      ModelContainer model = kmr.getModelLifeCycle(
          mArtifact,
          Sets.newHashSet(ModelContainer.PRODUCTION_READY_KEY, ModelContainer.MESSAGES_KEY));
      getPrintStream().println(model);
    } finally {
      kmr.close();
      kijiInstance.release();
    }
    return SUCCESS;
  }
}
