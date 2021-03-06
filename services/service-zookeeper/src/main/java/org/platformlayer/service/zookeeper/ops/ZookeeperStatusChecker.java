package org.platformlayer.service.zookeeper.ops;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.platformlayer.EndpointChooser;
import org.platformlayer.core.model.EndpointInfo;
import org.platformlayer.ops.Deviations;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;
import org.platformlayer.service.zookeeper.model.ZookeeperServer;
import org.platformlayer.service.zookeeper.ops.ZookeeperUtils.ZookeeperResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.InetAddresses;

public class ZookeeperStatusChecker {

	private static final Logger log = LoggerFactory.getLogger(ZookeeperStatusChecker.class);

	@Handler
	public void handler(OpsTarget target, ZookeeperServer zookeeperServer) throws OpsException {
		if (OpsContext.isConfigure() || OpsContext.isValidate()) {
			int port = ZookeeperConstants.ZK_PUBLIC_PORT;
			List<EndpointInfo> endpoints = EndpointInfo.findEndpoints(zookeeperServer.getTags(), port);

			EndpointInfo endpoint = EndpointChooser.any().choose(endpoints);
			if (endpoint == null) {
				throw new OpsException("Cannot find endpoint for zookeeper");
			}

			InetSocketAddress socketAddress = endpoint.asSocketAddress();

			{
				ZookeeperResponse response;
				try {
					// IPV6 requires ipsec; use the IPV4 loopback instead
					socketAddress = new InetSocketAddress(InetAddresses.forString("127.0.0.1"), socketAddress.getPort());
					response = ZookeeperUtils.sendCommand(target, socketAddress, "ruok");

					Deviations.assertEquals("imok", response.getRaw(), "Zookeeper ruok status");
				} catch (OpsException e) {
					Deviations.fail("Unable to connect to zookeeper", e);
				}
			}

			{
				ZookeeperResponse response;
				try {
					response = ZookeeperUtils.sendCommand(target, socketAddress, "srvr");

					Map<String, String> responseMap = response.asMap();
					String mode = responseMap.get("Mode");

					Deviations.assertIn(Arrays.asList("follower", "leader"), mode, "Zookeeper mode");
				} catch (OpsException e) {
					Deviations.fail("Unable to connect to zookeeper", e);
				}
			}
		}
	}
}
