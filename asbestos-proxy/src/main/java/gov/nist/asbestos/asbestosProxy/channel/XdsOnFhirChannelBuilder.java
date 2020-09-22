package gov.nist.asbestos.asbestosProxy.channel;

import gov.nist.asbestos.mhd.channel.XdsOnFhirChannel;
import gov.nist.asbestos.client.channel.BaseChannel;

public class XdsOnFhirChannelBuilder implements IChannelBuilder{
    @Override
    public BaseChannel build() {
        return new XdsOnFhirChannel();
    }
}
