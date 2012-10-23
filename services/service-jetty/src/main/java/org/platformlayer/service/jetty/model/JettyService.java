package org.platformlayer.service.jetty.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.ops.firewall.Transport;
import org.platformlayer.service.jetty.ops.JettyServiceController;
import org.platformlayer.xaas.Controller;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@Controller(JettyServiceController.class)
public class JettyService extends ItemBase {
	public String dnsName;

	public List<JettyContext> contexts;

	public PlatformLayerKey sslKey;

	public Transport transport;
}
