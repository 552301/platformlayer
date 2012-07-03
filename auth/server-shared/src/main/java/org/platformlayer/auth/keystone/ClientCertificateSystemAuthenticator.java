package org.platformlayer.auth.keystone;

import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.openstack.keystone.services.AuthenticatorException;
import org.openstack.keystone.services.ServiceAccount;
import org.openstack.keystone.services.SystemAuthenticator;
import org.openstack.utils.Hex;
import org.platformlayer.RepositoryException;
import org.platformlayer.auth.UserDatabase;

public class ClientCertificateSystemAuthenticator implements SystemAuthenticator {
	private static final Logger log = Logger.getLogger(ClientCertificateSystemAuthenticator.class);

	@Inject
	UserDatabase repository;

	@Override
	public ServiceAccount authenticate(X509Certificate[] certChain) throws AuthenticatorException {
		if (certChain.length == 0) {
			return null;
		}

		X509Certificate head = certChain[0];

		Principal subject = head.getSubjectDN();
		PublicKey publicKey = head.getPublicKey();

		ServiceAccount auth;
		try {
			auth = repository.findServiceAccount(subject.getName(), publicKey.getEncoded());
		} catch (RepositoryException e) {
			throw new AuthenticatorException("Error while authenticating user", e);
		}

		if (auth != null) {
			return auth;
		}

		String publicKeyHex = Hex.toHex(publicKey.getEncoded());

		log.debug("Authentication failed - public key not recognized: " + publicKeyHex);

		return null;
	}
}