package org.platformlayer.core.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.logging.Level;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlValue;

import org.platformlayer.ids.FederationKey;
import org.platformlayer.ids.ItemType;
import org.platformlayer.ids.ManagedItemId;
import org.platformlayer.ids.ProjectId;
import org.platformlayer.ids.ServiceType;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

//@XmlJavaTypeAdapter(PlatformLayerKeyAdapter.class)
@XmlAccessorType(XmlAccessType.NONE)
public class PlatformLayerKey {
	static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(PlatformLayerKey.class.getName());

	public static final String SCHEME = "platform";

	/* final */FederationKey host;
	/* final */ProjectId project;
	/* final */ServiceType serviceType;
	/* final */ItemType itemType;
	/* final */ManagedItemId id;

	@Deprecated
	// Don't call!
	public PlatformLayerKey() {
		// For JAXB, until this is fixed: http://java.net/jira/browse/JAXB-605
		host = null;
		project = null;
		serviceType = null;
		itemType = null;
		id = null;
	}

	@Deprecated
	// For JAXB
	public PlatformLayerKey(String s) {
		PlatformLayerKey key = parse(s);

		this.host = key.host;
		this.project = key.project;
		this.serviceType = key.serviceType;
		this.itemType = key.itemType;
		this.id = key.id;
	}

	@XmlValue
	protected String getValue() {
		return getUrl();
	}

	protected void setValue(String url) {
		try {
			PlatformLayerKey key = PlatformLayerKey.parse(url);
			this.project = key.project;
			this.serviceType = key.serviceType;
			this.itemType = key.itemType;
			this.id = key.id;
		} catch (Exception e) {
			log.log(Level.WARNING, "Error parsing value: " + url, e);
			throw new IllegalArgumentException("Cannot parse plaform layer key", e);
		}
	}

	public PlatformLayerKey(FederationKey host, ProjectId project, ServiceType serviceType, ItemType itemType,
			ManagedItemId id) {
		this.project = project;
		this.serviceType = serviceType;
		this.itemType = itemType;
		this.id = id;
	}

	public String getUrl() {
		String host = (this.host != null) ? this.host.getKey() : "";
		StringBuilder path = new StringBuilder();
		if (project != null) {
			path.append(project.getKey());
		}
		path.append("/");
		if (serviceType != null) {
			path.append(serviceType.getKey());
		}
		path.append("/");
		if (itemType != null) {
			path.append(itemType.getKey());
		}
		path.append("/");
		if (id != null) {
			path.append(id.getKey());
		}

		// URI uri = new URI(SCHEME, host, path);
		// return uri.toString();
		return SCHEME + "://" + host + "/" + path.toString();
	}

	public ProjectId getProject() {
		return project;
	}

	public ServiceType getServiceType() {
		return serviceType;
	}

	public ItemType getItemType() {
		return itemType;
	}

	public ManagedItemId getItemId() {
		return id;
	}

	public static PlatformLayerKey parse(String s) {
		if (!s.contains("://")) {
			int slashCount = 0;
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				if (c == '/') {
					slashCount++;
				}
			}
			String extraSlashes = "";
			if (slashCount < 4) {
				extraSlashes = Strings.repeat("/", 4 - slashCount);
			}
			s = SCHEME + "://" + extraSlashes + s;
		}

		URI uri;
		try {
			uri = new URI(s);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Error parsing URI", e);
		}

		if (!Objects.equal(SCHEME, uri.getScheme())) {
			throw new IllegalArgumentException();
		}

		FederationKey hostKey = null;
		String host = uri.getHost();
		if (!Strings.isNullOrEmpty(host)) {
			hostKey = FederationKey.build(host);
		}

		String path = uri.getPath();
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		ArrayList<String> components = Lists.newArrayList(Splitter.on('/').split(path));
		if (components.size() < 4) {
			throw new IllegalArgumentException();
		}

		String componentProject = components.get(0);
		ProjectId project = !Strings.isNullOrEmpty(componentProject) ? new ProjectId(componentProject) : null;

		String serviceComponent = components.get(1);
		ServiceType serviceType = !Strings.isNullOrEmpty(serviceComponent) ? new ServiceType(serviceComponent) : null;

		ItemType itemType = new ItemType(components.get(2));
		ManagedItemId itemId = new ManagedItemId(Joiner.on("/").join(components.subList(3, components.size())));

		return new PlatformLayerKey(hostKey, project, serviceType, itemType, itemId);
	}

	public static boolean isKey(String s) {
		return s.startsWith(PlatformLayerKey.SCHEME + "://");
	}

	public FederationKey getHost() {
		return host;
	}

	public PlatformLayerKey withId(ManagedItemId id2) {
		return new PlatformLayerKey(getHost(), getProject(), getServiceType(), getItemType(), id2);
	}

	public PlatformLayerKey withId(String id2) {
		return withId(new ManagedItemId(id2));
	}

	public PlatformLayerKey withServiceType(ServiceType serviceType2) {
		return new PlatformLayerKey(getHost(), getProject(), serviceType2, getItemType(), getItemId());
	}

	public PlatformLayerKey withProject(ProjectId project) {
		return new PlatformLayerKey(getHost(), project, getServiceType(), getItemType(), getItemId());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((host == null) ? 0 : host.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((itemType == null) ? 0 : itemType.hashCode());
		result = prime * result + ((project == null) ? 0 : project.hashCode());
		result = prime * result + ((serviceType == null) ? 0 : serviceType.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		PlatformLayerKey other = (PlatformLayerKey) obj;
		if (host == null) {
			if (other.host != null) {
				return false;
			}
		} else if (!host.equals(other.host)) {
			return false;
		}
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		if (itemType == null) {
			if (other.itemType != null) {
				return false;
			}
		} else if (!itemType.equals(other.itemType)) {
			return false;
		}
		if (project == null) {
			if (other.project != null) {
				return false;
			}
		} else if (!project.equals(other.project)) {
			return false;
		}
		if (serviceType == null) {
			if (other.serviceType != null) {
				return false;
			}
		} else if (!serviceType.equals(other.serviceType)) {
			return false;
		}
		return true;
	}

	public static PlatformLayerKey fromId(String id) {
		return new PlatformLayerKey(null, null, null, null, new ManagedItemId(id));
	}

	public static PlatformLayerKey fromServiceAndItem(String serviceType, String itemType) {
		return new PlatformLayerKey(null, null, new ServiceType(serviceType), ItemType.wrap(itemType), null);
	}

	public static PlatformLayerKey build(String host, String project, String serviceType, String itemType, String itemId) {
		FederationKey federationKey = host != null ? FederationKey.build(host) : null;
		ProjectId projectKey = project != null ? new ProjectId(project) : null;
		ServiceType serviceKey = serviceType != null ? new ServiceType(serviceType) : null;
		ItemType itemKey = itemType != null ? new ItemType(itemType) : null;
		ManagedItemId idKey = itemId != null ? new ManagedItemId(itemId) : null;

		return new PlatformLayerKey(federationKey, projectKey, serviceKey, itemKey, idKey);
	}

	@Override
	public String toString() {
		return getUrl();
	}

	public String getServiceTypeString() {
		if (serviceType == null) {
			return null;
		}
		return serviceType.getKey();
	}

	public String getItemTypeString() {
		if (itemType == null) {
			return null;
		}
		return itemType.getKey();
	}

	public String getItemIdString() {
		if (id == null) {
			return null;
		}
		return id.getKey();
	}

	public String getProjectString() {
		if (project == null) {
			return null;
		}
		return project.getKey();
	}

}
