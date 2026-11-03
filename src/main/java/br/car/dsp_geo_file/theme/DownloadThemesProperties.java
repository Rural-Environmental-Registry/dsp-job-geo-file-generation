package br.car.dsp_geo_file.theme;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dsp.download")
public class DownloadThemesProperties {

    /**
     * Same file the backend reads — synced into {@code config/downloads/} by dsp-core {@code ./config.sh}.
     * Docker/Compose sets {@code DSP_DOWNLOAD_THEMES_FILE=file:/config/downloadThemesConfig.json}.
     */
    private String themesFile = "file:config/downloads/downloadThemesConfig.json";
}
