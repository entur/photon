package de.komoot.photon.config;

import com.beust.jcommander.Parameter;

import java.util.List;

public class PhotonDBLayoutConfig {
    public static final String GROUP = "Database setup options";

    @Parameter(names = "-normalization-filters", category = GROUP, placeholder = "FILTER,...", description = """
            OpenSearch filters for normalizing queries and place names.
            """)
    private List<String> normFilters = List.of("lowercase", "asciifolding", "german_normalization");

    public List<String> getNormalizationFilters() {
        return normFilters;
    }
}
