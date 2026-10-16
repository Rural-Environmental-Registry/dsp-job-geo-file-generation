package br.car.dsp_geo_file.theme;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DownloadThemesDocument(
        String wfsBaseUrl,
        List<DownloadThemeConfig> themes
) {
}
