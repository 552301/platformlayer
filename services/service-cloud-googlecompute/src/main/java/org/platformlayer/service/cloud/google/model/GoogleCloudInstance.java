package org.platformlayer.service.cloud.google.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.platformlayer.core.model.InstanceBase;
import org.platformlayer.service.cloud.google.ops.GoogleCloudInstanceController;
import org.platformlayer.xaas.Controller;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@Controller(GoogleCloudInstanceController.class)
public class GoogleCloudInstance extends InstanceBase {
	public String hostname;
	public int minimumMemoryMb;
}
