package org.platformlayer.http;

import java.io.IOException;
import java.net.URI;

public interface HttpRequest {

	void setRequestHeader(String key, String value);

	HttpResponse doRequest() throws IOException;

	// OutputStream getOutputStream() throws IOException;

	URI getUrl();

	void setRequestContent(byte[] bytes) throws IOException;

}