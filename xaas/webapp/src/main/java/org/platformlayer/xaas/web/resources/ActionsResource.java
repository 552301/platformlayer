package org.platformlayer.xaas.web.resources;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;

import org.platformlayer.EnumUtils;
import org.platformlayer.RepositoryException;
import org.platformlayer.core.model.Action;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.jobs.model.JobData;
import org.platformlayer.ops.OperationType;
import org.platformlayer.ops.tasks.JobRegistry;

import com.google.common.base.Strings;

public class ActionsResource extends XaasResourceBase {
	@Inject
	JobRegistry operations;

	@POST
	@Consumes({ XML, JSON })
	@Produces({ XML, JSON })
	public JobData doAction(Action action) throws RepositoryException {
		boolean fetchTags = true;
		ItemBase managedItem = getManagedItem(fetchTags);

		String actionName = action.getName();
		if (Strings.isNullOrEmpty(actionName)) {
			throw new IllegalArgumentException("Action is required");
		}
		OperationType operationType = EnumUtils.valueOfCaseInsensitive(OperationType.class, actionName);
		PlatformLayerKey itemKey = getPlatformLayerKey();
		PlatformLayerKey jobKey = operations.enqueueOperation(operationType, getProjectAuthorization(), itemKey);

		JobData jobData = new JobData();
		jobData.key = jobKey;
		return jobData;
	}
}
