package org.platformlayer.service.aptcache.ops;

import java.io.File;
import java.net.URI;

import javax.inject.Inject;

import org.platformlayer.core.model.ManagedItemState;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.filesystem.SimpleFile;
import org.platformlayer.ops.helpers.InstanceHelpers;
import org.platformlayer.ops.instances.InstanceBuilder;
import org.platformlayer.ops.networks.NetworkPoint;
import org.platformlayer.ops.networks.NetworkPoints;
import org.platformlayer.ops.networks.PublicEndpoint;
import org.platformlayer.ops.packages.PackageDependency;
import org.platformlayer.ops.proxy.HttpProxyController;
import org.platformlayer.ops.service.ManagedService;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.platformlayer.service.aptcache.model.AptCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AptCacheServiceController extends OpsTreeBase implements HttpProxyController {
	private static final Logger log = LoggerFactory.getLogger(AptCacheServiceController.class);

	public static final int PORT = 3128;

	@Inject
	InstanceHelpers instances;

	@Inject
	NetworkPoints network;

	@Handler
	public void doOperation() {
	}

	@Override
	protected void addChildren() throws OpsException {
		AptCacheService model = OpsContext.get().getInstance(AptCacheService.class);

		// TODO: Create endpoint with default port; maybe default to closed?
		// model.dnsName

		InstanceBuilder instance = InstanceBuilder.build(model.dnsName, this, model.getTags());
		instance.hostPolicy.allowRunInContainer = true;
		addChild(instance);

		instance.addChild(PackageDependency.build("squid3"));

		instance.addChild(SimpleFile.build(getClass(), new File("/etc/squid3/squid.conf")));

		instance.addChild(ManagedService.build("squid3"));

		// instance.addChild(CollectdCollector.build());

		{
			PublicEndpoint endpoint = injected(PublicEndpoint.class);
			endpoint.publicPort = PORT;
			endpoint.backendPort = PORT;
			endpoint.dnsName = model.dnsName;

			endpoint.tagItem = model.getKey();
			endpoint.parentItem = model.getKey();

			instance.addChild(endpoint);
		}
	}

	@Override
	public String getUrl(Object modelObject, NetworkPoint forNetworkPoint, URI uri) throws OpsException {
		AptCacheService model = (AptCacheService) modelObject;

		if (model.getState() != ManagedItemState.ACTIVE) {
			log.info("Cache not active; returning null URL");
			return null;
		}

		// {
		// // By DNS
		// String dnsName = aptCacheService.getDnsName();
		// String address = "http://" + dnsName + ":3128/";
		// proxies.add(address);
		// }
		//
		// {
		// By IP
		NetworkPoint networkPoint = network.findNetworkPoint(model);
		if (networkPoint == null) {
			log.info("Machine not found for cache; returning null URL");
			return null;
		}

		String address = "http://" + networkPoint.getBestAddress(forNetworkPoint) + ":3128/";
		return address;
	}
}
