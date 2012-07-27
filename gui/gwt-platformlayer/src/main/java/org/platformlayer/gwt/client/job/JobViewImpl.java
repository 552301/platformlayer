package org.platformlayer.gwt.client.job;

import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.platformlayer.gwt.client.api.platformlayer.Job;
import org.platformlayer.gwt.client.api.platformlayer.JobLogLine;
import org.platformlayer.gwt.client.stores.JobStore;
import org.platformlayer.gwt.client.view.AbstractApplicationPage;
import org.platformlayer.gwt.client.widgets.Repeater;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiFactory;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;

public class JobViewImpl extends AbstractApplicationPage implements JobView {
	static final Logger log = Logger.getLogger(JobViewImpl.class.getName());

	interface ViewUiBinder extends UiBinder<HTMLPanel, JobViewImpl> {
	}

	private static ViewUiBinder viewUiBinder = GWT.create(ViewUiBinder.class);

	public JobViewImpl() {
		initWidget(viewUiBinder.createAndBindUi(this));
	}

	private JobActivity activity;

	@Inject
	JobStore jobStore;

	@UiField
	SpanElement labelSpan;

	@UiField
	SpanElement timestampsSpan;

	@UiField
	Repeater<JobLogLine> jobLogList;

	@Override
	public void start(JobActivity activity) {
		this.activity = activity;
	}

	@Override
	public void setJobData(Job job) {
		if (job == null) {
		} else {
			{
				SafeHtml html = SafeHtmlUtils.fromString(job.getTargetId() + " " + job.getAction().getName());
				labelSpan.setInnerSafeHtml(html);
			}

			{
				Date startedAt = job.getStartedAt();
				Date endedAt = job.getEndedAt();

				SafeHtmlBuilder html = new SafeHtmlBuilder();
				Date t;
				if (endedAt != null) {
					t = endedAt;
					html.appendEscaped("Ended");
				} else if (startedAt != null) {
					t = startedAt;
					html.appendEscaped("Started");
				} else {
					t = null;
					html.appendEscaped("Queued");
				}

				if (t != null) {
					// TODO: We should get now from the server response
					// so we're not subject to system clock etc
					Date now = new Date();

					html.appendEscaped(" ");

					double age = now.getTime() - t.getTime();
					age /= 1000.0;

					if (age <= 1) {
						html.appendEscaped(" just now");
					} else if (age <= 10) {
						html.appendEscaped(" seconds ago");
					} else if (age < 50) {
						int tens = (int) (age / 10);
						tens *= 10;
						html.appendEscaped(tens + " seconds ago");
					} else if (age < 120) {
						html.appendEscaped(" a minute ago");
					} else if (age < 600) {
						int minutes = (int) (age / 60);
						html.appendEscaped(minutes + " minutes ago");
					} else if (age < 3600) {
						int tenMinutes = (int) (age / 600);
						tenMinutes *= 10;
						html.appendEscaped(tenMinutes + " minutes ago");
					} else {
						int hours = (int) (age / 3600);
						html.appendEscaped(hours + " hours ago");
					}
				}

				timestampsSpan.setInnerSafeHtml(html.toSafeHtml());
			}
		}
	}

	@UiFactory
	Repeater<JobLogLine> makeJobLogList() {
		Repeater<JobLogLine> table = new Repeater<JobLogLine>(new TemplatedJobLogListCell(this));

		// final SingleSelectionModel<OpsProject> selectionModel = new SingleSelectionModel<Product>();
		// table.setSelectionModel(selectionModel);
		// selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
		// @Override
		// public void onSelectionChange(SelectionChangeEvent event) {
		// OpsProject selected = selectionModel.getSelectedObject();
		//
		// if (selectionModel.isSelected(selected)) {
		// Room room = activity.getPlace().getRoom();
		//
		// selectionModel.setSelected(selected, false);
		// activity.addItem(room, selected);
		// }
		// }
		// });

		return table;
	}

	@Override
	public void updateJobLog(List<JobLogLine> lines) {
		jobLogList.appendChildren(lines);
	}

}