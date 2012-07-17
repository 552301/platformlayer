package org.platformlayer.gwt.client.api.platformlayer;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface PlatformLayerService {
	void listRoots(OpsProject project, AsyncCallback<UntypedItemCollection> callback);

	void listJobs(OpsProject project, AsyncCallback<JobCollection> callback);

	void doAction(OpsProject project, String targetItem, Action action, AsyncCallback<Job> callback);

	void getJobLog(OpsProject project, String jobId, AsyncCallback<JobLog> callback);

	void getItem(OpsProject project, String key, AsyncCallback<UntypedItem> callback);
}