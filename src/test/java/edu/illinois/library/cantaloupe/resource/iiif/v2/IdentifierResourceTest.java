package edu.illinois.library.cantaloupe.resource.iiif.v2;

import edu.illinois.library.cantaloupe.config.Configuration;
import edu.illinois.library.cantaloupe.config.Key;
import edu.illinois.library.cantaloupe.resource.ResourceTest;
import edu.illinois.library.cantaloupe.resource.Route;
import edu.illinois.library.cantaloupe.resource.iiif.InformationResourceTester;
import org.junit.Test;

import java.net.URI;

public class IdentifierResourceTest extends ResourceTest {

    private static final String IMAGE = "jpg-rgb-64x56x8-baseline.jpg";

    private InformationResourceTester tester = new InformationResourceTester();

    @Override
    protected String getEndpointPath() {
        return Route.IIIF_2_PATH;
    }

    @Test
    public void testGETRedirectToInfoJSON() {
        URI fromURI = getHTTPURI("/" + IMAGE);
        URI toURI = getHTTPURI("/" + IMAGE + "/info.json");
        tester.testRedirectToInfoJSON(fromURI, toURI);
    }

    @Test
    public void testGETRedirectToInfoJSONWithEncodedCharacters() {
        Configuration config = Configuration.getInstance();
        config.setProperty(Key.SLASH_SUBSTITUTE, ":");

        URI fromURI = getHTTPURI("/subfolder%3A" + IMAGE);
        URI toURI = getHTTPURI("/subfolder%3A" + IMAGE + "/info.json");
        tester.testRedirectToInfoJSONWithEncodedCharacters(fromURI, toURI);
    }

    @Test
    public void testGETRedirectToInfoJSONWithDifferentPublicIdentifier()
            throws Exception {
        URI uri = getHTTPURI("/" + IMAGE);
        tester.testRedirectToInfoJSONWithDifferentPublicIdentifier(uri);
    }

}
