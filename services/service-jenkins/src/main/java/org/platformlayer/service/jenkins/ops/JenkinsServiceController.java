package org.platformlayer.service.jenkins.ops;

import java.io.File;

import org.platformlayer.images.model.Repository;
import org.platformlayer.images.model.RepositoryKey;
import org.platformlayer.ops.Handler;
import org.platformlayer.ops.OpsContext;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.backups.BackupDirectory;
import org.platformlayer.ops.filesystem.SimpleFile;
import org.platformlayer.ops.firewall.Transport;
import org.platformlayer.ops.instances.InstanceBuilder;
import org.platformlayer.ops.java.JavaVirtualMachine;
import org.platformlayer.ops.networks.PublicEndpoint;
import org.platformlayer.ops.packages.PackageDependency;
import org.platformlayer.ops.tree.OpsTreeBase;
import org.platformlayer.service.jenkins.model.JenkinsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsServiceController extends OpsTreeBase {
	private static final Logger log = LoggerFactory.getLogger(JenkinsServiceController.class);

	public static final int PORT = 8080;

	@Handler
	public void doOperation() {
	}

	@Override
	protected void addChildren() throws OpsException {
		JenkinsService model = OpsContext.get().getInstance(JenkinsService.class);

		InstanceBuilder vm;

		{
			vm = InstanceBuilder.build(model.dnsName, this, model.getTags());
			vm.publicPorts.add(PORT);

			vm.hostPolicy.allowRunInContainer = true;
			vm.minimumMemoryMb = 2048;

			addChild(vm);
		}

		// If we're building Java projects, we'll want a JDK
		vm.addChild(JavaVirtualMachine.buildJdk7());

		{
			PackageDependency jenkinsPackage = PackageDependency.build("jenkins");
			jenkinsPackage.repositoryKey = new RepositoryKey();
			jenkinsPackage.repositoryKey.setUrl("http://pkg.jenkins-ci.org/debian/jenkins-ci.org.key");
			jenkinsPackage.repository = new Repository();
			jenkinsPackage.repository.setKey("jenkins");
			jenkinsPackage.repository.getSource().add("deb http://pkg.jenkins-ci.org/debian binary/");
			vm.addChild(jenkinsPackage);
		}

		// We use curl for backups
		vm.addChild(PackageDependency.build("curl"));

		// Jenkins git usually relies on git being installed
		// git-core is valid on both Debian & Ubuntu
		vm.addChild(PackageDependency.build("git-core"));

		vm.addChild(SimpleFile.build(getClass(), new File("/etc/default/jenkins")));

		vm.addChild(EnsureJenkinsSshKey.class);

		{
			// Adding a known-host entry for github.com doesn't reduce security (?)
			EnsureKnownHost knownHost = vm.addChild(EnsureKnownHost.class);
			knownHost.user = "jenkins";
			knownHost.homeDir = new File("/var/lib/jenkins");
			knownHost.host = "github.com";
			knownHost.algorithm = "ssh-rsa";
			knownHost.key = "AAAAB3NzaC1yc2EAAAABIwAAAQEAq2A7hRGmdnm9tUDbO9IDSwBK6TbQa+PXYPCPy6rbTrTtw7PHkccKrpp0yVhp5HdEIcKr6pLlVDBfOLX9QUsyCOV0wzfjIJNlGEYsdlLJizHhbn2mUjvSAHQqZETYP81eFzLQNnPHt4EVVUh7VfDESU84KezmD5QlWpXLmvU31/yMf+Se8xhHTvKSCZIFImWwoG6mbUoWf9nzpIoaSjB+weqqUUmpaaasXVal72J+UX2B+2RPW3RcT0eOzQgqlJL3RKrTJvdsjE3JEAvGq3lGHSZXy28G3skua2SmVi/w4yCE6gbODqnTWlg7+wC604ydGXA8VJiS5ap43JXiUFFAaQ==";
		}

		// Collectd not in wheezy??
		// instance.addChild(CollectdCollector.build());

		// TODO: If we're going to support SSH git....
		// TODO: We need to ssh-keygen for jenkins
		// TODO: Someone has to add the jenkins ssh key to the git repo
		// TODO: We need to set the git user variables (name & email)
		// TODO: We need to add the ssh key(s) of any git repos we're going to be using over ssh

		// su -c "ssh-keygen -q -f /var/lib/jenkins/.ssh/id_rsa -N ''" jenkins

		// scp root@[2001:470:8157:2::f]:/var/lib/jenkins/.ssh/id_rsa.pub .
		// cat id_rsa.pub | ssh -p29418 <gerritip> gerrit create-account --ssh-key - --full-name Jenkins jenkins

		{
			PublicEndpoint endpoint = injected(PublicEndpoint.class);
			// endpoint.network = null;
			endpoint.publicPort = PORT;
			endpoint.backendPort = PORT;
			endpoint.dnsName = model.dnsName;

			endpoint.tagItem = model.getKey();
			endpoint.parentItem = model.getKey();

			endpoint.transport = Transport.Ipv6;

			vm.addChild(endpoint);
		}

		{
			BackupDirectory backup = injected(BackupDirectory.class);
			backup.itemKey = model.getKey();

			File jenkinsRoot = new File("/var/lib/jenkins");
			backup.backupRoot = jenkinsRoot;

			String[] excludes = { "jobs/*/workspace", "jobs/*/modules", "jobs/*/builds/*/workspace.tar.gz",
					".m2/repository" };

			for (String exclude : excludes) {
				backup.excludes.add(new File(jenkinsRoot, exclude));
			}

			vm.addChild(backup);
		}

	}
}
