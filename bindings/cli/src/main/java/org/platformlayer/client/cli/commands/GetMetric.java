package org.platformlayer.client.cli.commands;

import java.io.IOException;
import java.io.PrintWriter;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.platformlayer.PlatformLayerClient;
import org.platformlayer.PlatformLayerClientException;
import org.platformlayer.client.cli.output.MetricToJsonVisitor;
import org.platformlayer.metrics.model.MetricDataStream;
import org.platformlayer.metrics.model.MetricQuery;

import com.google.common.base.Strings;

public class GetMetric extends PlatformLayerCommandRunnerBase {
	@Argument(index = 0, required = true, usage = "path")
	public String path;

	// @Argument(index = 1, required = true, usage = "metric key")
	// public String metricKey;

	@Option(name = "-filter", usage = "Filter for query")
	public String filter;

	public GetMetric() {
		super("get", "metric");
	}

	@Override
	public Object runCommand() throws PlatformLayerClientException {
		PlatformLayerClient client = getPlatformLayerClient();

		MetricQuery query = new MetricQuery();
		query.item = getContext().pathToItem(path);
		if (!Strings.isNullOrEmpty(filter)) {
			query.filters.add(filter);
		}

		MetricDataStream dataStream = client.getMetric(query);

		return dataStream;
	}

	@Override
	public void formatRaw(Object o, PrintWriter writer) {
		MetricDataStream dataStream = (MetricDataStream) o;

		try {
			MetricToJsonVisitor visitor = new MetricToJsonVisitor(writer);
			dataStream.accept(visitor);
			visitor.close();
		} catch (IOException e) {
			throw new IllegalArgumentException("Error formatting results", e);
		}
	}

}
