package org.platformlayer.service.platformlayer.ops.backend;

import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.instances.InstanceBuilder;
import org.platformlayer.ops.networks.PublicEndpoint;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.platformlayer.service.platformlayer.model.PlatformLayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformLayerServiceController extends OpsTreeBase {

	private static final Logger log = LoggerFactory.getLogger(PlatformLayerServiceController.class);

	public static final int PORT = 8082;

	@Handler
	public void handler() {
	}

	@Override
	protected void addChildren() throws OpsException {
		PlatformLayerInstanceModel template = injected(PlatformLayerInstanceModel.class);
		PlatformLayerService model = template.getModel();

		int port = PORT;

		String dnsName = model.dnsName;

		InstanceBuilder vm;
		{
			vm = InstanceBuilder.build(dnsName, this);
			vm.publicPorts.add(port);
			vm.hostPolicy.configureCluster(template.getPlacementKey());

			// TODO: This needs to be configurable (?)
			vm.minimumMemoryMb = 2048;

			addChild(vm);
		}

		{
			PlatformLayerInstall install = injected(PlatformLayerInstall.class);
			vm.addChild(install);
		}

		{
			PlatformLayerInstance service = injected(PlatformLayerInstance.class);
			vm.addChild(service);
		}

		{
			PublicEndpoint endpoint = injected(PublicEndpoint.class);
			// endpoint.network = null;
			endpoint.publicPort = port;
			endpoint.backendPort = port;
			endpoint.dnsName = dnsName;

			endpoint.tagItem = model.getKey();
			endpoint.parentItem = model.getKey();

			vm.addChild(endpoint);
		}
	}
}
