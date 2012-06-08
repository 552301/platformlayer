package org.platformlayer.ops.cas.openstack;

import java.io.File;
import java.util.List;

import org.openstack.client.OpenstackCredentials;
import org.openstack.client.common.OpenstackSession;
import org.openstack.client.storage.OpenstackStorageClient;
import org.openstack.model.storage.StorageObject;
import org.platformlayer.crypto.Md5Hash;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;
import org.platformlayer.ops.cas.CasObject;
import org.platformlayer.ops.cas.CasStore;
import org.platformlayer.ops.networks.NetworkPoint;
import org.platformlayer.ops.openstack.DirectOpenstackDownload;

import com.google.common.collect.Lists;

public class OpenstackCasStore implements CasStore {
	final String containerName;
	final OpenstackCredentials credentials;

	public OpenstackCasStore(OpenstackCredentials credentials, String containerName) {
		this.credentials = credentials;
		this.containerName = containerName;
	}

	@Override
	public CasObject findArtifact(Md5Hash hash) throws Exception {
		OpenstackStorageClient storageClient = getStorageClient();

		List<StorageObject> storageObjects = Lists.newArrayList(storageClient.listObjects(containerName, null, null));

		String findHash = hash.toHex();
		for (StorageObject storageObject : storageObjects) {
			String storageObjectHash = storageObject.getHash();
			if (storageObjectHash.equalsIgnoreCase(findHash)) {
				return new OpenstackCasObject(storageObject);
			}
		}

		return null;
	}

	private OpenstackStorageClient getStorageClient() {
		OpenstackStorageClient storageClient = getSession().getStorageClient();
		return storageClient;
	}

	OpenstackSession session;

	private OpenstackSession getSession() {
		if (this.session == null) {
			OpenstackSession session = OpenstackSession.create();
			session.authenticate(getCredentials(), true);
			this.session = session;
		}
		return this.session;
	}

	public class OpenstackCasObject implements CasObject {
		final StorageObject storageObject;

		public OpenstackCasObject(StorageObject storageObject) {
			this.storageObject = storageObject;
		}

		@Override
		public void copyTo(OpsTarget target, File remoteFilePath) throws OpsException {
			String objectPath = storageObject.getName();

			DirectOpenstackDownload download = new DirectOpenstackDownload();
			download.download(target, remoteFilePath, getCredentials(), containerName, objectPath);
		}

		@Override
		public NetworkPoint getLocation() {
			throw new UnsupportedOperationException();
		}
	}

	public OpenstackCredentials getCredentials() {
		return credentials;
	}

}
