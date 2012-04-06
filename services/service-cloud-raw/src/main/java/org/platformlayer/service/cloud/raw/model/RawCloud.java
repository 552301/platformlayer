package org.platformlayer.service.cloud.raw.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.platformlayer.core.model.MachineCloudBase;
import org.platformlayer.service.cloud.raw.ops.RawCloudController;
import org.platformlayer.xaas.Controller;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@Controller(RawCloudController.class)
public class RawCloud extends MachineCloudBase {
}
