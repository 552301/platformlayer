package org.platformlayer.ops.service;

import org.platformlayer.ops.Handler;
import org.platformlayer.ops.Injection;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;

public class ManagedService {
	String serviceKey;
	boolean enabled = true;

	@Handler
	public void doOperation(OpsTarget target) throws OpsException {
		if (OpsContext.isConfigure()) {
			if (enabled) {
				// TODO: Be much smarter here...
				target.executeCommand("service {0} restart", getServiceKey());
			} else {
				// TODO: Be much smarter here...
				target.executeCommand("service {0} stop", getServiceKey());
			}
		}
	}

	public static ManagedService build(String serviceKey) {
		ManagedService service = Injection.getInstance(ManagedService.class);
		service.setServiceKey(serviceKey);
		return service;
	}

	public String getServiceKey() {
		return serviceKey;
	}

	public void setServiceKey(String serviceKey) {
		this.serviceKey = serviceKey;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public ManagedService setEnabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

}
