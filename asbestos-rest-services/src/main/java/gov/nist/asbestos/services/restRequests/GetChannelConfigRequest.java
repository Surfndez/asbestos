package gov.nist.asbestos.services.restRequests;

import gov.nist.asbestos.asbestosProxy.channel.ChannelControl;
import gov.nist.asbestos.client.Base.Request;
import gov.nist.asbestos.client.channel.ChannelConfig;
import gov.nist.asbestos.client.channel.ChannelConfigFactory;
import java.util.logging.Logger;

import java.io.IOException;
// 0 - empty
// 1 - app context
// 2 - "rw" or "accessGuard"
// 3 - "channel"
// 4 - channelID

public class GetChannelConfigRequest {
    private static Logger log = Logger.getLogger(GetChannelConfigRequest.class.getName());

    private Request request;

    public static boolean isRequest(Request request) {
        return request.uriParts.size() == 5 && "channel".equalsIgnoreCase(request.uriParts.get(3));
    }

    public GetChannelConfigRequest(Request request) {
        request.setType(this.getClass().getSimpleName());
        this.request = request;
    }

    public void run() throws IOException {
        request.announce("GetChannelConfig");
        String channelId = request.uriParts.get(4);
        ChannelConfig channelConfig;

        try {
            channelConfig = ChannelControl.channelConfigFromChannelId(request.externalCache, channelId);
        } catch (Throwable e) {
            request.notFound();
            return;
        }
        String configString = ChannelConfigFactory.convert(channelConfig);

        request.resp.setContentType("application/json");
        request.resp.getOutputStream().print(configString);
        request.ok();
    }
}
