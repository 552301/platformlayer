package org.platformlayer.auth.model;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ProjectValidation {
	public String id;
	public String name;

	public byte[] secret;

	public List<Role> roles;
}
