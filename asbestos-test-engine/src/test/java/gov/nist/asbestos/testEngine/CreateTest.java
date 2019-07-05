package gov.nist.asbestos.testEngine;

import gov.nist.asbestos.client.client.FhirClient;
import gov.nist.asbestos.client.client.Format;
import gov.nist.asbestos.client.resolver.Ref;
import gov.nist.asbestos.client.resolver.ResourceWrapper;
import gov.nist.asbestos.http.operations.HttpPost;
import gov.nist.asbestos.simapi.validation.Val;
import org.hl7.fhir.r4.model.BaseResource;
import org.hl7.fhir.r4.model.TestReport;
import org.junit.jupiter.api.Test;
import org.mockito.Matchers;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateTest {

    @Test
    void createPatient() throws URISyntaxException {
        FhirClient fhirClientMock = mock(FhirClient.class);
        ResourceWrapper wrapper = new ResourceWrapper();
        HttpPost poster = new HttpPost();
        poster.setStatus(200);
        wrapper.setHttpBase(poster);

        when(fhirClientMock.writeResource(any(BaseResource.class), any(Ref.class), eq(Format.XML), any(Map.class))).thenReturn(wrapper);

        Val val = new Val();
        File test1 = Paths.get(getClass().getResource("/setup/write/createPatient/TestScript.xml").toURI()).getParent().toFile();
        TestEngine testEngine = new TestEngine(test1, new URI(""))
                .setVal(val)
                .setFhirClient(fhirClientMock)
                .run();
        List<String> errors = testEngine.getErrors();
        printErrors(errors);
        assertEquals(0, errors.size());
        TestReport report = testEngine.getTestReport();
        TestReport.TestReportResult result = report.getResult();
        assertEquals(TestReport.TestReportResult.PASS, result);
    }

    private void printErrors(List<String> errors) {
        if (errors.isEmpty())
            return;
        System.out.println("Errors:\n" + errors);
    }
}
