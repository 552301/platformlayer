package org.platformlayer.ops.images.direct;

import java.io.File;

import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.OpsTarget;

import com.google.inject.ImplementedBy;

@ImplementedBy(SshAgentPeerToPeerCopy.class)
public interface PeerToPeerCopy {

	void copy(OpsTarget srcImageHost, File srcImageFile, OpsTarget target, File targetImageFile) throws OpsException;

}
