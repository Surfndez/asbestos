package gov.nist.asbestos.proxyWar;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import gov.nist.asbestos.analysis.RelatedReport;
import gov.nist.asbestos.analysis.Report;
import gov.nist.asbestos.client.Base.ProxyBase;
import gov.nist.asbestos.client.client.Format;
import gov.nist.asbestos.client.events.UIEvent;
import gov.nist.asbestos.client.events.UITask;
import gov.nist.asbestos.client.resolver.Ref;
import gov.nist.asbestos.http.operations.HttpGet;
import gov.nist.asbestos.http.operations.HttpPost;
import gov.nist.asbestos.testEngine.engine.TestEngine;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class pdbMinimalIT {

    // No_Patient must be loaded (it's in the Test_Patients collection) for translation on limited
    @BeforeAll
    static void beforeAll() throws URISyntaxException {
        Utility.loadCaches();
    }

    // This test is run through the API because cache management is handled on the server so
    // the test engine must run there.
    @Test
    void sendPDB() throws URISyntaxException {
        String channelId = "limited";
        String collectionId = "Internal";
        String testId = "sendMinimalPDB";
        Map<String, Object> testReport = Utility.runTest(channelId, collectionId, testId, null);

        String eventId = Utility.getEventId(testReport);
        Report report = Utility.getAnalysis(channelId, eventId, "request");
        assertEquals("DocumentManifest", report.getBase().getName());
        assertTrue(report.getBase().isMinimal());
        assertFalse(report.getBase().isComprehensive());
        assertTrue(report.getBase().getMinimalChecked().size() > 3);
        assertFalse(report.getBase().getValidationResult().hasIssue());
        assertEquals(1, report.getObjects().size());

        RelatedReport drReport = report.getObjects().get(0);
        assertEquals("DocumentReference", drReport.getName());
        assertTrue(drReport.isMinimal());
        assertTrue(drReport.getMinimalChecked().size() > 4);
        assertFalse(drReport.isComprehensive());
        assertTrue(drReport.getComprehensiveErrors().size() > 5);
        assertFalse(drReport.getValidationResult().hasIssue());

        UIEvent event = Utility.getEvent(channelId, eventId);
        UITask task = event.getClientTask();
        String returnBundleString = task.getResponseBody();
        Bundle bundle = (Bundle) ProxyBase.parse(returnBundleString, Format.JSON);
        Bundle.BundleEntryComponent entry = bundle.getEntry().get(2);
        Bundle.BundleEntryResponseComponent response = entry.getResponse();
        String binaryUrl = response.getLocation();

        HttpGet getter = new HttpGet();
        getter.getJson(binaryUrl);
        assertEquals(200, getter.getStatus());


        String foo = "foo";
    }
}
