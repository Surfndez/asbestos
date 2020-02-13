package gov.nist.abestos.client.resolver;

import gov.nist.asbestos.client.resolver.Ref;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefTest {

    @Test
    void rebase() throws URISyntaxException {
        String location = "http://localhost:8080/fhir/fhir/Patient/15142/_history/1";
        String base = "http://localhost:8080/fhir/fhir";
        Ref lref = new Ref(location);
        assertEquals(base, lref.getBase().toString());

        Ref aref = new Ref(new Ref(base), "Patient", "15142", "1");
        assertEquals(aref, lref);

        String newbase = "/proxy/prox/default_fhirpass/Channel";
        aref = lref.rebase(new URI(newbase));
       aref = aref.httpizeTo(new URI(location));
        assertEquals("http://localhost:8080/proxy/prox/default_fhirpass/Channel/Patient/15142/_history/1", aref.toString());
    }

    @Test
    void rebaseWithQueryParams() {
        String location = "http://localhost:8080/fhir/fhir/Patient?birthdate=1950-02-23&family=Alder&given=Alex";
        String newBase = "http://localhost:8081/asbestos/proxy/default__default";

        Ref ref = new Ref(location);
        Ref newLocationRef = ref.rebase(newBase);
        String newLocation = newLocationRef.toString();
        assertEquals("http://localhost:8081/asbestos/proxy/default__default/Patient?birthdate=1950-02-23&family=Alder&given=Alex", newLocation);
    }
}
