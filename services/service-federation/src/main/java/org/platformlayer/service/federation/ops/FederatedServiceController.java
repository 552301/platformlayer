package org.platformlayer.service.federation.ops;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.metrics.collectd.OpsTreeBase;

public class FederatedServiceController extends OpsTreeBase {
    static final Logger log = Logger.getLogger(FederatedServiceController.class);

    @Handler
    public void doOperation() throws OpsException, IOException {
    }

    @Override
    protected void addChildren() throws OpsException {

    }
}
