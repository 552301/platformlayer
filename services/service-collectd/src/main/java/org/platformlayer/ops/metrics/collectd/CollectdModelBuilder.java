package org.platformlayer.ops.metrics.collectd;

import java.net.InetAddress;
import java.util.Map;

import javax.inject.Inject;

import org.platformlayer.InetAddressChooser;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsSystem;
import org.platformlayer.ops.machines.PlatformLayerHelpers;
import org.platformlayer.ops.networks.NetworkPoint;
import org.platformlayer.ops.networks.NetworkPoints;
import org.platformlayer.ops.templates.TemplateDataSource;
import org.platformlayer.service.collectd.model.CollectdService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectdModelBuilder implements TemplateDataSource {
	static final Logger log = LoggerFactory.getLogger(CollectdModelBuilder.class);

	@Inject
	PlatformLayerHelpers platformLayer;

	@Inject
	OpsSystem opsSystem;

	@Inject
	NetworkPoints network;

	@Override
	public void buildTemplateModel(Map<String, Object> model) throws OpsException {
		model.put("collectdServer", getCollectdServer());
		model.put("collectdHostname", getCollectdHostKey());
	}

	private String getCollectdHostKey() {
		// TODO: Multiple machines per service
		ItemBase managed = OpsContext.get().getInstance(ItemBase.class);
		PlatformLayerKey modelKey = managed.getKey();
		return CollectdHelpers.toCollectdKey(modelKey);
	}

	@Deprecated
	public String getCollectdServer() throws OpsException {
		Iterable<CollectdService> collectdServices = platformLayer.listItems(CollectdService.class);
		for (CollectdService collectdService : collectdServices) {
			// TODO: Use DNS name when it works

			NetworkPoint target = network.getNetworkPoint(collectdService);

			if (target != null) {
				NetworkPoint targetNetworkPoint = NetworkPoint.forTargetInContext();
				InetAddress address = target.findBestAddress(targetNetworkPoint, InetAddressChooser.preferIpv6());
				if (address != null) {
					return address.getHostAddress();
				}
			}
		}

		log.warn("Unable to find collectd server; defaulting to 127.0.0.1");
		return "127.0.0.1";
	}
}
