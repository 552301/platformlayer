package org.platformlayer.service.federation.ops;

import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FederatedServiceMapController extends OpsTreeBase {
	private static final Logger log = LoggerFactory.getLogger(FederatedServiceMapController.class);

	@Handler
	public void doOperation() {
	}

	@Override
	protected void addChildren() throws OpsException {

	}
}
