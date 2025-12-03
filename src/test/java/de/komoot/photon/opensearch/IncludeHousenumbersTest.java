package de.komoot.photon.opensearch;

import de.komoot.photon.Constants;
import de.komoot.photon.ESBaseTester;
import de.komoot.photon.Importer;
import de.komoot.photon.PhotonDoc;
import de.komoot.photon.query.SimpleSearchRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncludeHousenumbersTest extends ESBaseTester {

    private static final String STREET_NAME = "Test Street";

    @BeforeAll
    void setUp(@TempDir Path dataDirectory) throws Exception {
        setUpES(dataDirectory);
        Importer instance = makeImporter();

        // Add a street
        var street = new PhotonDoc(1, "W", 1, "highway", "residential")
                .names(makeDocNames("name", STREET_NAME))
                .countryCode("DE")
                .importance(0.5)
                .rankAddress(26);

        // Add a house on that street
        var address = new HashMap<String, String>();
        address.put("street", STREET_NAME);
        var house = new PhotonDoc(2, "N", 2, "building", "yes")
                .countryCode("DE")
                .houseNumber("42")
                .addAddresses(address, getProperties().getLanguages())
                .importance(0.1)
                .rankAddress(30);

        instance.add(List.of(street));
        instance.add(List.of(house));
        instance.finish();
        refresh();
    }

    @AfterAll
    @Override
    public void tearDown() {
        super.tearDown();
    }

    @Test
    void searchWithoutIncludeHousenumbersReturnsOnlyStreet() {
        var request = new SimpleSearchRequest();
        request.setQuery(STREET_NAME);

        var handler = getServer().createSearchHandler(1);
        var results = handler.search(request);

        // Should find the street but not the housenumber
        boolean foundStreet = false;
        boolean foundHousenumber = false;
        for (var result : results) {
            if (STREET_NAME.equals(result.getLocalised(Constants.NAME, "en"))) {
                foundStreet = true;
            }
            if (result.get(Constants.HOUSENUMBER) != null) {
                foundHousenumber = true;
            }
        }
        assertTrue(foundStreet, "Should find the street");
        assertFalse(foundHousenumber, "Should not find housenumber without include_housenumbers");
    }

    @Test
    void searchWithIncludeHousenumbersReturnsHousenumbers() {
        var request = new SimpleSearchRequest();
        request.setQuery(STREET_NAME);
        request.setIncludeHousenumbers(true);

        var handler = getServer().createSearchHandler(1);
        var results = handler.search(request);

        // Should find housenumber results
        boolean foundHousenumber = false;
        for (var result : results) {
            if ("42".equals(result.get(Constants.HOUSENUMBER))) {
                foundHousenumber = true;
                break;
            }
        }
        assertTrue(foundHousenumber, "Should find housenumber with include_housenumbers=true");
    }
}
