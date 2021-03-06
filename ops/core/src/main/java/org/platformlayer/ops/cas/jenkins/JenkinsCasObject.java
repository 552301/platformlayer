package org.platformlayer.ops.cas.jenkins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import javax.inject.Inject;

import org.slf4j.*;
import org.platformlayer.cas.CasLocation;
import org.platformlayer.ops.Command;
import org.platformlayer.ops.CommandEnvironment;
import org.platformlayer.ops.FileUpload;
import org.platformlayer.ops.Injection;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;
import org.platformlayer.ops.cas.OpsCasLocation;
import org.platformlayer.ops.cas.OpsCasObjectBase;
import org.platformlayer.ops.helpers.CurlRequest;
import org.platformlayer.ops.machines.InetAddressUtils;
import org.platformlayer.ops.networks.NetworkPoint;
import org.platformlayer.ops.proxy.HttpProxyHelper;
import org.platformlayer.ops.proxy.HttpProxyHelper.Usage;

import com.fathomdb.TimeSpan;
import com.fathomdb.hash.Md5Hash;
import com.fathomdb.io.IoUtils;
import com.google.common.io.Closeables;

public class JenkinsCasObject extends OpsCasObjectBase {
	private static final Logger log = LoggerFactory.getLogger(JenkinsCasObject.class);

	@Inject
	HttpProxyHelper httpProxies;

	private final URI uri;

	public JenkinsCasObject(JenkinsCasStore cas, Md5Hash hash, URI uri) {
		super(cas, hash);

		this.uri = uri;
		Injection.injectMembers(this);
	}

	@Override
	public void copyTo0(OpsTarget target, File remoteFilePath) throws OpsException {
		InetAddress host;
		try {
			host = InetAddress.getByName(uri.getHost());
		} catch (UnknownHostException e) {
			throw new OpsException("Unable to resolve host: " + uri, e);
		}

		if (InetAddressUtils.isPublic(host)) {
			CurlRequest curlRequest = new CurlRequest(uri);
			curlRequest.bareRequest = true;
			CommandEnvironment commandEnvironment = httpProxies.getHttpProxyEnvironment(target, Usage.General, uri);

			Command curlCommand = curlRequest.toCommand();
			curlCommand.addLiteral(">");
			curlCommand.addFile(remoteFilePath);
			curlCommand.setEnvironment(commandEnvironment);
			curlCommand.setTimeout(TimeSpan.FIVE_MINUTES);

			target.executeCommand(curlCommand);
		} else {
			log.warn("Address was not public: " + host + ", doing copy via ops system");

			File tempFile;
			try {
				tempFile = File.createTempFile("jenkins", "dat");
			} catch (IOException e) {
				throw new OpsException("Error creating temp file", e);
			}
			try {
				InputStream is = uri.toURL().openStream();
				try {
					IoUtils.copyStream(is, tempFile);
				} finally {
					Closeables.closeQuietly(is);
				}

				FileUpload.upload(target, remoteFilePath, tempFile);
			} catch (IOException e) {
				throw new OpsException("Error copying jenkins artifact", e);
			} finally {
				tempFile.delete();
			}
		}

	}

	@Override
	public CasLocation getLocation() throws OpsException {
		return new OpsCasLocation(NetworkPoint.forPublicHostname(uri.getHost()));
	}

	@Override
	public String toString() {
		return "JenkinsCasObject [uri=" + uri + "]";
	}

}
