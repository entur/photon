package de.komoot.photon.opensearch;

import de.komoot.photon.ESBaseTester;
import de.komoot.photon.PhotonDoc;
import de.komoot.photon.query.SimpleSearchRequest;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ConfigureNormalizationTest extends ESBaseTester {

    private void makeImporterWithSearchables(String... names) {
        var importer = makeImporter();

        long id = 1;
        for (String name : names) {
            importer.add(List.of(
                    new PhotonDoc(String.valueOf(id), "N", id, "place", "hamlet")
                            .names(makeDocNames("name", name))
            ));
            id++;
        }

        importer.finish();
        refresh();
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
    void testDefaultNormalization(@TempDir Path dataDirectory) throws IOException {
        setUpES(dataDirectory);
        makeImporterWithSearchables("RO32", "München");

        SoftAssertions all = new SoftAssertions();

        all.assertThat(hitNames("RO32")).contains("RO32");
        all.assertThat(hitNames("ro32")).contains("RO32");
        all.assertThat(hitNames("munchen")).contains("München");
        all.assertThat(hitNames("muenchen")).contains("München");

        all.assertAll();
    }

    @Test
    void testDisableAllNormalization(@TempDir Path dataDirectory) throws IOException {
        setUpES(dataDirectory, List.of());
        makeImporterWithSearchables("RO32", "München");

        SoftAssertions all = new SoftAssertions();

        all.assertThat(hitNames("RO32")).contains("RO32");
        all.assertThat(hitNames("ro32")).isEmpty();
        all.assertThat(hitNames("München")).contains("München");
        all.assertThat(hitNames("munchen")).isEmpty();
        all.assertThat(hitNames("muenchen")).isEmpty();

        all.assertAll();

    }

    @Test
    void testCustomNormalization(@TempDir Path dataDirectory) throws IOException {
        setUpES(dataDirectory, List.of("apostrophe"));
        makeImporterWithSearchables("John's", "peter");

        SoftAssertions all = new SoftAssertions();

        all.assertThat(hitNames("John")).contains("John's");
        all.assertThat(hitNames("John's")).contains("John's");
        all.assertThat(hitNames("john's")).isEmpty();
        all.assertThat(hitNames("peter's")).contains("peter");
        all.assertThat(hitNames("peter")).contains("peter");

        all.assertAll();
    }

}
