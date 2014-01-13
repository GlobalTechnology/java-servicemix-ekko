package org.ccci.gto.servicemix.ekko.cloudvideo;

import org.ccci.gto.servicemix.common.aws.jaxrs.api.AwsSnsApi.SnsNotificationHandler;
import org.ccci.gto.servicemix.common.aws.model.SnsNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.util.json.JSONObject;

public class AwsEtJobSnsNotificationHandler implements SnsNotificationHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AwsEtJobSnsNotificationHandler.class);

    @Autowired
    private VideoStateMachine videoStateMachine;

    @Override
    public void handle(final SnsNotification notification) {
        if (this.videoStateMachine != null) {
            // parse the Notification Message
            final JSONObject job;
            try {
                job = new JSONObject(notification.getMessage());
            } catch (final Exception e) {
                LOG.debug("Error processing ET Job notification");
                return;
            }

            if (job != null) {
                this.videoStateMachine.checkEncodingJob(job.optString("jobId", null));
            }
        }
    }
}
