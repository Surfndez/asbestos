package gov.nist.asbestos.asbestosProxy.requests;

// 0 - empty
// 1 - app context (asbestos)
// 2 - "log"
// 3 - "analysis"
// 4 - "text"
// JSON or XML of Resource is the POST body


import com.google.gson.Gson;
import gov.nist.asbestos.analysis.Report;
import gov.nist.asbestos.client.Base.EventContext;
import gov.nist.asbestos.client.Base.ProxyBase;
import gov.nist.asbestos.client.client.Format;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.hl7.fhir.r4.model.BaseResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

public class AnalyseResourceRequest {
    private static Logger log = Logger.getLogger(AnalyseResourceRequest.class);
    private EventContext eventContext;
    private Request request;

    public static boolean isRequest(Request request) {
        return request.uriParts.size() == 5
                && "log".equalsIgnoreCase(request.uriParts.get(2))
                && "analysis".equalsIgnoreCase(request.uriParts.get(3))
                && "text".equalsIgnoreCase(request.uriParts.get(4));
    }

    public AnalyseResourceRequest(Request request) {
        this.request = request;
    }

    public void run() throws IOException {
        log.info("AnalyseResourceRequest");

        String contentType = request.req.getHeader("Content-Type");
        InputStream input = request.req.getInputStream();
        StringWriter writer = new StringWriter();
        IOUtils.copy(input, writer, "utf-8");
        String string = writer.toString();
        AnalyseResourceClass in = new Gson().fromJson(string, AnalyseResourceClass.class);
        String resourceString = in.string;
        BaseResource baseResource;
        try {
            baseResource = ProxyBase.parse(resourceString, Format.fromContent(resourceString));
        } catch (Exception e) {
            new GetLogEventAnalysisRequest(request)
                    .setEventContext(eventContext)
                    .returnReport(new Report("Cannot parse - " + e.getMessage()));
            return;
        }
        new GetLogEventAnalysisRequest(request).analyseResource(baseResource, eventContext, true);
    }
}