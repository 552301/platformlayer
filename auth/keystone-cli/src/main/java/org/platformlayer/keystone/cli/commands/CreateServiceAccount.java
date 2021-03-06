package org.platformlayer.keystone.cli.commands;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.kohsuke.args4j.Option;
import org.platformlayer.auth.ServiceAccount;
import org.platformlayer.auth.UserDatabase;

public class CreateServiceAccount extends KeystoneCommandRunnerBase {
	@Option(name = "-k", aliases = "--key", usage = "keystore", required = true)
	public String keystore;

	@Option(name = "-s", aliases = "--secret", usage = "keystore secret")
	public String keystoreSecret;

	@Option(name = "-a", aliases = "--alias", usage = "key alias")
	public String keyAlias;

	public CreateServiceAccount() {
		super("create", "serviceaccount");
	}

	@Override
	public Object runCommand() throws Exception {
		Certificate[] certificateChain = getContext().getCertificateChain(keystore, keystoreSecret, keyAlias);

		X509Certificate cert;
		if (certificateChain.length == 1) {
			cert = (X509Certificate) certificateChain[0];
		} else {
			System.out.println("Certificate chain has length " + certificateChain.length + ", assuming entry 2 is CA");
			cert = (X509Certificate) certificateChain[1];
		}

		UserDatabase userRepository = getContext().getUserRepository();

		ServiceAccount account = userRepository.createServiceAccount(cert);

		return account;
	}

}
