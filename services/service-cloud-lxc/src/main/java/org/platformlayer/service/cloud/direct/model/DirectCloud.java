package org.platformlayer.service.cloud.direct.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.service.cloud.direct.ops.DirectCloudController;
import org.platformlayer.xaas.Controller;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@Controller(DirectCloudController.class)
public class DirectCloud extends ItemBase {
	public PlatformLayerKey network;
}
