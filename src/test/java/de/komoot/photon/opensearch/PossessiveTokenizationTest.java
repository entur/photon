package de.komoot.photon.opensearch;

import de.komoot.photon.ESBaseTester;
import de.komoot.photon.PhotonDoc;
import de.komoot.photon.nominatim.model.AddressType;
import de.komoot.photon.query.SimpleSearchRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the desired matching behavior for apostrophe-bearing names AND short-letter
 * abbreviations. We want all of the following to work simultaneously, with no regressions
 * against each other:
 *  - "Oslo S" returns the actual Oslo S stop place at the top, NOT random possessive POIs
 *  - Possessive-named POIs (Tiffany's, Lio's) are findable by typing either form
 *  - Compound apostrophe names (O'Connor) are findable by both the full form and the suffix
 *  - Apostrophe-prefix names in other languages (L'Étoile, D'Artagnan) are findable
 *
 * Both ASCII (') and typographic (’) apostrophes are exercised; OSM data uses the
 * typographic form by default.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PossessiveTokenizationTest extends ESBaseTester {

    @BeforeAll
    void setUp(@TempDir Path dataDirectory) throws IOException {
        getProperties().setStemEnglishPossessives(true);
        setUpES(dataDirectory);

        Map<String, String> osloCity = new HashMap<>();
        osloCity.put("city", "Oslo");

        var importer = makeImporter();
        importer.add(List.of(
                cafe("1", 1, "Lio's Cafe Bar", osloCity),
                cafe("2", 2, "Tiffany’s", osloCity),
                cafe("3", 3, "O'Connor", osloCity),
                cafe("4", 4, "L'Étoile", osloCity),
                cafe("5", 5, "D'Artagnan", osloCity),
                // Substring case: a cafe whose name literally contains "Oslo S".
                // Locks in that the actual stop place still ranks above this one.
                cafe("6", 6, "Espresso House Oslo S Hovedhallen", osloCity),
                stopPlace("100", 100, "Oslo S", osloCity)
        ));
        importer.finish();
        refresh();
    }

    @AfterAll
    @Override
    public void tearDown() {
        super.tearDown();
    }

    private PhotonDoc cafe(String id, long osmId, String name, Map<String, String> address) {
        return new PhotonDoc(id, "N", osmId, "amenity", "cafe")
                .names(makeDocNames("name", name))
                .countryCode("NO")
                .importance(0.05)
                .addAddresses(address, getProperties().getLanguages());
    }

    /** Approximates a real stop place: addressType HOUSE matches the rank-30 mapping
     *  used by the converter for stopPlace, and importance ~0.27 is what a typical
     *  rail station gets after the log-normalised popularity scaling. */
    private PhotonDoc stopPlace(String id, long osmId, String name, Map<String, String> address) {
        return new PhotonDoc(id, "N", osmId, "railway", "station")
                .names(makeDocNames("name", name))
                .countryCode("NO")
                .importance(0.27)
                .addressType(AddressType.HOUSE)
                .addAddresses(address, getProperties().getLanguages());
    }

    private List<String> hitNames(String query) {
        var request = new SimpleSearchRequest();
        request.setQuery(query);
        return getServer().createSearchHandler(20).search(request).toList()
                .stream()
                .map(r -> r.getLocalised("name", "en"))
                .toList();
    }

    @Test
    void osloSReturnsTheStopPlaceFirst() {
        var hits = hitNames("Oslo S");

        assertThat(hits)
                .as("query \"Oslo S\" must return at least the actual stop place")
                .isNotEmpty();
        assertThat(hits.get(0))
                .as("the Oslo S stop place must be the top result, ranked above any substring matches")
                .isEqualTo("Oslo S");
        assertThat(hits)
                .as("query \"Oslo S\" must not surface possessive POIs whose only S-context is the apostrophe-s")
                .doesNotContain("Tiffany’s", "Lio's Cafe Bar", "O'Connor", "L'Étoile", "D'Artagnan");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            // Possessive name in plain form
            "'Tiffany',  'Tiffany’s'",
            "'Lio',      'Lio''s Cafe Bar'",

            // Possessive name with the user's apostrophe form (ASCII and typographic)
            "'Tiffany''s',  'Tiffany’s'",
            "'Tiffany’s',   'Tiffany’s'",
            "'Lio''s',      'Lio''s Cafe Bar'",

            // Compound apostrophe name (O'Connor): full form, suffix, and space-separated
            "'O''Connor',  'O''Connor'",
            "'Connor',     'O''Connor'",
            "'O Connor',   'O''Connor'",

            // Apostrophe-prefix names in other languages
            "'L''Étoile',  'L''Étoile'",
            "'Étoile',     'L''Étoile'",
            "'D''Artagnan','D''Artagnan'",
            "'Artagnan',   'D''Artagnan'"
    })
    void queryReturnsExpectedHit(String query, String expectedName) {
        assertThat(hitNames(query))
                .as("query %s should surface %s", query, expectedName)
                .contains(expectedName);
    }

    @ParameterizedTest(name = "{0} must NOT surface {1}")
    @CsvSource({
            // The original bug class: no cross-pollination between unrelated possessive POIs
            "'Tiffany',  'Lio''s Cafe Bar'",
            "'Tiffany',  'O''Connor'",
            "'Connor',   'Tiffany’s'",
            "'Connor',   'Lio''s Cafe Bar'",
            // A standalone city name must not pull in every cafe in the city
            "'Oslo',     'Tiffany’s'"
    })
    void queryDoesNotReturnUnrelatedHit(String query, String forbiddenName) {
        assertThat(hitNames(query))
                .as("query %s must not surface %s", query, forbiddenName)
                .doesNotContain(forbiddenName);
    }
}
