package org.platformlayer.xaas.services;

import org.platformlayer.core.model.ManagedItemState;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.jobs.model.JobExecutionData;
import org.platformlayer.model.ProjectAuthorization;
import org.platformlayer.ops.OpsException;

public interface ChangeQueue {
	JobExecutionData notifyChange(ProjectAuthorization auth, PlatformLayerKey itemKey, ManagedItemState newState)
			throws OpsException;
}
