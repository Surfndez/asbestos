package gov.nist.asbestos.services.restRequests;

import gov.nist.asbestos.client.Base.Request;
import gov.nist.asbestos.client.log.SimStore;
import gov.nist.asbestos.client.channel.ChannelConfig;
import gov.nist.asbestos.client.channel.ChannelConfigFactory;
import gov.nist.asbestos.simapi.simCommon.SimId;
import gov.nist.asbestos.simapi.simCommon.TestSession;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
// 0 - empty
// 1 - app context
// 2 - "channel"
// 3 - "create"
// Create a channel based on JSON configuration in request

public class CreateChannelRequest {
    private static Logger log = Logger.getLogger(CreateChannelRequest.class);

    private Request request;

    public static boolean isRequest(Request request) {
        if (request.uriParts.size() == 4) {
            String uriPart2 = request.uriParts.get(2);
            String uriPart3 = request.uriParts.get(3);
            return "create".equals(uriPart3) && ("channel".equals(uriPart2) || "channelGuard".equals(uriPart2));
        }
        return false;
    }

    public CreateChannelRequest(Request request) {
        this.request = request;
    }

    public void run() throws IOException {
        request.announce("CreateChannel");
        String rawRequest = IOUtils.toString(request.req.getInputStream(), Charset.defaultCharset());   // json
        log.debug("CREATE Channel " + rawRequest);
        ChannelConfig channelConfig = ChannelConfigFactory.convert(rawRequest);

        if ("fhir".equalsIgnoreCase(channelConfig.getChannelType()) && channelConfig.isLogMhdCapabilityStatementRequest()) {
            channelConfig.setLogMhdCapabilityStatementRequest(false);
        }

        SimStore simStore = new SimStore(request.externalCache,
                new SimId(new TestSession(channelConfig.getTestSession()),
                        channelConfig.asChannelId(),
                        channelConfig.getActorType(),
                        channelConfig.getEnvironment(),
                        true));

        simStore.create(channelConfig);
        log.info("Channel " + simStore.getChannelId().toString() + " created (type " + simStore.getActorType() + ")" );

        request.resp.setContentType("application/json");
        request.resp.getOutputStream().print(rawRequest);

        request.setStatus((simStore.isNewlyCreated() ? request.resp.SC_CREATED : request.resp.SC_OK));
    }
}