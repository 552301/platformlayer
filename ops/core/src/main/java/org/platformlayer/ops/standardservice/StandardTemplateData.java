package org.platformlayer.ops.standardservice;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.platformlayer.InetAddressChooser;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.Link;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.ops.Command;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.crypto.ManagedSecretKey;
import org.platformlayer.ops.crypto.ManagedSecretKeys;
import org.platformlayer.ops.helpers.ProviderHelper;
import org.platformlayer.ops.machines.PlatformLayerHelpers;
import org.platformlayer.ops.metrics.MetricsManager;
import org.platformlayer.ops.networks.NearestAddressChooser;
import org.platformlayer.ops.networks.NetworkPoint;
import org.platformlayer.ops.templates.TemplateDataSource;
import org.platformlayer.ops.uses.LinkHelpers;
import org.platformlayer.ops.uses.LinkTarget;
import org.platformlayer.ops.uses.SimpleLinkConsumer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public abstract class StandardTemplateData implements TemplateDataSource {

	@Inject
	protected ProviderHelper providers;

	@Inject
	protected PlatformLayerHelpers platformLayer;

	@Inject
	protected ManagedSecretKeys managedSecretKeys;

	@Inject
	protected LinkHelpers links;

	@Inject
	protected MetricsManager metricsManager;

	public abstract ItemBase getModel();

	public String getServiceKey() {
		return getKey() + "-" + getInstanceKey();
	}

	public String getUser() {
		return getKey();
	}

	public String getGroup() {
		return getKey();
	}

	public File getInstallDir() {
		return new File("/opt", getKey());
	}

	public File getInstanceDir() {
		return new File(new File("/var", getKey()), getInstanceKey());
	}

	public File getConfigDir() {
		return new File(getInstanceDir(), "config");
	}

	public abstract String getKey();

	public String getInstanceKey() {
		return "default";
	}

	protected abstract Command getCommand() throws OpsException;

	public File getConfigurationFile() {
		return new File(getConfigDir(), "configuration.properties");
	}

	protected Map<String, String> getConfigurationProperties() throws OpsException {
		Map<String, String> properties = Maps.newHashMap();

		List<Link> modelLinks = getLinks();
		if (modelLinks != null && !modelLinks.isEmpty()) {
			NetworkPoint networkPoint = NetworkPoint.forTargetInContext();
			InetAddressChooser inetAddressChooser = NearestAddressChooser.build(networkPoint);
			SimpleLinkConsumer consumer = new SimpleLinkConsumer(getModel().getKey(), inetAddressChooser);

			properties.putAll(links.buildLinkTargetProperties(consumer, modelLinks));
		}

		return properties;
	}

	protected List<Link> getLinks() throws OpsException {
		List<Link> links;
		if (getModel().links == null) {
			links = Lists.newArrayList();
		} else {
			links = getModel().links.getLinks();
		}

		{
			PlatformLayerKey metricsKey = metricsManager.findMetricsServer();
			if (metricsKey != null) {
				Link link = new Link();
				link.name = "metrics";
				link.target = metricsKey;
				links.add(link);
			}
		}

		return links;
	}

	protected PlatformLayerKey getCaPath() throws OpsException {
		return null;
	}

	public ManagedSecretKey findCaSignedKey(PlatformLayerKey caPath, String alias) throws OpsException {
		if (caPath == null) {
			return null;
		}
		return managedSecretKeys.findSslKey(getModel().getKey(), caPath, alias);
	}

	public ManagedSecretKey findCaSignedKey(String alias) throws OpsException {
		return findCaSignedKey(getCaPath(), alias);
	}

	public ManagedSecretKey findCaKey() throws OpsException {
		PlatformLayerKey caPath = getCaPath();
		if (caPath == null) {
			return null;
		}

		ItemBase sslKeyItem = (ItemBase) platformLayer.getItem(caPath);
		ManagedSecretKey key = providers.toInterface(sslKeyItem, ManagedSecretKey.class);

		return key;
	}

	protected abstract PlatformLayerKey getSslKeyPath() throws OpsException;

	public ManagedSecretKey findPublicSslKey() throws OpsException {
		PlatformLayerKey sslKey = getSslKeyPath();
		if (sslKey == null) {
			if (getCaPath() != null) {
				ManagedSecretKey key = findCaSignedKey("public");
				if (key != null) {
					return key;
				}
			}

			return null;
		}
		ItemBase sslKeyItem = (ItemBase) platformLayer.getItem(sslKey);
		ManagedSecretKey key = providers.toInterface(sslKeyItem, ManagedSecretKey.class);
		return key;
	}

	public boolean shouldCreateSslKey() {
		return true;
	}

	public File getKeystoreFile() {
		return new File(getConfigDir(), "keystore.jks");
	}

	public File getDistFile() {
		String extension = ".tar.gz";

		String specifier = getDownloadSpecifier();
		if (specifier != null) {
			if (specifier.endsWith(".zip")) {
				extension = ".zip";
			}
		}

		return new File(getInstallDir(), getKey() + extension);
	}

	public File getWarsPath() {
		return new File(getInstallDir(), "wars");
	}

	public boolean shouldExpand() {
		String distFilename = getDistFile().getName();
		return distFilename.endsWith(".tar.gz") || distFilename.endsWith(".zip");
	}

	public Map<String, String> getEnvironment() throws OpsException {
		return Maps.newHashMap();
	}

	public String getMatchExecutableName() {
		return null;
	}

	public boolean shouldCreateKeystore() {
		return true;
	}

	public File getLogConfigurationFile() {
		return null;
	}

	public abstract String getDownloadSpecifier();

	public void getAdditionalKeys(Map<String, ManagedSecretKey> keys) throws OpsException {
		for (Link link : getLinks()) {
			ItemBase item = platformLayer.getItem(link.getTarget());
			LinkTarget linkTarget = providers.toInterface(item, LinkTarget.class);

			PlatformLayerKey caPath = linkTarget.getCaForClientKey();
			if (caPath != null) {
				String alias = links.buildKeyName(link);

				keys.put(alias, findCaSignedKey(caPath, alias));
			}
		}
	}
}
